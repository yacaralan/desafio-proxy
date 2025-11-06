package org.example.ratelimit;

import org.example.controller.RateLimitRuleRequest;
import org.example.ratelimit.strategy.GlobalStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

public class GlobalStrategyTest {

    private GlobalStrategy strategy;

    @BeforeEach
    public void setUp() {
        strategy = new GlobalStrategy();
    }

    @Test
    public void validate_acceptsAnything() {
        RateLimitRuleRequest req = new RateLimitRuleRequest();
        req.setType("GLOBAL");
        assertDoesNotThrow(() -> strategy.validate(req));
    }

    @Test
    public void matches_alwaysTrue() {
        RateLimitRule r = new RateLimitRule(RateLimitType.GLOBAL, "*", null, 1);
        assertTrue(strategy.matches(r, "1.2.3.4", "/any"));
        assertTrue(strategy.matches(r, null, null));
    }

    @Test
    public void computeKey_isGlobal() {
        RateLimitRule r = new RateLimitRule(RateLimitType.GLOBAL, "*", null, 1);
        assertEquals("global", strategy.computeKey(r, "1.2.3.4", "/any"));
    }

    @Test
    public void index_and_remove_noOp() {
        ConcurrentMap<String, RateLimitRule> ipIndex = new ConcurrentHashMap<>();
        ConcurrentMap<String, RateLimitRule> pathExact = new ConcurrentHashMap<>();
        ConcurrentMap<String, ConcurrentMap<String, RateLimitRule>> ipPath = new ConcurrentHashMap<>();
        ConcurrentMap<String, RateLimitRule> pathPrefix = new ConcurrentHashMap<>();

        RateLimitRule r = new RateLimitRule(RateLimitType.GLOBAL, "*", null, 1);
        RateLimitRule prev = strategy.index(r, ipIndex, pathExact, pathPrefix, ipPath);
        assertNull(prev);
        assertTrue(ipIndex.isEmpty());
        assertTrue(pathExact.isEmpty());
        assertTrue(ipPath.isEmpty());
        assertTrue(pathPrefix.isEmpty());

        strategy.removeFromIndex(r, ipIndex, pathExact, pathPrefix, ipPath);
        assertTrue(ipIndex.isEmpty());
        assertTrue(pathExact.isEmpty());
        assertTrue(ipPath.isEmpty());
        assertTrue(pathPrefix.isEmpty());
    }

    @Test
    public void duplicateRules_global_keepsLast() {
        RateLimitService service = new RateLimitService();
        for (RateLimitRule r : service.getRules()) service.removeRuleById(r.getId());
        assertTrue(service.getRules().isEmpty());

        RateLimitRule first = new RateLimitRule(RateLimitType.GLOBAL, "*", null, 1);
        service.addRule(first);
        RateLimitRule second = new RateLimitRule(RateLimitType.GLOBAL, "*", null, 2);
        service.addRule(second);

        assertNull(service.findRuleById(first.getId()), "first GLOBAL rule should have been removed");
        assertNotNull(service.findRuleById(second.getId()), "second GLOBAL rule should remain");
    }
}
