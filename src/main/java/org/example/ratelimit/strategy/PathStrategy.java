package org.example.ratelimit.strategy;

import org.example.controller.RateLimitRuleRequest;
import org.example.ratelimit.RateLimitRule;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

public class PathStrategy implements RateLimitStrategy {
	@Override
	public void validate(RateLimitRuleRequest request) {
		String path = request.getPath();
		if (path == null || path.isBlank()) {
			throw new IllegalArgumentException("path is required for type PATH");
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
		String path = rule.getPath();
		if (path != null && path.endsWith("/*")) {
			String key = path;
			return pathPrefixIndex.put(key, rule);
		} else {
			String key = path == null ? "" : path;
			return pathExactIndex.put(key, rule);
		}
	}
	
	@Override
	public void removeFromIndex(RateLimitRule rule,
								ConcurrentMap<String, RateLimitRule> ipIndex,
								ConcurrentMap<String, RateLimitRule> pathExactIndex,
								ConcurrentMap<String, RateLimitRule> pathPrefixIndex,
								ConcurrentMap<String, RateLimitRule> ipPathIndex) {
		String path = rule.getPath();
		if (path != null && path.endsWith("/*")) {
			pathPrefixIndex.remove(path, rule);
		} else {
			String key = path == null ? "" : path;
			pathExactIndex.remove(key, rule);
		}
	}
	
	@Override
	public boolean matches(RateLimitRule rule, String ip, String path) {
		return pathMatches(rule.getPath(), path);
	}
	
	@Override
	public String computeKey(RateLimitRule rule, String ip, String path) {
		return path == null ? "unknown" : path;
	}
	
	private static boolean pathMatches(String pattern, String path) {
		if (pattern == null) {
			return false;
		}
		if ("*".equals(pattern)) {
			return true;
		}
		if (pattern.endsWith("/*")) {
			String prefix = pattern.substring(0, pattern.length() - 1); // keep trailing /
			return path != null && path.startsWith(prefix);
		}
		return Objects.equals(pattern, path);
	}
}
