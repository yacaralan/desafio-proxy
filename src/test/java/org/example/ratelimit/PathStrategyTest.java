package org.example.ratelimit;

import org.example.controller.RateLimitRuleRequest;
import org.example.ratelimit.strategy.PathStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

public class PathStrategyTest {
	
	private PathStrategy strategy;
	
	@BeforeEach
	public void setUp() {
		strategy = new PathStrategy();
	}
	
	@Test
	public void validate_throwsWhenPathMissing() {
		RateLimitRuleRequest requestuest = new RateLimitRuleRequest();
		requestuest.setType("PATH");
		requestuest.setPath(null);
		assertThrows(IllegalArgumentException.class, () -> strategy.validate(requestuest));
	}
	
	@Test
	public void validate_acceptsWhenPathPresent() {
		RateLimitRuleRequest request = new RateLimitRuleRequest();
		request.setType("PATH");
		request.setPath("/categories/*");
		assertDoesNotThrow(() -> strategy.validate(request));
	}
	
	@Test
	public void matches_exactAndPrefix() {
		RateLimitRule exact = new RateLimitRule(RateLimitType.PATH, null, "/foo", 10);
		assertTrue(strategy.matches(exact, "1.2.3.4", "/foo"));
		assertFalse(strategy.matches(exact, "1.2.3.4", "/foo/bar"));
		
		RateLimitRule prefix = new RateLimitRule(RateLimitType.PATH, null, "/foo/*", 10);
		assertTrue(strategy.matches(prefix, "1.2.3.4", "/foo/bar"));
		assertFalse(strategy.matches(prefix, "1.2.3.4", "/fbar"));
	}
	
	@Test
	public void computeKey_returnsPathOrUnknown() {
		RateLimitRule r = new RateLimitRule(RateLimitType.PATH, null, "/foo/*", 10);
		assertEquals("/foo/bar", strategy.computeKey(r, "1.2.3.4", "/foo/bar"));
		assertEquals("unknown", strategy.computeKey(r, "1.2.3.4", null));
	}
	
	@Test
	public void index_and_remove_updatePathIndexes() {
		ConcurrentMap<String, RateLimitRule> ipIndex = new ConcurrentHashMap<>();
		ConcurrentMap<String, RateLimitRule> pathExact = new ConcurrentHashMap<>();
		ConcurrentMap<String, ConcurrentMap<String, RateLimitRule>> ipPath = new ConcurrentHashMap<>();
		ConcurrentMap<String, RateLimitRule> pathPrefix = new ConcurrentHashMap<>();
		
		RateLimitRule exact = new RateLimitRule(RateLimitType.PATH, null, "/bar", 100);
		RateLimitRule previous = strategy.index(exact, ipIndex, pathExact, pathPrefix, ipPath);
		assertNull(previous);
		assertTrue(pathExact.containsKey("/bar"));
		assertEquals(exact, pathExact.get("/bar"));
		
		strategy.removeFromIndex(exact, ipIndex, pathExact, pathPrefix, ipPath);
		assertFalse(pathExact.containsKey("/bar"));
		
		RateLimitRule pref = new RateLimitRule(RateLimitType.PATH, null, "/baz/*", 100);
		strategy.index(pref, ipIndex, pathExact, pathPrefix, ipPath);
		assertTrue(pathPrefix.containsKey("/baz/*"));
		assertEquals(pref, pathPrefix.get("/baz/*"));
		strategy.removeFromIndex(pref, ipIndex, pathExact, pathPrefix, ipPath);
		assertFalse(pathPrefix.containsKey("/baz/*"));
	}
	
	@Test
	public void duplicateRules_samePath_keepsLast() {
        RateLimitService service = new RateLimitService();
        for (RateLimitRule r : service.getRules()) service.removeRuleById(r.getId());
        assertTrue(service.getRules().isEmpty());

        RateLimitRule first = new RateLimitRule(RateLimitType.PATH, null, "/foo/*", 5);
        service.addRule(first);
        RateLimitRule second = new RateLimitRule(RateLimitType.PATH, null, "/foo/*", 10);
        service.addRule(second);

        assertNull(service.findRuleById(first.getId()), "first PATH rule should have been removed");
        assertNotNull(service.findRuleById(second.getId()), "second PATH rule should remain");
    }
}
