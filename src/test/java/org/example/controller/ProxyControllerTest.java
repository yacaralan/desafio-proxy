package org.example.controller;

import org.example.service.ProxyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = ProxyController.class)
public class ProxyControllerTest {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private ProxyService proxyService;

    @Test
    public void testProxyGetForwardsAndReturnsOk() {
        byte[] body = "{\"id\":\"MLA\"}".getBytes();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<byte[]> resp = ResponseEntity.ok().headers(headers).body(body);

        when(proxyService.forward(any(), any())).thenReturn(Mono.just(resp));

        webClient.get().uri("/sites").exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody().json("{\"id\":\"MLA\"}");
    }

    @Test
    public void testRateLimitExceededReturns429() {
        ResponseEntity<byte[]> resp = ResponseEntity.status(429).body(new byte[0]);
        when(proxyService.forward(any(), any())).thenReturn(Mono.just(resp));

        webClient.get().uri("/sites").exchange()
                .expectStatus().isEqualTo(429);
    }
}
