package org.example.config;

import org.example.client.DefaultProxyClient;
import org.example.client.ProxyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class ProxyConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyConfig.class);

    @Value("${proxy.target-base-url:http://api.mercadolibre.com}")
    private String targetBaseUrl;

    @Bean
    public WebClient upstreamWebClient() {
        LOGGER.info("Creating proxy WebClient for targetBaseUrl={}", targetBaseUrl);

        HttpClient httpClient = HttpClient.create()
                .followRedirect(false); // explicitly disable redirects

        return WebClient.builder()
                .baseUrl(targetBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(ProxyClient.class)
    public ProxyClient proxyClient(WebClient upstreamWebClient) {
        // Default implementation delegates to the configured WebClient; can be replaced by a mock in tests
        return new DefaultProxyClient(upstreamWebClient);
    }
}
