package org.example.ratelimit.strategy;

import org.example.controller.RateLimitRuleRequest;
import org.example.ratelimit.RateLimitRule;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class IpPathStrategy implements RateLimitStrategy {
    @Override
    public void validate(RateLimitRuleRequest request) {
        String ip = request.getIp();
        String path = request.getPath();
        if (ip == null || ip.isBlank() || path == null || path.isBlank())
            throw new IllegalArgumentException("both ip and path are required for type IP_PATH");
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public RateLimitRule index(RateLimitRule rule, ConcurrentMap<String, RateLimitRule> ipIndex, ConcurrentMap<String, RateLimitRule> pathExactIndex, ConcurrentMap<String, RateLimitRule> pathPrefixIndex, ConcurrentMap<String, ConcurrentMap<String, RateLimitRule>> ipPathIndex) {
        String ip = rule.getIp() == null ? "" : rule.getIp();
        String pathKey = rule.getPath() == null ? "" : rule.getPath();
        ConcurrentMap<String, RateLimitRule> inner = ipPathIndex.computeIfAbsent(ip, k -> new ConcurrentHashMap<>());
        return inner.put(pathKey, rule);
    }

    @Override
    public void removeFromIndex(RateLimitRule rule, ConcurrentMap<String, RateLimitRule> ipIndex, ConcurrentMap<String, RateLimitRule> pathExactIndex, ConcurrentMap<String, RateLimitRule> pathPrefixIndex, ConcurrentMap<String, ConcurrentMap<String, RateLimitRule>> ipPathIndex) {
        String ipPart = rule.getIp() == null ? "" : rule.getIp();
        String pathKey = rule.getPath() == null ? "" : rule.getPath();
        ConcurrentMap<String, RateLimitRule> inner = ipPathIndex.get(ipPart);
        if (inner != null) {
            inner.remove(pathKey, rule);
            if (inner.isEmpty()) {
                ipPathIndex.remove(ipPart, inner);
            }
        }
    }

    @Override
    public boolean matches(RateLimitRule rule, String ip, String path) {
        String ruleIp = rule.getIp();
        String rulePath = rule.getPath();
        boolean ipMatch = (ruleIp != null) && ("*".equals(ruleIp) || ruleIp.equals(ip));
        boolean pathMatch = pathMatches(rulePath, path);
        return ipMatch && pathMatch;
    }

    @Override
    public String computeKey(RateLimitRule rule, String ip, String path) {
        String ipPart = ip == null ? "unknown" : ip;
        String pathPart = path == null ? "unknown" : path;
        return ipPart + "|" + pathPart;
    }

    private static boolean pathMatches(String pattern, String path) {
        if (pattern == null) return false;
        if ("*".equals(pattern)) return true;
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return path != null && path.startsWith(prefix);
        }
        return Objects.equals(pattern, path);
    }
}
