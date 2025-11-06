package org.example.ratelimit.strategy;

import org.example.controller.RateLimitRuleRequest;
import org.example.ratelimit.RateLimitRule;

import java.util.concurrent.ConcurrentMap;

public interface RateLimitStrategy {
	void validate(RateLimitRuleRequest req);
	
	boolean isGlobal();
	
	/**
	 * Index the provided rule into the provided indexes. Returns the previous rule stored for the same key (if any).
	 */
	RateLimitRule index(RateLimitRule rule,
				   ConcurrentMap<String, RateLimitRule> ipIndex,
				   ConcurrentMap<String, RateLimitRule> pathExactIndex,
				   ConcurrentMap<String, RateLimitRule> pathPrefixIndex,
				   ConcurrentMap<String, ConcurrentMap<String, RateLimitRule>> ipPathIndex);
	
	void removeFromIndex(RateLimitRule rule,
					 ConcurrentMap<String, RateLimitRule> ipIndex,
					 ConcurrentMap<String, RateLimitRule> pathExactIndex,
					 ConcurrentMap<String, RateLimitRule> pathPrefixIndex,
					 ConcurrentMap<String, ConcurrentMap<String, RateLimitRule>> ipPathIndex);
	
	boolean matches(RateLimitRule rule, String ip, String path);
	
	String computeKey(RateLimitRule rule, String ip, String path);
}
