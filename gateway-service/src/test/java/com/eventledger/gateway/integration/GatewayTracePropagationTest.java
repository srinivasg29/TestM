package com.eventledger.gateway.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.entity.EventType;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
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
 * Verifies the Gateway propagates its trace context to the Account Service via a W3C "traceparent"
 * header (format: version-traceId-spanId-flags, e.g. "00-<32 hex>-<16 hex>-01") on every outgoing
 * call, which is what lets both services' structured logs be correlated to the same request.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayTracePropagationTest {

    private static final Pattern TRACEPARENT_PATTERN =
            Pattern.compile("^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$");

    private static final WireMockServer ACCOUNT_SERVICE_STUB =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        ACCOUNT_SERVICE_STUB.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("account-service.base-url", ACCOUNT_SERVICE_STUB::baseUrl);
    }

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + gatewayPort + path;
    }

    @Test
    void submittingAnEvent_propagatesAWellFormedTraceparentHeaderToAccountService() {
        ACCOUNT_SERVICE_STUB.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(okJson("""
                        {
                          "id": 1,
                          "eventId": "evt-trace-1",
                          "accountId": "acct-trace",
                          "type": "CREDIT",
                          "amount": 15.00,
                          "currency": "USD",
                          "eventTimestamp": "%s",
                          "createdAt": "%s"
                        }
                        """.formatted(Instant.now(), Instant.now()))));

        EventRequest request = new EventRequest();
        request.setEventId("evt-trace-1");
        request.setAccountId("acct-trace");
        request.setType(EventType.CREDIT);
        request.setAmount(new BigDecimal("15.00"));
        request.setCurrency("USD");
        request.setEventTimestamp(Instant.now());

        ResponseEntity<String> response = restTemplate.postForEntity(url("/events"), request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        List<ServeEvent> received = ACCOUNT_SERVICE_STUB.getAllServeEvents();
        assertThat(received).hasSize(1);


        String traceparent = received.get(0).getRequest().getHeader("traceparent");
        assertThat(traceparent)
                .as("Account Service should receive a W3C traceparent header from the Gateway")
                .isNotNull()
                .matches(TRACEPARENT_PATTERN);

        assertThat(ACCOUNT_SERVICE_STUB.findAll(postRequestedFor(urlPathMatching("/accounts/.*/transactions"))))
                .hasSize(1);
    }
}
