package org.example.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

public interface ProxyClient {
    Mono<ProxyResponse> execute(HttpMethod method, String uri, HttpHeaders headers, Mono<byte[]> body);
}

