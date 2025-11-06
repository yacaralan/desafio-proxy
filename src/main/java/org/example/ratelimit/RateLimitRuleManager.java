package org.example.ratelimit;

import org.example.controller.RateLimitRuleRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RateLimitRuleManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitRuleManager.class);
	
    public RateLimitRule createRule(RateLimitRuleRequest req) {
        if (req == null || req.getType() == null) {
            throw new IllegalArgumentException("type is required");
        }

        RateLimitType type;
        try {
            type = RateLimitType.valueOf(req.getType());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid type");
        }

        // Delegate validation to the type
        type.validate(req);

        Long rpmObj = req.getRpm();
        if (rpmObj == null || rpmObj <= 0) {
            throw new IllegalArgumentException("rpm must be a positive number");
        }
        long rpm = rpmObj;

        String ip = req.getIp();
        String path = req.getPath();

        RateLimitRule rule = new RateLimitRule(type, ip, path, rpm);
        LOGGER.info("RateLimitRuleManager created rule={}", rule);
        return rule;
    }
}
