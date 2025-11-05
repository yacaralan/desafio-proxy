package org.example.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class DefaultProxyClient implements ProxyClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultProxyClient.class);

    private final WebClient webClient;

    public DefaultProxyClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<ProxyResponse> execute(HttpMethod method, String uri, HttpHeaders headers, Mono<byte[]> body) {
        // Log at INFO level the upstream request that will be executed
        LOGGER.info("Executing upstream request method={} uri={} headers={}", method, uri, headers);

        WebClient.RequestBodySpec req = webClient.method(method).uri(uri).headers(h -> {
            if (headers != null) h.addAll(headers);
        });

        WebClient.RequestHeadersSpec<?> spec;
        if (body == null) {
            spec = req.body(BodyInserters.empty());
        } else {
            spec = req.body(BodyInserters.fromPublisher(body, byte[].class));
        }

        return spec.exchangeToMono(resp -> resp.toEntity(byte[].class)
                .map(entity -> {
                    // Log upstream response for debugging
                    LOGGER.info("Upstream responded status={} headers={}", entity.getStatusCode().value(), entity.getHeaders());
                    byte[] b = entity.getBody() == null ? new byte[0] : entity.getBody();
                    return new ProxyResponse(entity.getStatusCode().value(), entity.getHeaders(), b);
                }))
                .onErrorResume(ex -> {
                    HttpHeaders eh = new HttpHeaders();
                    // do not force Content-Type header here; return empty headers and error body
                    String msg = "upstream error: " + ex.getMessage();
                    LOGGER.error("Upstream call failed: {}", ex.getMessage(), ex);
                    return Mono.just(new ProxyResponse(502, eh, msg.getBytes()));
                });
    }
}
