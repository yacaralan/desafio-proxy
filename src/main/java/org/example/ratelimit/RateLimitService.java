package org.example.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RateLimitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitService.class);

    // Keep a master map by id to preserve order / retrieval
    private final ConcurrentMap<String, RateLimitRule> rulesById = new ConcurrentHashMap<>();

    // Indexes for fast lookup - single rule per key
    private final ConcurrentMap<String, RateLimitRule> ipIndex = new ConcurrentHashMap<>(); // key: ip or "*"
    private final ConcurrentMap<String, RateLimitRule> pathExactIndex = new ConcurrentHashMap<>(); // key: exact path
    private final ConcurrentMap<String, RateLimitRule> pathPrefixIndex = new ConcurrentHashMap<>(); // key: prefix pattern ending with /*
    private final ConcurrentMap<String, ConcurrentMap<String, RateLimitRule>> ipPathIndex = new ConcurrentHashMap<>(); // key: ip or "*" -> (pathPattern -> rule)
    // Only a single GLOBAL rule is allowed at a time. If a new GLOBAL rule is added it replaces the previous one.
    private volatile RateLimitRule globalRule = null;

    // For each rule, map key (ip, path, ip|path or global) to a bucket
    private final ConcurrentMap<String, ConcurrentMap<String, Bucket>> buckets = new ConcurrentHashMap<>();

    public RateLimitService() {
        // default example rules (these can be changed or extended via code or admin endpoints)
        addRule(new RateLimitRule(RateLimitType.IP, "*", null, 1000));
        addRule(new RateLimitRule(RateLimitType.PATH, null, "/categories/*", 10000));
        addRule(new RateLimitRule(RateLimitType.IP_PATH, "152.152.152.152", "/items/*", 10));
        LOGGER.info("RateLimitService initialized with rules={}", getRules());
    }

    public List<RateLimitRule> getRules() {
        // preserve insertion-like order via values stream
        return new ArrayList<>(rulesById.values());
    }

    public void addRule(RateLimitRule rule) {
        Objects.requireNonNull(rule, "rule");

        if (rule.getType().isGlobal()) {
            RateLimitRule existing = this.globalRule;
            if (existing != null) {
                // remove existing global rule from indexes and master map and buckets
                rulesById.remove(existing.getId());
                existing.getType().removeFromIndex(existing, ipIndex, pathExactIndex, pathPrefixIndex, ipPathIndex);
                buckets.remove(existing.getId());
                LOGGER.info("Replaced existing GLOBAL rule id={} with new GLOBAL rule id={}", existing.getId(), rule.getId());
            }
            this.globalRule = rule;
            rulesById.put(rule.getId(), rule);
            // index the new global (no previous rule to handle)
            rule.getType().index(rule, ipIndex, pathExactIndex, pathPrefixIndex, ipPathIndex);
        } else {
            // index returns previous rule for same key if any
            RateLimitRule previous = rule.getType().index(rule, ipIndex, pathExactIndex, pathPrefixIndex, ipPathIndex);
            if (previous != null) {
                // remove previous rule from master map and buckets
                rulesById.remove(previous.getId());
                buckets.remove(previous.getId());
                LOGGER.info("Replaced existing rule id={} with new rule id={}", previous.getId(), rule.getId());
            }
            rulesById.put(rule.getId(), rule);
        }
        LOGGER.info("Added rate-limit rule={}", rule);
    }

    public RateLimitRule findRuleById(String id) {
        return rulesById.get(id);
    }

    public boolean removeRuleById(String id) {
        RateLimitRule foundRule = rulesById.remove(id);
        if (foundRule == null) return false;
        foundRule.getType().removeFromIndex(foundRule, ipIndex, pathExactIndex, pathPrefixIndex, ipPathIndex);
        // remove buckets for this rule id
        buckets.remove(foundRule.getId());
        if (globalRule != null && globalRule.getId().equals(foundRule.getId())) {
            globalRule = null;
        }
        LOGGER.info("Removed rate-limit rule id={} rule={}", id, foundRule);
        return true;
    }


    private Bucket createBucketFor(RateLimitRule rule) {
        Bandwidth limit = Bandwidth.classic(rule.getRequestsPerMinute(), Refill.greedy(rule.getRequestsPerMinute(), Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    public boolean tryConsume(String ip, String path) {
        LOGGER.debug("tryConsume called for ip={} path={}", ip, path);
        // Collect candidate matching rules from indexes (avoid iterating all rules)
        List<RateLimitRule> candidates = new ArrayList<>();

        // IP rules: exact ip and wildcard
        if (ip != null) {
            RateLimitRule ipRule = ipIndex.get(ip);
            if (ipRule != null) candidates.add(ipRule);
        }
        RateLimitRule wildcardIpRule = ipIndex.get("*");
        if (wildcardIpRule != null) candidates.add(wildcardIpRule);

        // PATH exact
        if (path != null) {
            RateLimitRule pathExact = pathExactIndex.get(path);
            if (pathExact != null) candidates.add(pathExact);
            // prefix matches - iterate all prefix rules
            for (RateLimitRule rule : pathPrefixIndex.values()) {
                if (rule.matches(ip, path)) candidates.add(rule);
            }
        }

        // IP_PATH
        if (ip != null) {
            ConcurrentMap<String, RateLimitRule> ipMap = ipPathIndex.get(ip);
            if (ipMap != null) {
                for (RateLimitRule rule : ipMap.values()) if (rule.matches(ip, path)) candidates.add(rule);
            }
        }
        ConcurrentMap<String, RateLimitRule> ipMapWild = ipPathIndex.get("*");
        if (ipMapWild != null) {
            for (RateLimitRule rule : ipMapWild.values()) if (rule.matches(ip, path)) candidates.add(rule);
        }

        // GLOBAL: only at most one rule exists
        RateLimitRule globalRuleRef = globalRule;
        if (globalRuleRef != null) candidates.add(globalRuleRef);

        // Remove duplicates (same rule may have been added multiple times via indexes)
        List<RateLimitRule> uniqueCandidates = new ArrayList<>(new HashSet<>(candidates));

        // Evaluate all candidate rules; if any matching rule denies, deny request.
        for (RateLimitRule candidateRule : uniqueCandidates) {
            if (!candidateRule.matches(ip, path)) continue;
            String key = candidateRule.computeKey(ip, path);
            ConcurrentMap<String, Bucket> bucketMap = buckets.computeIfAbsent(candidateRule.getId(), ruleId -> new ConcurrentHashMap<>());
            Bucket bucket = bucketMap.computeIfAbsent(key, bucketKey -> createBucketFor(candidateRule));
            boolean ok = bucket.tryConsume(1);
            LOGGER.debug("Rule={} key={} tryConsume result={}", candidateRule, key, ok);
            if (!ok) {
                LOGGER.info("Request denied by rate-limit rule={} for key={} ip={} path={}", candidateRule, key, ip, path);
                return false;
            }
        }
        LOGGER.debug("Request allowed for ip={} path={}", ip, path);
        return true;
    }
}
