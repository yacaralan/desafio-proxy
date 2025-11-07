package org.example.service;

import org.example.client.ProxyClient;
import org.example.client.ProxyResponse;
import org.example.ratelimit.RateLimitService;
import org.example.stats.StatsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProxyServiceTest {
	
	@Mock
	private ProxyClient proxyClient;
	@Mock
	private RateLimitService rateLimitService;
	@Mock
	private StatsService statsService;
	@Mock
	private ProxyResponseMapper mapper;
	
	@Test
	public void forward_withAdminPath_returnsNotFound_andDoesNotCallDownstream() {
		ProxyService service = new ProxyService(proxyClient, rateLimitService, statsService, mapper, "");
		MockServerHttpRequest req = MockServerHttpRequest.get("/admin/config").build();
		ServerWebExchange exchange = MockServerWebExchange.from(req);
		
		Mono<ResponseEntity<byte[]>> respMono = service.forward(exchange, Mono.empty());
		ResponseEntity<byte[]> resp = respMono.block();
		
		assertNotNull(resp);
		assertEquals(404, resp.getStatusCodeValue());
		
		verifyNoInteractions(proxyClient, rateLimitService, statsService, mapper);
	}
	
	@Test
	public void forward_post_withBody_callsProxyClientWithSameBodyMono() {
		ProxyService service = new ProxyService(proxyClient, rateLimitService, statsService, mapper, "");
		MockServerHttpRequest req = MockServerHttpRequest.post("/sites?x=1").build();
		ServerWebExchange exchange = MockServerWebExchange.from(req);
		
		Mono<byte[]> bodyMono = Mono.just("payload".getBytes());
		
		when(rateLimitService.tryConsume(anyString(), anyString())).thenReturn(true);
		
		ProxyResponse proxyResp = mock(ProxyResponse.class);
		when(proxyResp.getStatus()).thenReturn(200);
		ResponseEntity<byte[]> mapped = ResponseEntity.ok("ok".getBytes());
		when(mapper.map(proxyResp, "")).thenReturn(mapped);
		
		when(proxyClient.execute(eq(HttpMethod.POST), eq("/sites?x=1"), any(HttpHeaders.class), same(bodyMono)))
				.thenReturn(Mono.just(proxyResp));
		
		ResponseEntity<byte[]> resp = service.forward(exchange, bodyMono).block();
		
		assertNotNull(resp);
		assertEquals(200, resp.getStatusCodeValue());
		assertArrayEquals("ok".getBytes(), resp.getBody());
		
		verify(proxyClient).execute(eq(HttpMethod.POST), eq("/sites?x=1"), any(HttpHeaders.class), same(bodyMono));
	}
}
