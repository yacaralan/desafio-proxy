package org.example.ratelimit;

import org.example.controller.RateLimitRuleRequest;
import org.example.ratelimit.strategy.IpStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

public class IpStrategyTest {

    private IpStrategy strategy;

    @BeforeEach
    public void setUp() {
        strategy = new IpStrategy();
    }

    @Test
    public void validate_throwsWhenIpMissing() {
        RateLimitRuleRequest req = new RateLimitRuleRequest();
        req.setType("IP");
        req.setIp(null);
        assertThrows(IllegalArgumentException.class, () -> strategy.validate(req));
    }

    @Test
    public void validate_acceptsWhenIpPresent() {
        RateLimitRuleRequest req = new RateLimitRuleRequest();
        req.setType("IP");
        req.setIp("1.2.3.4");
        assertDoesNotThrow(() -> strategy.validate(req));
    }

    @Test
    public void matches_wildcardAndExact() {
        RateLimitRule wildcard = new RateLimitRule(RateLimitType.IP, "*", null, 10);
        assertTrue(strategy.matches(wildcard, "1.2.3.4", "/x"));

        RateLimitRule exact = new RateLimitRule(RateLimitType.IP, "1.2.3.4", null, 10);
        assertTrue(strategy.matches(exact, "1.2.3.4", "/x"));

        RateLimitRule other = new RateLimitRule(RateLimitType.IP, "9.9.9.9", null, 10);
        assertFalse(strategy.matches(other, "1.2.3.4", "/x"));
    }

    @Test
    public void computeKey_returnsIpOrUnknown() {
        RateLimitRule r = new RateLimitRule(RateLimitType.IP, "*", null, 10);
        assertEquals("1.2.3.4", strategy.computeKey(r, "1.2.3.4", "/x"));
        assertEquals("unknown", strategy.computeKey(r, null, "/x"));
    }

    @Test
    public void index_and_remove_updateIpIndex() {
        ConcurrentMap<String, RateLimitRule> ipIndex = new ConcurrentHashMap<>();
        ConcurrentMap<String, RateLimitRule> pathExact = new ConcurrentHashMap<>();
        ConcurrentMap<String, ConcurrentMap<String, RateLimitRule>> ipPath = new ConcurrentHashMap<>();
        ConcurrentMap<String, RateLimitRule> pathPrefix = new ConcurrentHashMap<>();

        RateLimitRule r = new RateLimitRule(RateLimitType.IP, "5.6.7.8", null, 100);
        RateLimitRule previous = strategy.index(r, ipIndex, pathExact, pathPrefix, ipPath);
        assertNull(previous);
        assertTrue(ipIndex.containsKey("5.6.7.8"));
        assertEquals(r, ipIndex.get("5.6.7.8"));

        strategy.removeFromIndex(r, ipIndex, pathExact, pathPrefix, ipPath);
        assertFalse(ipIndex.containsKey("5.6.7.8"));
    }

    @Test
    public void duplicateRules_sameIp_keepsLast() {
        RateLimitService service = new RateLimitService();
        // cleanup
        for (RateLimitRule r : service.getRules()) service.removeRuleById(r.getId());
        assertTrue(service.getRules().isEmpty());

        RateLimitRule first = new RateLimitRule(RateLimitType.IP, "1.2.3.4", null, 5);
        service.addRule(first);
        RateLimitRule second = new RateLimitRule(RateLimitType.IP, "1.2.3.4", null, 10);
        service.addRule(second);

        assertNull(service.findRuleById(first.getId()), "first rule should have been removed");
        assertNotNull(service.findRuleById(second.getId()), "second rule should remain");
    }
}
