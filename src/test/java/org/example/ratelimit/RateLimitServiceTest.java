package org.example.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RateLimitServiceTest {

    private RateLimitService service;

    @BeforeEach
    public void setUp() {
        service = new RateLimitService();
        // remove any default rules so tests are deterministic
        for (RateLimitRule r : service.getRules()) {
            service.removeRuleById(r.getId());
        }
        assertTrue(service.getRules().isEmpty(), "rules should be empty after cleanup");
    }

    // helper to create and add a rule
    private RateLimitRule addRule(RateLimitType type, String ip, String path, int rpm) {
        RateLimitRule rule = new RateLimitRule(type, ip, path, rpm);
        service.addRule(rule);
        return rule;
    }

    // assert that 'allowed' attempts succeed and the next one is denied
    private void assertAllowsNTimesThenDenied(String ip, String path, int allowed) {
        for (int i = 0; i < allowed; i++) {
            assertTrue(service.tryConsume(ip, path), "expected allowed token #" + (i + 1) + " for " + path);
        }
        assertFalse(service.tryConsume(ip, path), "expected denied after " + allowed + " tokens for " + path);
    }

    // assert that a single success attempt is allowed (keeps readability)
    private void assertAllowed(String ip, String path) {
        assertTrue(service.tryConsume(ip, path));
    }

    @Test
    public void globalRule_enforcesLimits() {
        // add a GLOBAL rule with 2 rpm (2 tokens available)
        RateLimitRule global = addRule(RateLimitType.GLOBAL, "*", null, 2);
        assertEquals(1, service.getRules().size());
        assertEquals(global.getId(), service.getRules().getFirst().getId());

        // consume twice -> allowed; third -> denied
        assertAllowsNTimesThenDenied("1.2.3.4", "/any", 2);
    }

    @Test
    public void globalRule_replace_resetsBucketAndUsesNewLimit() {
        addRule(RateLimitType.GLOBAL, "*", null, 2);
        // consume available tokens from global1
        assertAllowsNTimesThenDenied("1.2.3.4", "/x", 2);

        // add another GLOBAL rule -> should replace the previous one and provide fresh tokens
        RateLimitRule global2 = addRule(RateLimitType.GLOBAL, "*", null, 5);
        assertEquals(1, service.getRules().size());
        assertEquals(global2.getId(), service.getRules().getFirst().getId());

        // g2 should have 5 tokens available now
        assertAllowsNTimesThenDenied("1.2.3.4", "/x", 5);
    }

    @Test
    public void globalRule_remove_allowsRequestsWhenNoRules() {
        RateLimitRule rule = addRule(RateLimitType.GLOBAL, "*", null, 2);
        // remove global rule
        service.removeRuleById(rule.getId());
        assertTrue(service.getRules().isEmpty(), "rules should be empty after removing global");

        // with no rules, tryConsume should allow requests
        assertAllowed("1.2.3.4", "/x");
    }

    @Test
    public void ipRule_enforcesLimitsForSpecificIp() {
        addRule(RateLimitType.IP, "10.0.0.1", null, 2);

        // same IP should be limited
        assertAllowsNTimesThenDenied("10.0.0.1", "/any", 2);

        // different IP should not be affected
        assertAllowed("10.0.0.2", "/any");
    }

    @Test
    public void pathRule_prefixWildcard_appliesToMatchingPaths() {
        // PATH rule with prefix wildcard
        addRule(RateLimitType.PATH, null, "/sites/*", 2);

        // requests to matching path consume tokens
        assertAllowsNTimesThenDenied("1.1.1.1", "/sites/MLA", 2);

        // non-matching path should not be limited by this rule
        assertAllowed("1.1.1.1", "/other");
    }

    @Test
    public void ipPathRule_enforcesOnlyWhenIpAndPathMatch() {
        // IP_PATH rule: ip=5.5.5.5 and path prefix /sites/* limit
        addRule(RateLimitType.IP_PATH, "5.5.5.5", "/sites/*", 2);

        // matching ip and path consumes
        assertAllowsNTimesThenDenied("5.5.5.5", "/sites/MLA", 2);

        // same path but different ip should not be affected
        assertAllowed("6.6.6.6", "/sites/MLA");

        // same ip but different path should not be affected
        assertAllowed("5.5.5.5", "/other");
    }

    @Test
    public void pathRule_exactMatch_appliesOnlyToExactPath() {
        // PATH rule without wildcard must match exact path only
        addRule(RateLimitType.PATH, null, "/sites/MLA", 2);

        // exact path consumes
        assertAllowsNTimesThenDenied("2.2.2.2", "/sites/MLA", 2);

        // similar but different path should not be limited by this rule
        assertAllowed("2.2.2.2", "/sites/MLA/sub");
        assertAllowed("3.3.3.3", "/sites/MLA/sub");
    }

    @Test
    public void ipPathRule_exactPath_requiresExactPathAndIp() {
        // IP_PATH exact path (no wildcard)
        addRule(RateLimitType.IP_PATH, "7.7.7.7", "/items/123", 2);

        // matching ip and exact path consumes
        assertAllowsNTimesThenDenied("7.7.7.7", "/items/123", 2);

        // same ip but different path should not be affected
        assertAllowed("7.7.7.7", "/items/123/extra");

        // different ip same path should not be affected
        assertAllowed("8.8.8.8", "/items/123");
    }

    @Test
    public void ipPath_wildcardWins_andExactPathUnaffected() {
        // IP_PATH wildcard (2 rpm) and exact (3 rpm) both registered under wildcard IP "*"
        addRule(RateLimitType.IP_PATH, "1.2.3.4", "/sites", 3);
        addRule(RateLimitType.IP_PATH, "1.2.3.4", "/sites/*", 2);

        String ip = "1.2.3.4";

        // Requests to /sites/MLA should be governed by the wildcard (2 tokens)
        assertAllowsNTimesThenDenied(ip, "/sites/MLA", 2);

        // Requests to exact /sites should still have 3 tokens available (not consumed by the wildcard requests)
        assertAllowsNTimesThenDenied(ip, "/sites", 3);
    }

    @Test
    public void path_wildcardWins_andExactPathUnaffected() {
        // PATH wildcard (2 rpm) and exact (3 rpm)
        addRule(RateLimitType.PATH, null, "/sites/*", 2);
        addRule(RateLimitType.PATH, null, "/sites", 3);

        String ip = "1.2.3.4";

        // Requests to /sites/MLA should be governed by the wildcard (2 tokens)
        assertAllowsNTimesThenDenied(ip, "/sites/MLA", 2);

        // Requests to exact /sites should still have 3 tokens available (not consumed by the wildcard requests)
        assertAllowsNTimesThenDenied(ip, "/sites", 3);
    }

    @Test
    public void path_exactWins_andWildcardUnaffected_whenExactIsMoreRestrictive() {
        // exact /sites (2 rpm) and wildcard /sites/* (3 rpm)
        addRule(RateLimitType.PATH, null, "/sites/*", 3);
        addRule(RateLimitType.PATH, null, "/sites", 2);

        String ip = "9.9.9.9";

        // Requests to exact /sites should be governed by the exact rule (2 tokens)
        assertAllowsNTimesThenDenied(ip, "/sites", 2);

        // The wildcard rule should remain untouched for its own matching paths
        // Requests to /sites/MLA should be governed by the wildcard (3 tokens)
        assertAllowsNTimesThenDenied(ip, "/sites/MLA", 3);
    }

    @Test
    public void ipPath_exactWins_andWildcardUnaffected_whenExactIsMoreRestrictive() {
        // IP_PATH exact /sites (2 rpm) and wildcard /sites/* (3 rpm)
        addRule(RateLimitType.IP_PATH, "9.9.9.9", "/sites/*", 3);
        addRule(RateLimitType.IP_PATH, "9.9.9.9", "/sites", 2);

        String ip = "9.9.9.9";

        assertAllowsNTimesThenDenied(ip, "/sites", 2);
        assertAllowsNTimesThenDenied(ip, "/sites/MLA", 3);
    }

    // The following tests verify that removing a rule via removeRuleById removes it from indexes and buckets
    @Test
    public void removeIpRule_removesIndexAndBucket() {
        RateLimitRule rule = addRule(RateLimitType.IP, "10.0.0.1", null, 1);

        // first consume should be allowed, second should be denied by the IP rule
        assertTrue(service.tryConsume("10.0.0.1", "/x"));
        assertFalse(service.tryConsume("10.0.0.1", "/x"));

        // remove and now requests should be allowed again (index/bucket removed)
        assertTrue(service.removeRuleById(rule.getId()));
        assertTrue(service.tryConsume("10.0.0.1", "/x"));
    }

    @Test
    public void removePathRule_removesIndexAndBucket() {
        RateLimitRule rule = addRule(RateLimitType.PATH, null, "/foo/*", 1);

        assertTrue(service.tryConsume("1.2.3.4", "/foo/1"));
        assertFalse(service.tryConsume("1.2.3.4", "/foo/1"));

        assertTrue(service.removeRuleById(rule.getId()));
        assertTrue(service.tryConsume("1.2.3.4", "/foo/1"));
    }

    @Test
    public void removeIpPathRule_removesIndexAndBucket() {
        RateLimitRule rule = addRule(RateLimitType.IP_PATH, "1.2.3.4", "/items/*", 1);

        assertTrue(service.tryConsume("1.2.3.4", "/items/123"));
        assertFalse(service.tryConsume("1.2.3.4", "/items/123"));

        assertTrue(service.removeRuleById(rule.getId()));
        assertTrue(service.tryConsume("1.2.3.4", "/items/123"));
    }

    @Test
    public void removeGlobalRule_removesIndexAndBucket() {
        RateLimitRule rule = addRule(RateLimitType.GLOBAL, "*", null, 1);

        assertTrue(service.tryConsume("9.9.9.9", "/any"));
        assertFalse(service.tryConsume("9.9.9.9", "/any"));

        assertTrue(service.removeRuleById(rule.getId()));
        assertTrue(service.tryConsume("9.9.9.9", "/any"));
    }
}
