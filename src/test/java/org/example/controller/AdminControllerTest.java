package org.example.controller;

import org.example.ratelimit.RateLimitRule;
import org.example.ratelimit.RateLimitRuleManager;
import org.example.ratelimit.RateLimitService;
import org.example.ratelimit.RateLimitType;
import org.example.stats.StatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AdminControllerTest {

    private StatsService statsService;
    private RateLimitRuleManager ruleManager;
    private RateLimitService rateLimitService;
    private AdminController controller;

    @BeforeEach
    public void setup() {
        statsService = Mockito.mock(StatsService.class);
        ruleManager = Mockito.mock(RateLimitRuleManager.class);
        rateLimitService = Mockito.mock(RateLimitService.class);
        controller = new AdminController(statsService, ruleManager, rateLimitService);
    }

    @Test
    public void stats_returnsSnapshot() {
        when(statsService.snapshot()).thenReturn(Map.of("foo", 1));
        ResponseEntity<Map<String, Object>> resp = controller.stats();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(1, resp.getBody().get("foo"));
        verify(statsService).snapshot();
    }

    @Test
    public void getRules_returnsRulesFromService() {
        RateLimitRule r = new RateLimitRule(RateLimitType.IP, "1.2.3.4", "/path", 10);
        when(rateLimitService.getRules()).thenReturn(List.of(r));
        ResponseEntity<List<RateLimitRule>> resp = controller.getRules();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<RateLimitRule> bodyList = resp.getBody();
        assertNotNull(bodyList);
        assertEquals(1, bodyList.size());
        assertEquals(r, bodyList.getFirst());
    }

    @Test
    public void addRule_success_returnsOkWithRule() {
        RateLimitRuleRequest req = new RateLimitRuleRequest();
        req.setType("IP");
        req.setIp("1.1.1.1");
        req.setPath("/foo");
        req.setRpm(100L);

        RateLimitRule created = new RateLimitRule("id-1", RateLimitType.IP, "1.1.1.1", "/foo", 100L);
        when(ruleManager.createRule(any())).thenReturn(created);
        // rateLimitService.addRule doesn't need to do anything; just ensure it's called

        ResponseEntity<?> resp = controller.addRule(req);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertInstanceOf(Map.class, resp.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertTrue((Boolean) body.get("ok"));
        assertEquals(created, body.get("rule"));
        verify(ruleManager).createRule(any());
        verify(rateLimitService).addRule(created);
    }

    @Test
    public void addRule_validationError_returnsBadRequest() {
        RateLimitRuleRequest req = new RateLimitRuleRequest();
        req.setType("BAD");
        when(ruleManager.createRule(any())).thenThrow(new IllegalArgumentException("invalid type"));

        ResponseEntity<?> resp = controller.addRule(req);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertInstanceOf(Map.class, resp.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertFalse((Boolean) body.get("ok"));
        assertEquals("invalid type", body.get("error"));
        verify(ruleManager).createRule(any());
        verify(rateLimitService, never()).addRule(any());
    }

    @Test
    public void deleteRule_found_returnsOk() {
        when(rateLimitService.removeRuleById("id-1")).thenReturn(true);
        ResponseEntity<?> resp = controller.deleteRule("id-1");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertInstanceOf(Map.class, resp.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertTrue((Boolean) body.get("ok"));
        assertEquals("id-1", body.get("id"));
    }

    @Test
    public void deleteRule_notFound_returns404() {
        when(rateLimitService.removeRuleById("id-2")).thenReturn(false);
        ResponseEntity<?> resp = controller.deleteRule("id-2");
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertInstanceOf(Map.class, resp.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertFalse((Boolean) body.get("ok"));
        assertEquals("rule not found", body.get("error"));
        assertEquals("id-2", body.get("id"));
    }
}
