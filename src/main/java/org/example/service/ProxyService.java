package org.example.service;

import org.example.client.ProxyClient;
import org.example.client.ProxyResponse;
import org.example.ratelimit.RateLimitService;
import org.example.stats.StatsService;
import org.example.util.HeaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Service
public class ProxyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyService.class);

    private final ProxyClient proxyClient;
    private final RateLimitService rateLimitService;
    private final StatsService statsService;
    private final ProxyResponseMapper mapper;
    private final String defaultContentType;

    public ProxyService(ProxyClient proxyClient, RateLimitService rateLimitService, StatsService statsService,
                        ProxyResponseMapper mapper,
                        @Value("${proxy.default-content-type:}") String defaultContentType) {
        this.proxyClient = proxyClient;
        this.rateLimitService = rateLimitService;
        this.statsService = statsService;
        this.mapper = mapper;
        this.defaultContentType = defaultContentType == null ? "" : defaultContentType.trim();
        LOGGER.info("ProxyService initialized (defaultContentType={})", this.defaultContentType);
    }

    public Mono<ResponseEntity<byte[]>> forward(ServerWebExchange exchange, Mono<byte[]> body) {
        String path = exchange.getRequest().getURI().getRawPath();

        // exclude admin endpoints
        if (path.startsWith("/admin") || path.startsWith("/actuator")) {
            LOGGER.debug("Request to admin/actuator excluded from proxy: {}", path);
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

        String ip = extractIp(exchange);
        boolean allowed = rateLimitService.tryConsume(ip, path);
        statsService.record(ip, path, allowed);
        if (!allowed) {
            LOGGER.info("Rate limit exceeded for ip={} path={}", ip, path);
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build());
        }

        HttpMethod method = exchange.getRequest().getMethod();
        String uri = buildUri(exchange);
        HttpHeaders copy = HeaderUtils.filterHopByHop(exchange.getRequest().getHeaders());

        LOGGER.info("Forwarding request method={} uri={} headers={}", method, uri, copy);

        Mono<ProxyResponse> responseMono = requiresBody(method)
                ? proxyClient.execute(method, uri, copy, body)
                : proxyClient.execute(method, uri, copy, Mono.empty());
		
        return responseMono
                .map(pr -> mapProxyResponse(pr, ip, path, uri))
                .onErrorResume(ex -> getErrorResponseEntityMono(ex, ip, path));
    }

    private Mono<ResponseEntity<byte[]>> getErrorResponseEntityMono(Throwable ex, String ip, String path) {
        statsService.recordUpstreamStatus(ip, path, 502);
        HttpHeaders headers = new HttpHeaders();
        if (defaultContentType != null && !defaultContentType.isBlank()) headers.set("Content-Type", defaultContentType);
        String msg = "upstream error: " + ex.getMessage();
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).headers(headers).body(msg.getBytes()));
    }

    private String buildUri(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getRawPath();
        String rawQuery = exchange.getRequest().getURI().getRawQuery();
        return path + (rawQuery == null ? "" : "?" + rawQuery);
    }

    private ResponseEntity<byte[]> mapProxyResponse(ProxyResponse proxyResp, String ip, String path, String uri) {
        if (proxyResp == null) {
            LOGGER.warn("Proxy client returned null for uri={}", uri);
            statsService.recordUpstreamStatus(ip, path, 502);
            HttpHeaders headers = new HttpHeaders();
            if (defaultContentType != null && !defaultContentType.isBlank()) headers.set("Content-Type", defaultContentType);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).headers(headers).body(new byte[0]);
        }

        statsService.recordUpstreamStatus(ip, path, proxyResp.getStatus());
        return mapper.map(proxyResp, defaultContentType);
    }

    private boolean requiresBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH || method == HttpMethod.DELETE;
    }

    private String extractIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null ? "unknown" : remote.getAddress().getHostAddress();
    }
}
