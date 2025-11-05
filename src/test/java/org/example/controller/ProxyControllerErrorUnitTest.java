package org.example.controller;

import org.example.service.ProxyService;
import org.example.stats.StatsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = ProxyController.class)
public class ProxyControllerErrorUnitTest {

    @Autowired
    private WebTestClient client;

    @MockBean
    private ProxyService proxyService;

    @Test
    public void whenUpstreamEmitsError_controllerReturns502AndRecordsStat_withDefaultContentType() {
        ResponseEntity<byte[]> resp = ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body("upstream error: boom".getBytes());

        when(proxyService.forward(any(), any())).thenReturn(Mono.just(resp));

        client.get().uri("/sites").exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_TYPE, "application/json")
                .expectBody().consumeWith(r -> {
                    assertThat(new String(r.getResponseBody())).contains("upstream error: boom");
                });
    }

    @Test
    public void whenUpstreamReturnsNull_controllerReturns502AndRecordsStat_withDefaultContentType() {
        ResponseEntity<byte[]> resp = ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(new byte[0]);

        when(proxyService.forward(any(), any())).thenReturn(Mono.just(resp));

        client.get().uri("/sites").exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_TYPE, "application/json");
    }
}
