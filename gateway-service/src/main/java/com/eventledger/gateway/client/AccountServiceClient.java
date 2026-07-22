package com.eventledger.gateway.client;

import com.eventledger.gateway.entity.EventEntity;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AccountServiceClient {

    private static final String CIRCUIT_BREAKER_NAME = "accountService";

    private final RestClient restClient;
    private final Tracer tracer;

    public AccountServiceClient(RestClient accountServiceRestClient, Tracer tracer) {
        this.restClient = accountServiceRestClient;
        this.tracer = tracer;
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public AccountTransactionResponse applyTransaction(EventEntity event) {
        AccountTransactionRequest request = new AccountTransactionRequest(event.getEventId(), event.getType(),
                event.getAmount(), event.getCurrency(), event.getEventTimestamp());
        try {
            return restClient.post()
                    .uri("/accounts/{accountId}/transactions", event.getAccountId())
                    .headers(traceHeaders())
                    .body(request)
                    .retrieve()
                    .body(AccountTransactionResponse.class);
        } catch (RestClientException e) {
            throw new AccountServiceUnavailableException(
                    "Account Service is unavailable; could not apply transaction for event " + event.getEventId(), e);
        }
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public AccountBalanceResponse getBalance(String accountId) {
        try {
            return restClient.get()
                    .uri("/accounts/{accountId}/balance", accountId)
                    .headers(traceHeaders())
                    .retrieve()
                    .body(AccountBalanceResponse.class);
        } catch (RestClientException e) {
            throw new AccountServiceUnavailableException(
                    "Account Service is unavailable; could not retrieve balance for account " + accountId, e);
        }
    }

    /**
     * Builds the current trace context's W3C "traceparent" header (format: 00-{traceId}-{spanId}-{01|00},
     * see https://www.w3.org/TR/trace-context/#traceparent-header) directly from TraceContext fields,
     * applied at the request-building call site. Neither Spring Boot's automatic RestClient
     * observation-based propagation nor Micrometer's Propagator.inject() actually added this header in
     * practice in this project's setup - despite every relevant autoconfiguration reporting as matched
     * and the current span being confirmably valid at the call site, Propagator.inject() (OTel bridge)
     * produced an empty carrier, suggesting Tracer.currentSpan() here isn't backed by an active OTel
     * Context.current() scope even though it reports a valid span object. Building the header directly
     * from TraceContext sidesteps that gap entirely.
     */
    private Consumer<HttpHeaders> traceHeaders() {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            return headers -> { };
        }
        TraceContext context = currentSpan.context();
        String traceparent = "00-" + context.traceId() + "-" + context.spanId() + "-" + (context.sampled() ? "01" : "00");
        return headers -> headers.set("traceparent", traceparent);
    }
}
