package org.example.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Service
public class StatsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatsService.class);

    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder allowedRequests = new LongAdder();
    private final LongAdder deniedRequests = new LongAdder();
	
    private final LongAdder upstreamClientErrors = new LongAdder(); // 4xx
    private final LongAdder upstreamServerErrors = new LongAdder(); // 5xx

    private final ConcurrentHashMap<String, LongAdder> byPath = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> byIp = new ConcurrentHashMap<>();
	
    private final ConcurrentHashMap<String, LongAdder> upstream4xxByPath = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> upstream5xxByPath = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> upstream4xxByIp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> upstream5xxByIp = new ConcurrentHashMap<>();

    public void record(String ip, String path, boolean allowed) {
        totalRequests.increment();
        if (allowed) allowedRequests.increment(); else deniedRequests.increment();
        byPath.computeIfAbsent(path == null ? "unknown" : path, p -> new LongAdder()).increment();
        byIp.computeIfAbsent(ip == null ? "unknown" : ip, p -> new LongAdder()).increment();
        LOGGER.debug("Recorded request ip={} path={} allowed={}", ip, path, allowed);
    }
	
    public void recordUpstreamStatus(String ip, String path, int status) {
        String p = path == null ? "unknown" : path;
        String i = ip == null ? "unknown" : ip;
        if (status >= 400 && status < 500) {
            upstreamClientErrors.increment();
            upstream4xxByPath.computeIfAbsent(p, k -> new LongAdder()).increment();
            upstream4xxByIp.computeIfAbsent(i, k -> new LongAdder()).increment();
            LOGGER.info("Recorded upstream 4xx status={} for ip={} path={}", status, i, p);
        } else if (status >= 500 && status < 600) {
            upstreamServerErrors.increment();
            upstream5xxByPath.computeIfAbsent(p, k -> new LongAdder()).increment();
            upstream5xxByIp.computeIfAbsent(i, k -> new LongAdder()).increment();
            LOGGER.info("Recorded upstream 5xx status={} for ip={} path={}", status, i, p);
        } else {
            LOGGER.debug("Upstream status {} not counted as error", status);
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new HashMap<>();
        m.put("total", totalRequests.sum());
        m.put("allowed", allowedRequests.sum());
        m.put("denied", deniedRequests.sum());
        m.put("upstream_4xx", upstreamClientErrors.sum());
        m.put("upstream_5xx", upstreamServerErrors.sum());
        m.put("byPath", toSimpleMap(byPath));
        m.put("byIp", toSimpleMap(byIp));
        m.put("upstream_4xx_by_path", toSimpleMap(upstream4xxByPath));
        m.put("upstream_5xx_by_path", toSimpleMap(upstream5xxByPath));
        m.put("upstream_4xx_by_ip", toSimpleMap(upstream4xxByIp));
        m.put("upstream_5xx_by_ip", toSimpleMap(upstream5xxByIp));
        LOGGER.debug("Snapshot called, totals={} allowed={} denied={} upstream4xx={} upstream5xx={}", totalRequests.sum(), allowedRequests.sum(), deniedRequests.sum(), upstreamClientErrors.sum(), upstreamServerErrors.sum());
        return m;
    }

    private Map<String, Long> toSimpleMap(ConcurrentHashMap<String, LongAdder> source) {
        Map<String, Long> r = new HashMap<>();
        for (Map.Entry<String, LongAdder> e : source.entrySet()) r.put(e.getKey(), e.getValue().sum());
        return r;
    }
}