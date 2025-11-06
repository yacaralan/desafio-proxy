package org.example.ratelimit.strategy;

import org.example.controller.RateLimitRuleRequest;
import org.example.ratelimit.RateLimitRule;

import java.util.concurrent.ConcurrentMap;

public class GlobalStrategy implements RateLimitStrategy {
    @Override
    public void validate(RateLimitRuleRequest request) {
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    @Override
    public RateLimitRule index(RateLimitRule r, ConcurrentMap<String, RateLimitRule> ipIndex, ConcurrentMap<String, RateLimitRule> pathExactIndex, ConcurrentMap<String, RateLimitRule> pathPrefixIndex, ConcurrentMap<String, ConcurrentMap<String, RateLimitRule>> ipPathIndex) {
        // nothing to index for global
        return null;
    }

    @Override
    public void removeFromIndex(RateLimitRule r, ConcurrentMap<String, RateLimitRule> ipIndex, ConcurrentMap<String, RateLimitRule> pathExactIndex, ConcurrentMap<String, RateLimitRule> pathPrefixIndex, ConcurrentMap<String, ConcurrentMap<String, RateLimitRule>> ipPathIndex) {
        // nothing to remove
    }

    @Override
    public boolean matches(RateLimitRule rule, String ip, String path) {
        return true;
    }

    @Override
    public String computeKey(RateLimitRule rule, String ip, String path) {
        return "global";
    }
}
