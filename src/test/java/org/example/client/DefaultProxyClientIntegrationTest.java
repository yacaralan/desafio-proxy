package org.example.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultProxyClientIntegrationTest {

    private MockWebServer mockWebServer;
    private DefaultProxyClient client;

    @BeforeEach
    public void setup() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String baseUrl = mockWebServer.url("/").toString();
        WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
        client = new DefaultProxyClient(webClient);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (mockWebServer != null) mockWebServer.shutdown();
    }

    @Test
    public void execute_returns200AndBodyAndHeaders() {
        String body = "{\"ok\":true}";
        MockResponse resp = new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
        mockWebServer.enqueue(resp);

        ProxyResponse pr = client.execute(HttpMethod.GET, "/test", new HttpHeaders(), null).block();
        assertNotNull(pr);
        assertEquals(200, pr.getStatus());
        assertEquals("application/json", pr.getHeaders().getFirst("Content-Type"));
        assertArrayEquals(body.getBytes(), pr.getBody());
    }

    @Test
    public void execute_returns500AndBodyAndHeaders() {
        MockResponse resp = new MockResponse()
                .setResponseCode(500)
                .addHeader("Content-Type", "text/plain")
                .setBody("internal error");
        mockWebServer.enqueue(resp);

        ProxyResponse pr = client.execute(HttpMethod.GET, "/error", new HttpHeaders(), null).block();
        assertNotNull(pr);
        assertEquals(500, pr.getStatus());
        assertEquals("text/plain", pr.getHeaders().getFirst("Content-Type"));
        assertEquals("internal error", new String(pr.getBody()));
    }

    @Test
    public void execute_onConnectionFailure_returns502WithMessage() throws Exception {
        // simulate connection failure by shutting down the server before the call
        mockWebServer.shutdown();

        ProxyResponse pr = client.execute(HttpMethod.GET, "/down", new HttpHeaders(), null).block();
        assertNotNull(pr);
        assertEquals(502, pr.getStatus());
        String msg = new String(pr.getBody());
        assertTrue(msg.startsWith("upstream error:"), "expected upstream error message but was: " + msg);
    }
}

