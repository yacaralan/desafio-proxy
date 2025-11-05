package org.example.service;

import org.example.client.ProxyResponse;
import org.example.util.HeaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ProxyResponseMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyResponseMapper.class);

    public ResponseEntity<byte[]> map(ProxyResponse proxyResp, String defaultContentType) {
        if (proxyResp == null) {
            LOGGER.warn("ProxyResponseMapper.map received null proxyResp");
            return ResponseEntity.status(502).body(new byte[0]);
        }

        HttpHeaders respHeaders = HeaderUtils.filterHopByHop(proxyResp.getHeaders());
        if (!HeaderUtils.hasContentType(respHeaders) && defaultContentType != null && !defaultContentType.isBlank()) {
            respHeaders.set("Content-Type", defaultContentType);
        }

        byte[] body = proxyResp.getBody() == null ? new byte[0] : proxyResp.getBody();
        return ResponseEntity.status(proxyResp.getStatus()).headers(respHeaders).body(body);
    }
}
