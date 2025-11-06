package org.example.controller;

import org.example.ratelimit.RateLimitRule;
import org.example.ratelimit.RateLimitRuleManager;
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
	private final RateLimitRuleManager ruleManager;
	private final RateLimitService rateLimitService;
	
	public AdminController(StatsService statsService, RateLimitRuleManager ruleManager, RateLimitService rateLimitService) {
		this.statsService = statsService;
		this.ruleManager = ruleManager;
		this.rateLimitService = rateLimitService;
		LOGGER.info("AdminController initialized");
	}
	
	@GetMapping("/stats")
	public ResponseEntity<Map<String, Object>> stats() {
		LOGGER.debug("Admin /stats called");
		return ResponseEntity.ok(statsService.snapshot());
	}
	
	@GetMapping("/rules")
	public ResponseEntity<List<RateLimitRule>> getRules() {
		LOGGER.debug("Admin /rules called");
		return ResponseEntity.ok(rateLimitService.getRules());
	}
	
	@PostMapping("/rules")
	public ResponseEntity<?> addRule(@RequestBody RateLimitRuleRequest req) {
		LOGGER.info("Admin /rules POST payload={}", req);
		try {
			// validate and create the rule object (manager only creates)
			RateLimitRule rule = ruleManager.createRule(req);
			// controller persists it into the rateLimitService
			rateLimitService.addRule(rule);
			
			Map<String, Object> resp = new HashMap<>();
			resp.put("ok", true);
			resp.put("rule", rule);
			LOGGER.info("Added new rate limit rule={}", rule);
			return ResponseEntity.ok(resp);
		} catch (IllegalArgumentException e) {
			LOGGER.warn("Validation failed for rule request {}: {}", req, e.getMessage());
			return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
		} catch (Exception e) {
			LOGGER.error("Failed to add rule, req={} error={}", req, e.toString());
			return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
		}
	}
	
	@DeleteMapping("/rules/{id}")
	public ResponseEntity<?> deleteRule(@PathVariable("id") String id) {
		LOGGER.info("Admin DELETE /rules/{} called", id);
		boolean removed = rateLimitService.removeRuleById(id);
		if (removed) {
			return ResponseEntity.ok(Map.of("ok", true, "id", id));
		} else {
			return ResponseEntity.status(404).body(Map.of("ok", false, "error", "rule not found", "id", id));
		}
	}
}
