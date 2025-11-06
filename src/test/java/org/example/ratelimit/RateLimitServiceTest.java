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

    @Test
    public void globalRule_enforcesLimits() {
        // add a GLOBAL rule with 2 rpm (2 tokens available)
        RateLimitRule global = new RateLimitRule(RateLimitType.GLOBAL, "*", null, 2);
        service.addRule(global);
        assertEquals(1, service.getRules().size());
        assertEquals(global.getId(), service.getRules().get(0).getId());

        // consume twice -> allowed; third -> denied
        assertTrue(service.tryConsume("1.2.3.4", "/any"));
        assertTrue(service.tryConsume("1.2.3.4", "/any"));
        assertFalse(service.tryConsume("1.2.3.4", "/any"));
    }

    @Test
    public void globalRule_replace_resetsBucketAndUsesNewLimit() {
        RateLimitRule global1 = new RateLimitRule(RateLimitType.GLOBAL, "*", null, 2);
        service.addRule(global1);
        // consume available tokens from global1
        assertTrue(service.tryConsume("1.2.3.4", "/x"));
        assertTrue(service.tryConsume("1.2.3.4", "/x"));
        assertFalse(service.tryConsume("1.2.3.4", "/x"));

        // add another GLOBAL rule -> should replace the previous one and provide fresh tokens
        RateLimitRule global2 = new RateLimitRule(RateLimitType.GLOBAL, "*", null, 5);
        service.addRule(global2);
        assertEquals(1, service.getRules().size());
        assertEquals(global2.getId(), service.getRules().get(0).getId());

        // g2 should have 5 tokens available now
        for (int i = 0; i < 5; i++) {
            assertTrue(service.tryConsume("1.2.3.4", "/x"), "expected token " + (i + 1) + " to be consumed");
        }
        assertFalse(service.tryConsume("1.2.3.4", "/x"));
    }

    @Test
    public void globalRule_remove_allowsRequestsWhenNoRules() {
        RateLimitRule rule = new RateLimitRule(RateLimitType.GLOBAL, "*", null, 2);
        service.addRule(rule);
        // remove global rule
        service.removeRuleById(rule.getId());
        assertTrue(service.getRules().isEmpty(), "rules should be empty after removing global");

        // with no rules, tryConsume should allow requests
        assertTrue(service.tryConsume("1.2.3.4", "/x"));
    }
}
