package com.eventledger.gateway.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.entity.EventType;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Uses a tighter circuit breaker configuration (small sliding window) than production so the circuit
 * can be reliably driven open within a handful of calls, then verifies the open circuit short-circuits
 * immediately - the request never reaches Account Service at all - rather than attempting (and
 * failing) the call again.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayCircuitBreakerTest {

    private static final WireMockServer ACCOUNT_SERVICE_STUB =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        ACCOUNT_SERVICE_STUB.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("account-service.base-url", ACCOUNT_SERVICE_STUB::baseUrl);
        registry.add("resilience4j.circuitbreaker.instances.accountService.sliding-window-size", () -> 4);
        registry.add("resilience4j.circuitbreaker.instances.accountService.minimum-number-of-calls", () -> 4);
        registry.add("resilience4j.circuitbreaker.instances.accountService.wait-duration-in-open-state", () -> "10s");
    }

    @AfterAll
    static void shutdownStub() {
        ACCOUNT_SERVICE_STUB.stop();
    }

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + gatewayPort + path;
    }

    private EventRequest event(String eventId) {
        EventRequest request = new EventRequest();
        request.setEventId(eventId);
        request.setAccountId("acct-breaker");
        request.setType(EventType.CREDIT);
        request.setAmount(new BigDecimal("10.00"));
        request.setCurrency("USD");
        request.setEventTimestamp(Instant.now());
        return request;
    }

    @Test
    void circuitOpens_afterRepeatedFailures_andShortCircuitsSubsequentCalls() {
        ACCOUNT_SERVICE_STUB.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withStatus(500).withBody("Account Service internal error")));

        // Drive 4 failing calls - matches minimum-number-of-calls, all failures, so the circuit opens.
        for (int i = 0; i < 4; i++) {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url("/events"), event("evt-breaker-" + i), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
        assertThat(ACCOUNT_SERVICE_STUB.findAll(postRequestedFor(urlPathMatching("/accounts/.*/transactions"))))
                .hasSize(4);

        // The circuit is now open: this call must short-circuit fast (no network attempt at all)
        // rather than trying and failing again.
        long start = System.nanoTime();
        ResponseEntity<String> shortCircuited = restTemplate.postForEntity(
                url("/events"), event("evt-breaker-open"), String.class);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(shortCircuited.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(elapsedMillis).isLessThan(500);
        assertThat(ACCOUNT_SERVICE_STUB.findAll(postRequestedFor(urlPathMatching("/accounts/.*/transactions"))))
                .hasSize(4);
    }
}
