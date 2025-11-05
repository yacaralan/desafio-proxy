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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("mock-client")
public class ProxyUpstreamErrorTest {

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
    public void whenUpstreamReturns400_itIsPropagatedAndStatsRecorded() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"error\":\"bad request\"}".getBytes();

        when(proxyClient.execute(any(), anyString(), any(), any())).thenReturn(Mono.just(new ProxyResponse(400, headers, body)));

        webTestClient.get().uri("/sites").exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody().jsonPath("$.error").isEqualTo("bad request");

        Object upstream4xx = statsService.snapshot().get("upstream_4xx");
        assertThat(upstream4xx).isNotNull();
        assertThat(((Number)upstream4xx).intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void whenUpstreamReturns500_itIsPropagatedAndStatsRecorded() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"error\":\"internal\"}".getBytes();

        when(proxyClient.execute(any(), anyString(), any(), any())).thenReturn(Mono.just(new ProxyResponse(500, headers, body)));

        webTestClient.get().uri("/sites").exchange()
                .expectStatus().is5xxServerError()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody().jsonPath("$.error").isEqualTo("internal");

        Object upstream5xx = statsService.snapshot().get("upstream_5xx");
        assertThat(upstream5xx).isNotNull();
        assertThat(((Number)upstream5xx).intValue()).isGreaterThanOrEqualTo(1);
    }
}

