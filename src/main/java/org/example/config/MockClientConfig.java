package org.example.config;

import org.example.client.MockProxyClient;
import org.example.client.ProxyClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("mock-client")
public class MockClientConfig {

    @Bean
    public ProxyClient proxyClient() {
        return new MockProxyClient();
    }
}

