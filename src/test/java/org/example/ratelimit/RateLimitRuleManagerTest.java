package org.example.ratelimit;

import org.example.controller.RateLimitRuleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RateLimitRuleManagerTest {

    private RateLimitRuleManager manager;

    @BeforeEach
    public void setup() {
        manager = new RateLimitRuleManager();
    }

    @Test
    public void createRule_nullRequest_throws() {
        assertThrows(IllegalArgumentException.class, () -> manager.createRule(null));
    }

    @Test
    public void createRule_missingType_throws() {
        RateLimitRuleRequest req = new RateLimitRuleRequest();
        req.setIp("1.1.1.1");
        req.setPath("/foo");
        req.setRpm(10L);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> manager.createRule(req));
        assertEquals("type is required", ex.getMessage());
    }

    @Test
    public void createRule_invalidType_throws() {
        RateLimitRuleRequest req = new RateLimitRuleRequest();
        req.setType("BAD");
        req.setIp("1.1.1.1");
        req.setPath("/foo");
        req.setRpm(10L);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> manager.createRule(req));
        assertEquals("invalid type", ex.getMessage());
    }

    @Test
    public void createRule_invalidRpm_throws() {
        RateLimitRuleRequest req = new RateLimitRuleRequest();
        req.setType("IP");
        req.setIp("1.1.1.1");
        req.setPath("/foo");
        req.setRpm(0L);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> manager.createRule(req));
        assertEquals("rpm must be a positive number", ex.getMessage());
    }

    @Test
    public void createRule_ipTypeWithoutIp_throws() {
        RateLimitRuleRequest req = new RateLimitRuleRequest();
        req.setType("IP");
        req.setPath("/foo");
        req.setRpm(5L);
        // IpStrategy.validate should throw
        assertThrows(IllegalArgumentException.class, () -> manager.createRule(req));
    }

    @Test
    public void createRule_success_createsRuleWithExpectedFields() {
        RateLimitRuleRequest req = new RateLimitRuleRequest();
        req.setType("IP");
        req.setIp("10.0.0.1");
        req.setPath("/bar");
        req.setRpm(123L);

        RateLimitRule rule = manager.createRule(req);
        assertNotNull(rule.getId());
        assertEquals(RateLimitType.IP, rule.getType());
        assertEquals("10.0.0.1", rule.getIp());
        assertEquals("/bar", rule.getPath());
        assertEquals(123L, rule.getRpm());
    }
}

