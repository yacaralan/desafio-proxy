package org.example.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MockProxyClient implements ProxyClient {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(MockProxyClient.class);
	
	private static final class Route {
		final Pattern pattern;
		final BiFunction<String, Matcher, String> resourceResolver; // (path, matcher) -> resourceName
		
		Route(Pattern pattern, BiFunction<String, Matcher, String> resourceResolver) {
			this.pattern = pattern;
			this.resourceResolver = resourceResolver;
		}
	}
	
	// routing table (order matters)
	private static final List<Route> ROUTES = List.of(
			new Route(Pattern.compile("^/sites$", Pattern.CASE_INSENSITIVE), (p, m) -> "mocks/sites.json"),
			new Route(Pattern.compile("^/sites/[^/]+/listing_types$", Pattern.CASE_INSENSITIVE), (p, m) -> "mocks/listing_types.json"),
			new Route(Pattern.compile("^/sites/[^/]+/listing_prices$", Pattern.CASE_INSENSITIVE), (p, m) -> "mocks/listing_prices.json"),
			new Route(Pattern.compile("^/sites/[^/]+/categories$", Pattern.CASE_INSENSITIVE), (p, m) -> "mocks/categories.json"),
			new Route(Pattern.compile("^/categories/([^/]+)/attributes$", Pattern.CASE_INSENSITIVE),
					(p, m) -> String.format("mocks/category_%s_attributes.json", m.group(1))),
			new Route(Pattern.compile("^/categories/([^/]+)$", Pattern.CASE_INSENSITIVE),
					(p, m) -> String.format("mocks/category_%s.json", m.group(1)))
	);
	
	@Override
	public Mono<ProxyResponse> execute(HttpMethod method, String uri, HttpHeaders headers, Mono<byte[]> body) {
		LOGGER.info("MockProxyClient: incoming request method={} uri={} headers={}", method, uri, headers);
		
		if (method != HttpMethod.GET) {
			LOGGER.info("MockProxyClient: method {} not allowed for mocks, returning 405", method);
			return Mono.just(new ProxyResponse(405, new HttpHeaders(), new byte[0]));
		}
		
		try {
			String path = uri == null ? "/" : uri.split("\\?")[0]; // strip query
			
			for (Route route : ROUTES) {
				Matcher matcher = route.pattern.matcher(path);
				if (matcher.matches()) {
					String resourceName = route.resourceResolver.apply(path, matcher);
					LOGGER.info("MockProxyClient: matched path='{}' -> serving resource='{}'", path, resourceName);
					byte[] bytes = readResource(resourceName);
					HttpHeaders resp = new HttpHeaders();
					return Mono.just(new ProxyResponse(200, resp, bytes));
				}
			}
			
			LOGGER.info("MockProxyClient: no mock route matched for path='{}', returning 404", path);
			return Mono.just(new ProxyResponse(404, new HttpHeaders(), new byte[0]));
		} catch (IOException e) {
			LOGGER.error("MockProxyClient: error reading mock resource: {}", e.getMessage(), e);
			// do not force Content-Type header here; return empty headers and error body
			HttpHeaders resp = new HttpHeaders();
			return Mono.just(new ProxyResponse(500, resp, ("error: " + e.getMessage()).getBytes()));
		}
	}
	
	private byte[] readResource(String path) throws IOException {
		ClassPathResource resource = new ClassPathResource(path);
		try (InputStream in = resource.getInputStream()) {
			return in.readAllBytes();
		}
	}
}
