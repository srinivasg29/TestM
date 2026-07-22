package com.eventledger.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.eventledger.account.AccountServiceApplication;
import com.eventledger.gateway.dto.BalanceResponse;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.entity.EventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots a real Account Service Spring context (separate process-equivalent, separate in-memory H2
 * database) alongside the Gateway's own test context, so this exercises the full flow over real
 * HTTP between two independently-runnable services, not a mocked downstream.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayAccountServiceIntegrationTest {

    private static final ConfigurableApplicationContext ACCOUNT_SERVICE_CONTEXT;
    private static final int ACCOUNT_SERVICE_PORT;

    static {
        // Command-line-style args (highest property precedence in Spring Boot) are used here rather
        // than .properties(...), because account-service and gateway-service both ship a classpath-root
        // application.yml; with account-service loaded as a test dependency inside the Gateway's JVM,
        // classpath resource lookup for "application.yml" can resolve to either module's file, and only
        // args are guaranteed to win regardless of which one is picked up.
        ACCOUNT_SERVICE_CONTEXT = new SpringApplicationBuilder(AccountServiceApplication.class)
                .run("--server.port=0", "--spring.datasource.url=jdbc:h2:mem:accountdb-it;DB_CLOSE_DELAY=-1");
        ACCOUNT_SERVICE_CONTEXT.registerShutdownHook();
        ACCOUNT_SERVICE_PORT = ACCOUNT_SERVICE_CONTEXT.getEnvironment().getProperty("local.server.port", Integer.class);
    }

    @DynamicPropertySource
    static void accountServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("account-service.base-url", () -> "http://localhost:" + ACCOUNT_SERVICE_PORT);
    }

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private TestRestTemplate restTemplate;

    private String gatewayUrl(String path) {
        return "http://localhost:" + gatewayPort + path;
    }

    private EventRequest event(String eventId, String accountId, EventType type, String amount, Instant timestamp) {
        EventRequest request = new EventRequest();
        request.setEventId(eventId);
        request.setAccountId(accountId);
        request.setType(type);
        request.setAmount(new BigDecimal(amount));
        request.setCurrency("USD");
        request.setEventTimestamp(timestamp);
        return request;
    }

    @Test
    void fullFlow_submitDuplicateOutOfOrderAndBalance() {
        String accountId = "acct-integration-1";
        Instant t1 = Instant.now().minus(3, ChronoUnit.DAYS);
        Instant t2 = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant t3 = Instant.now().minus(1, ChronoUnit.DAYS);

        // 1. Submit a new event -> 201 Created, and it was actually applied on the Account Service.
        ResponseEntity<EventResponse> created = restTemplate.postForEntity(
                gatewayUrl("/events"), event("evt-it-1", accountId, EventType.CREDIT, "200.00", t2),
                EventResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().getEventId()).isEqualTo("evt-it-1");

        // 2. Resubmitting the same eventId is idempotent: 200 OK, no duplicate, no balance change.
        ResponseEntity<EventResponse> duplicate = restTemplate.postForEntity(
                gatewayUrl("/events"), event("evt-it-1", accountId, EventType.CREDIT, "200.00", t2),
                EventResponse.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. Out-of-order arrival: an event with an EARLIER timestamp arrives AFTER one with a later
        // timestamp, and a third arrives last with the latest timestamp of all.
        ResponseEntity<EventResponse> earlierArrivingLate = restTemplate.postForEntity(
                gatewayUrl("/events"), event("evt-it-0", accountId, EventType.DEBIT, "50.00", t1),
                EventResponse.class);
        assertThat(earlierArrivingLate.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<EventResponse> latest = restTemplate.postForEntity(
                gatewayUrl("/events"), event("evt-it-2", accountId, EventType.CREDIT, "30.00", t3),
                EventResponse.class);
        assertThat(latest.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 4. Balance reflects all three transactions correctly regardless of arrival order:
        // 200 (credit) - 50 (debit) + 30 (credit) = 180.
        ResponseEntity<BalanceResponse> balance = restTemplate.getForEntity(
                gatewayUrl("/accounts/" + accountId + "/balance"), BalanceResponse.class);
        assertThat(balance.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(balance.getBody().getBalance()).isEqualByComparingTo("180.00");

        // 5. Listing is chronologically sorted by eventTimestamp, not arrival/insertion order.
        ResponseEntity<EventResponse[]> listing = restTemplate.getForEntity(
                gatewayUrl("/events?account=" + accountId), EventResponse[].class);
        assertThat(listing.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> orderedEventIds = List.of(listing.getBody()).stream().map(EventResponse::getEventId).toList();
        assertThat(orderedEventIds).containsExactly("evt-it-0", "evt-it-1", "evt-it-2");

        // 6. GET /events/{id} returns the single event.
        ResponseEntity<EventResponse> single = restTemplate.getForEntity(
                gatewayUrl("/events/evt-it-2"), EventResponse.class);
        assertThat(single.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(single.getBody().getEventId()).isEqualTo("evt-it-2");
    }
}
