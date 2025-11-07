package org.example.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import org.example.ratelimit.RateLimitService;
import org.example.stats.StatsService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("mock-client")
public class MockClientIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private StatsService statsService;

    @BeforeEach
    public void setUp() {
        // Common stub for all tests: allow requests by default
        when(rateLimitService.tryConsume(anyString(), anyString())).thenReturn(true);
    }

    @Test
    public void testSitesEndpoint() {
        webTestClient.get().uri("/sites").exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$[1].id").isEqualTo("MLA")
                .jsonPath("$[1].name").isEqualTo("Argentina");
    }

    @Test
    public void testListingTypesEndpoint() {
        webTestClient.get().uri("/sites/MLA/listing_types").exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("lowest")
                .jsonPath("$[4].id").isEqualTo("highest");
    }

    @Test
    public void testListingPricesEndpoint() {
        webTestClient.get().uri("/sites/MLA/listing_prices?price=100").exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$[0].listing_type_id").isEqualTo("gold_pro");
    }

    // New tests for categories and category details/attributes
    @Test
    public void testSitesCategoriesEndpoint() {
        webTestClient.get().uri("/sites/MLA/categories").exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("MLA5725")
                .jsonPath("$[0].name").isEqualTo("Accesorios para Vehículos");
    }

    @Test
    public void testCategoryDetailEndpoint() {
        webTestClient.get().uri("/categories/MLA5725").exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.id").isEqualTo("MLA5725")
                .jsonPath("$.name").isEqualTo("Accesorios para Vehículos")
                .jsonPath("$.children_categories[0].id").isEqualTo("MLA4711");
    }

    @Test
    public void testCategoryAttributesEndpoint() {
        webTestClient.get().uri("/categories/MLA5725/attributes").exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("Season")
                .jsonPath("$[1].id").isEqualTo("GENDER");
    }

}
