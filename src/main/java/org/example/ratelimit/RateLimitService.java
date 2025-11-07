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
    private final ConcurrentMap<String, RateLimitRule> ipIndex = new ConcurrentHashMap<>(); // key: ip
    private final ConcurrentMap<String, RateLimitRule> pathExactIndex = new ConcurrentHashMap<>(); // key: exact path
    private final ConcurrentMap<String, RateLimitRule> pathPrefixIndex = new ConcurrentHashMap<>(); // key: prefix pattern ending with /*
    // ipPathIndex maps compositeKey "ip|pathPattern" -> RateLimitRule
    private final ConcurrentMap<String, RateLimitRule> ipPathIndex = new ConcurrentHashMap<>(); // key: "ip|path"
    // Only a single GLOBAL rule is allowed at a time. If a new GLOBAL rule is added it replaces the previous one.
    private volatile RateLimitRule globalRule = null;

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitService() {
        // default example rules (these can be changed or extended via code or admin endpoints)
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
                removeBucketForRule(existing.getId());
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
                removeBucketForRule(previous.getId());
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
        // remove bucket for this rule id
        removeBucketForRule(foundRule.getId());
        if (globalRule != null && globalRule.getId().equals(foundRule.getId())) {
            globalRule = null;
        }
        LOGGER.info("Removed rate-limit rule id={} rule={}", id, foundRule);
        return true;
    }


    private Bucket createBucketFor(RateLimitRule rule) {
        Bandwidth limit = Bandwidth.classic(rule.getRpm(), Refill.greedy(rule.getRpm(), Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    public boolean tryConsume(String ip, String path) {
        LOGGER.debug("tryConsume called for ip={} path={}", ip, path);

        List<RateLimitRule> candidates = collectCandidates(ip, path);
        List<RateLimitRule> uniqueCandidates = removeDuplicateCandidates(candidates);
        boolean allowed = evaluateCandidates(uniqueCandidates, ip, path);
        if (allowed) {
            LOGGER.debug("Request allowed for ip={} path={}", ip, path);
        }
        return allowed;
    }

    private List<RateLimitRule> collectCandidates(String ip, String path) {
        List<RateLimitRule> candidates = new ArrayList<>();
        addIpCandidate(ip, candidates);
        addPathCandidates(ip, path, candidates);
        addIpPathCandidates(ip, path, candidates);
        addGlobalCandidate(candidates);
        return candidates;
    }

    private void addIpCandidate(String ip, List<RateLimitRule> candidates) {
        if (ip == null) return;
        RateLimitRule ipRule = ipIndex.get(ip);
        if (ipRule != null) candidates.add(ipRule);
    }

    private void addPathCandidates(String ip, String path, List<RateLimitRule> candidates) {
        if (path == null) return;
        RateLimitRule pathRule = pathExactIndex.get(path);
        if (pathRule != null) candidates.add(pathRule);
        // prefix matches - iterate all prefix rules
        for (RateLimitRule rule : pathPrefixIndex.values()) {
            if (rule.matches(ip, path)) candidates.add(rule);
        }
    }

    private void addIpPathCandidates(String ip, String path, List<RateLimitRule> candidates) {
        if (ip == null || path == null) return;
        // exact composite lookup
        String compositeExact = ip + "|" + path;
        RateLimitRule exact = ipPathIndex.get(compositeExact);
        if (exact != null) candidates.add(exact);
        // also consider pattern rules registered for this ip (keys like "ip|/sites/*"); iterate entries but only those for this ip
        String prefix = ip + "|";
        for (ConcurrentMap.Entry<String, RateLimitRule> e : ipPathIndex.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith(prefix)) continue;
            RateLimitRule rule = e.getValue();
            if (rule == exact) continue; // already added
            if (rule.matches(ip, path)) candidates.add(rule);
        }
    }

    private void addGlobalCandidate(List<RateLimitRule> candidates) {
        RateLimitRule globalRuleRef = globalRule;
        if (globalRuleRef != null) candidates.add(globalRuleRef);
    }

    private List<RateLimitRule> removeDuplicateCandidates(List<RateLimitRule> candidates) {
        return new ArrayList<>(new HashSet<>(candidates));
    }

    private boolean evaluateCandidates(List<RateLimitRule> candidates, String ip, String path) {
        for (RateLimitRule rule : candidates) {
            Bucket bucket = buckets.computeIfAbsent(rule.getId(), id -> createBucketFor(rule));
            boolean ok = bucket.tryConsume(1);
            LOGGER.debug("Rule={} key={} tryConsume result={}", rule, rule.getId(), ok);
            if (!ok) {
                LOGGER.info("Request denied by rate-limit rule={} for key={} ip={} path={}", rule, rule.getId(), ip, path);
                return false;
            }
        }
        return true;
    }
	
    private void removeBucketForRule(String ruleId) {
        if (ruleId == null) return;
        buckets.remove(ruleId);
    }
}
