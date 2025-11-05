package org.example.controller;

import org.example.client.ProxyClient;
import org.example.client.ProxyResponse;
import org.example.ratelimit.RateLimitService;
import org.example.stats.StatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("mock-client")
public class ProxyControllerErrorIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ProxyClient proxyClient;

    @MockBean
    private RateLimitService rateLimitService;

    @Autowired
    private StatsService statsService;

    @BeforeEach
    public void setup() {
        when(rateLimitService.tryConsume(anyString(), anyString())).thenReturn(true);
    }

    @Test
    public void whenUpstreamErrors_integrationReturns502AndStatsRecorded() {
        when(proxyClient.execute(any(), anyString(), any(), any())).thenReturn(Mono.error(new RuntimeException("upstream failure")));

        webTestClient.get().uri("/sites").exchange()
                .expectStatus().isEqualTo(org.springframework.http.HttpStatus.BAD_GATEWAY)
                .expectBody().consumeWith(result -> {
                    byte[] body = result.getResponseBody();
                    assertThat(new String(body)).contains("upstream error: upstream failure");
                });

        Object upstream502 = statsService.snapshot().get("upstream_5xx");
        // since we record 502 explicitly, ensure metric increased (>=1)
        // snapshot may return Long or Integer depending on environment; coerce to Number if present
        Object val = statsService.snapshot().get("upstream_5xx");
        assertThat(val).isNotNull();
        assertThat(((Number)val).intValue()).isGreaterThanOrEqualTo(1);
    }
}

