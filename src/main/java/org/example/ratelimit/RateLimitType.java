package org.example.ratelimit;

import org.example.controller.RateLimitRuleRequest;
import org.example.ratelimit.strategy.GlobalStrategy;
import org.example.ratelimit.strategy.IpPathStrategy;
import org.example.ratelimit.strategy.IpStrategy;
import org.example.ratelimit.strategy.PathStrategy;
import org.example.ratelimit.strategy.RateLimitStrategy;

import java.util.concurrent.ConcurrentMap;

public enum RateLimitType {
    IP(new IpStrategy()),
    PATH(new PathStrategy()),
    IP_PATH(new IpPathStrategy()),
    GLOBAL(new GlobalStrategy());

    private final RateLimitStrategy strategy;

    RateLimitType(RateLimitStrategy strategy) {
        this.strategy = strategy;
    }

    public void validate(RateLimitRuleRequest request) {
        strategy.validate(request);
    }

    public boolean isGlobal() {
        return strategy.isGlobal();
    }

    public RateLimitRule index(RateLimitRule rule,
                      ConcurrentMap<String, RateLimitRule> ipIndex,
                      ConcurrentMap<String, RateLimitRule> pathExactIndex,
                      ConcurrentMap<String, RateLimitRule> pathPrefixIndex,
                      ConcurrentMap<String, ConcurrentMap<String, RateLimitRule>> ipPathIndex) {
        return strategy.index(rule, ipIndex, pathExactIndex, pathPrefixIndex, ipPathIndex);
    }

    public void removeFromIndex(RateLimitRule rule,
                                ConcurrentMap<String, RateLimitRule> ipIndex,
                                ConcurrentMap<String, RateLimitRule> pathExactIndex,
                                ConcurrentMap<String, RateLimitRule> pathPrefixIndex,
                                ConcurrentMap<String, ConcurrentMap<String, RateLimitRule>> ipPathIndex) {
        strategy.removeFromIndex(rule, ipIndex, pathExactIndex, pathPrefixIndex, ipPathIndex);
    }

    public boolean matches(RateLimitRule rule, String ip, String path) {
        return strategy.matches(rule, ip, path);
    }

    public String computeKey(RateLimitRule rule, String ip, String path) {
        return strategy.computeKey(rule, ip, path);
    }
}