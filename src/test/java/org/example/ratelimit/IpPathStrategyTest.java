package org.example.ratelimit;

import org.example.controller.RateLimitRuleRequest;
import org.example.ratelimit.strategy.IpPathStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

public class IpPathStrategyTest {
	
	private IpPathStrategy strategy;
	
	@BeforeEach
	public void setUp() {
		strategy = new IpPathStrategy();
	}
	
	@Test
	public void validate_throwsWhenMissingIpOrPath() {
		RateLimitRuleRequest request = new RateLimitRuleRequest();
		request.setType("IP_PATH");
		request.setIp(null);
		request.setPath(null);
		assertThrows(IllegalArgumentException.class, () -> strategy.validate(request));
		
		request.setIp("1.2.3.4");
		request.setPath("");
		assertThrows(IllegalArgumentException.class, () -> strategy.validate(request));
	}
	
	@Test
	public void validate_acceptsWhenBothPresent() {
		RateLimitRuleRequest request = new RateLimitRuleRequest();
		request.setType("IP_PATH");
		request.setIp("1.2.3.4");
		request.setPath("/items/*");
		assertDoesNotThrow(() -> strategy.validate(request));
	}
	
	@Test
	public void matches_requiresBothIpAndPath() {
		RateLimitRule r = new RateLimitRule(RateLimitType.IP_PATH, "1.2.3.4", "/items/*", 10);
		assertTrue(strategy.matches(r, "1.2.3.4", "/items/123"));
		assertFalse(strategy.matches(r, "9.9.9.9", "/items/123"));
		assertFalse(strategy.matches(r, "1.2.3.4", "/other/123"));
	}
	
	@Test
	public void computeKey_combinesIpAndPath() {
		RateLimitRule r = new RateLimitRule(RateLimitType.IP_PATH, "1.2.3.4", "/items/*", 10);
		assertEquals("1.2.3.4|/items/123", strategy.computeKey(r, "1.2.3.4", "/items/123"));
		assertEquals("unknown|unknown", strategy.computeKey(r, null, null));
	}
	
	@Test
	public void index_and_remove_updateIpPathIndex() {
		ConcurrentMap<String, RateLimitRule> ipIndex = new ConcurrentHashMap<>();
		ConcurrentMap<String, RateLimitRule> pathExact = new ConcurrentHashMap<>();
		ConcurrentMap<String, RateLimitRule> pathPrefix = new ConcurrentHashMap<>();
		ConcurrentMap<String, RateLimitRule> ipPath = new ConcurrentHashMap<>();
		
		RateLimitRule r = new RateLimitRule(RateLimitType.IP_PATH, "8.8.8.8", "/items/*", 50);
		RateLimitRule previous = strategy.index(r, ipIndex, pathExact, pathPrefix, ipPath);
		assertNull(previous);
		String composite = "8.8.8.8|/items/*";
		assertTrue(ipPath.containsKey(composite));
		assertEquals(r, ipPath.get(composite));

		strategy.removeFromIndex(r, ipIndex, pathExact, pathPrefix, ipPath);
		assertFalse(ipPath.containsKey(composite));
	}
	
	@Test
	public void duplicateRules_sameIpPath_keepsLast() {
        RateLimitService service = new RateLimitService();
        for (RateLimitRule r : service.getRules()) service.removeRuleById(r.getId());
        assertTrue(service.getRules().isEmpty());

        RateLimitRule first = new RateLimitRule(RateLimitType.IP_PATH, "8.8.8.8", "/items/*", 5);
        service.addRule(first);
        RateLimitRule second = new RateLimitRule(RateLimitType.IP_PATH, "8.8.8.8", "/items/*", 10);
        service.addRule(second);

        assertNull(service.findRuleById(first.getId()), "first IP_PATH rule should have been removed");
        assertNotNull(service.findRuleById(second.getId()), "second IP_PATH rule should remain");
    }
}
