package org.example.ratelimit.strategy;

import org.example.controller.RateLimitRuleRequest;
import org.example.ratelimit.RateLimitRule;

import java.util.concurrent.ConcurrentMap;

public class IpStrategy implements RateLimitStrategy {
	@Override
	public void validate(RateLimitRuleRequest req) {
		String ip = req.getIp();
		if (ip == null || ip.isBlank()) {
			throw new IllegalArgumentException("ip is required for type IP");
		}
	}
	
	@Override
	public boolean isGlobal() {
		return false;
	}
	
	@Override
	public RateLimitRule index(RateLimitRule rule,
							   ConcurrentMap<String, RateLimitRule> ipIndex,
							   ConcurrentMap<String, RateLimitRule> pathExactIndex,
							   ConcurrentMap<String, RateLimitRule> pathPrefixIndex,
							   ConcurrentMap<String, RateLimitRule> ipPathIndex) {
		String key = rule.getIp() == null ? "" : rule.getIp();
		return ipIndex.put(key, rule);
	}
	
	@Override
	public void removeFromIndex(RateLimitRule rule,
								ConcurrentMap<String, RateLimitRule> ipIndex,
								ConcurrentMap<String, RateLimitRule> pathExactIndex,
								ConcurrentMap<String, RateLimitRule> pathPrefixIndex,
								ConcurrentMap<String, RateLimitRule> ipPathIndex) {
		String key = rule.getIp() == null ? "" : rule.getIp();
		ipIndex.remove(key, rule);
	}
	
	@Override
	public boolean matches(RateLimitRule rule, String ip, String path) {
		String ruleIp = rule.getIp();
		if (ruleIp == null) {
			return false;
		}
		return ruleIp.equals(ip);
	}
	
	@Override
	public String computeKey(RateLimitRule rule, String ip, String path) {
		return ip == null ? "unknown" : ip;
	}
}
