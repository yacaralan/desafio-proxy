package org.example.client;

import org.springframework.http.HttpHeaders;

public class ProxyResponse {
    private final int statusCode;
    private final HttpHeaders headers;
    private final byte[] body;

    public ProxyResponse(int statusCode, HttpHeaders headers, byte[] body) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
    }

    public int getStatus() {
        return statusCode;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }
}
