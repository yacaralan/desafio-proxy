package org.example.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RateLimitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitService.class);

    public enum Type {IP, PATH, IP_PATH, GLOBAL}

    public static final class Rule {
        public final Type type;
        public final String pattern; // for IP_PATH you can use * wildcards for path
        public final long requestsPerMinute;

        public Rule(Type type, String pattern, long requestsPerMinute) {
            this.type = Objects.requireNonNull(type);
            this.pattern = pattern;
            this.requestsPerMinute = requestsPerMinute;
        }

        @Override
        public String toString() {
            return "Rule{" + "type=" + type + ", pattern='" + pattern + '\'' + ", rpm=" + requestsPerMinute + '}';
        }
    }

    private final List<Rule> rules = new ArrayList<>();
    // For each rule, map key (ip, path, ip|path or global) to a bucket
    private final ConcurrentMap<Rule, ConcurrentMap<String, Bucket>> buckets = new ConcurrentHashMap<>();

    public RateLimitService() {
        // default example rules (these can be changed or extended via code or admin endpoints)
        rules.add(new Rule(Type.IP, "*", 1000));
        rules.add(new Rule(Type.PATH, "/categories/*", 10000));
        rules.add(new Rule(Type.IP_PATH, "152.152.152.152|/items/*", 10));
        LOGGER.info("RateLimitService initialized with rules={}", rules);
    }

    public List<Rule> getRules() {
        return List.copyOf(rules);
    }

    public void addRule(Rule r) {
        rules.add(r);
        LOGGER.info("Added rate-limit rule={}", r);
    }

    private String computeKey(Rule rule, String ip, String path) {
        return switch (rule.type) {
            case IP -> ip == null ? "unknown" : ip;
            case PATH -> path == null ? "unknown" : path;
            case IP_PATH -> (ip == null ? "unknown" : ip) + "|" + (path == null ? "unknown" : path);
            case GLOBAL -> "global";
        };
    }

    private boolean matchesPattern(Rule rule, String ip, String path) {
        return switch (rule.type) {
            case IP -> rule.pattern.equals("*") || rule.pattern.equals(ip);
            case PATH -> pathMatches(rule.pattern, path);
            case IP_PATH -> {
                String[] parts = rule.pattern.split("\\|", 2);
                String pIp = parts.length > 0 ? parts[0] : "";
                String pPath = parts.length > 1 ? parts[1] : "";
                boolean ipMatch = pIp.equals("*") || pIp.equals(ip);
                boolean pathMatch = pathMatches(pPath, path);
                yield ipMatch && pathMatch;
            }
            case GLOBAL -> true;
        };
    }

    private boolean pathMatches(String pattern, String path) {
        if (pattern == null) return false;
        if (pattern.equals("*")) return true;
        // very simple wildcard: supports suffix star (/categories/*)
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 1); // keep trailing /
            return path != null && path.startsWith(prefix);
        }
        return Objects.equals(pattern, path);
    }

    private Bucket createBucketFor(Rule rule) {
        Bandwidth limit = Bandwidth.classic(rule.requestsPerMinute, Refill.greedy(rule.requestsPerMinute, Duration.ofMinutes(1)));
        return Bucket4j.builder().addLimit(limit).build();
    }

    public boolean tryConsume(String ip, String path) {
        LOGGER.debug("tryConsume called for ip={} path={}", ip, path);
        // Evaluate all rules; if any matching rule denies, deny request.
        List<Bucket> toConsume = new ArrayList<>();
        for (Rule r : rules) {
            if (!matchesPattern(r, ip, path)) continue;
            String key = computeKey(r, ip, path);
            ConcurrentMap<String, Bucket> map = buckets.computeIfAbsent(r, rr -> new ConcurrentHashMap<>());
            Bucket bucket = map.computeIfAbsent(key, k -> createBucketFor(r));
            // quick availability check
            long available = bucket.getAvailableTokens();
            LOGGER.debug("Rule={} key={} availableTokens={}", r, key, available);
            if (available <= 0) {
                LOGGER.info("Request denied by rate-limit rule={} for key={} ip={} path={}", r, key, ip, path);
                return false;
            }
            toConsume.add(bucket);
        }
        // consume from all matching buckets
        for (Bucket b : toConsume) {
            boolean ok = b.tryConsume(1);
            if (!ok) {
                // Best-effort; no rollback here. If this happens, deny.
                LOGGER.info("Request failed to consume token from bucket, denying");
                return false;
            }
        }
        LOGGER.debug("Request allowed for ip={} path={}", ip, path);
        return true;
    }
}
