package com.eventledger.gateway.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.entity.EventType;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
 * Documents Gateway behavior when the Account Service is unreachable or slow. GET endpoints depend
 * only on the Gateway's own H2 database, so they're expected to keep working regardless. A fully
 * unreachable Account Service is handled by AccountServiceClient's exception mapping (503, not a 500
 * or a hang). Since Commit 6, a bounded read timeout (see AccountServiceClientConfig) also protects
 * against a merely slow Account Service, so the Gateway no longer waits out an arbitrarily long
 * downstream delay - see {@link GatewayCircuitBreakerTest} for the circuit breaker's own fast-fail
 * behavior once repeated failures open the circuit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayGracefulDegradationTest {

    private static final WireMockServer ACCOUNT_SERVICE_STUB =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        ACCOUNT_SERVICE_STUB.start();
    }

    @DynamicPropertySource
    static void accountServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("account-service.base-url", ACCOUNT_SERVICE_STUB::baseUrl);
    }

    @AfterAll
    static void shutdownStub() {
        ACCOUNT_SERVICE_STUB.stop();
    }

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void resetStub() {
        ACCOUNT_SERVICE_STUB.resetAll();
    }

    private String url(String path) {
        return "http://localhost:" + gatewayPort + path;
    }

    private EventRequest event(String eventId, String accountId) {
        EventRequest request = new EventRequest();
        request.setEventId(eventId);
        request.setAccountId(accountId);
        request.setType(EventType.CREDIT);
        request.setAmount(new BigDecimal("25.00"));
        request.setCurrency("USD");
        request.setEventTimestamp(Instant.now());
        return request;
    }

    private String accountTransactionResponseJson(String eventId, String accountId) {
        return """
                {
                  "id": 1,
                  "eventId": "%s",
                  "accountId": "%s",
                  "type": "CREDIT",
                  "amount": 25.00,
                  "currency": "USD",
                  "eventTimestamp": "%s",
                  "createdAt": "%s"
                }
                """.formatted(eventId, accountId, Instant.now(), Instant.now());
    }

    @Test
    void getEndpoints_remainHealthy_whenAccountServiceIsUnreachable() {
        String accountId = "acct-degraded-1";

        // Seed one successfully-applied event while the Account Service is healthy.
        ACCOUNT_SERVICE_STUB.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(okJson(accountTransactionResponseJson("evt-seed", accountId))));
        ResponseEntity<EventResponse> created = restTemplate.postForEntity(
                url("/events"), event("evt-seed", accountId), EventResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Account Service becomes unreachable (every call resets the connection).
        ACCOUNT_SERVICE_STUB.resetAll();
        ACCOUNT_SERVICE_STUB.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        // GET endpoints only touch the Gateway's own H2 database, so they're unaffected.
        ResponseEntity<EventResponse> fetched = restTemplate.getForEntity(url("/events/evt-seed"), EventResponse.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<EventResponse[]> listing = restTemplate.getForEntity(
                url("/events?account=" + accountId), EventResponse[].class);
        assertThat(listing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listing.getBody()).hasSize(1);
    }

    @Test
    void postEvents_alreadyReturns503NotAHangOrA500_whenAccountServiceConnectionFails() {
        ACCOUNT_SERVICE_STUB.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/events"), event("evt-unreachable", "acct-degraded-2"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void postEvents_failsFastWithTimeout_insteadOfBlockingOnASlowAccountService() {
        int stubDelayMillis = 5000;
        ACCOUNT_SERVICE_STUB.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(okJson(accountTransactionResponseJson("evt-slow", "acct-degraded-3"))
                        .withFixedDelay(stubDelayMillis)));

        long start = System.nanoTime();
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/events"), event("evt-slow", "acct-degraded-3"), String.class);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        // The configured read timeout (account-service.read-timeout-ms, 2000ms in application.yml)
        // now bounds the wait: the Gateway fails fast with 503 well before the stub's 5s delay
        // elapses, rather than tying up its own thread for the full downstream delay.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(elapsedMillis).isLessThan(stubDelayMillis);
    }
}
