package org.example.controller;

import org.example.service.ProxyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class ProxyController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyController.class);

    private final ProxyService proxyService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @RequestMapping("/**")
    public Mono<ResponseEntity<byte[]>> proxy(ServerWebExchange exchange, @RequestBody(required = false) Mono<byte[]> body) {
        // Controller kept intentionally thin: delegate all proxy logic to ProxyService
        LOGGER.debug("Incoming request delegated to ProxyService: method={} path={} headers={}",
                exchange.getRequest().getMethod(), exchange.getRequest().getURI().getRawPath(), exchange.getRequest().getHeaders());

        return proxyService.forward(exchange, body);
    }
}
