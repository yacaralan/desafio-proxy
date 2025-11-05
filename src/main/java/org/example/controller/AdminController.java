package org.example.controller;

import org.example.ratelimit.RateLimitService;
import org.example.stats.StatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminController.class);

    private final StatsService statsService;
    private final RateLimitService rateLimitService;

    public AdminController(StatsService statsService, RateLimitService rateLimitService) {
        this.statsService = statsService;
        this.rateLimitService = rateLimitService;
        LOGGER.info("AdminController initialized");
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        LOGGER.debug("Admin /stats called");
        return ResponseEntity.ok(statsService.snapshot());
    }

    @GetMapping("/rules")
    public ResponseEntity<List<RateLimitService.Rule>> getRules() {
        LOGGER.debug("Admin /rules called");
        return ResponseEntity.ok(rateLimitService.getRules());
    }

    @PostMapping("/rules")
    public ResponseEntity<?> addRule(@RequestBody Map<String, String> body) {
        LOGGER.info("Admin /rules POST payload={}", body);
        try {
            String type = body.get("type");
            String pattern = body.get("pattern");
            long rpm = Long.parseLong(body.getOrDefault("rpm", "0"));
            RateLimitService.Type t = RateLimitService.Type.valueOf(type);
            RateLimitService.Rule r = new RateLimitService.Rule(t, pattern, rpm);
            rateLimitService.addRule(r);
            Map<String, Object> resp = new HashMap<>();
            resp.put("ok", true);
            resp.put("rule", r.toString());
            LOGGER.info("Added new rate limit rule={}", r);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            LOGGER.warn("Failed to add rule, body={} error={}", body, e.toString());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
