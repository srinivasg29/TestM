package com.eventledger.gateway.client;

import com.eventledger.gateway.entity.EventEntity;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AccountServiceClient {

    private final RestClient restClient;

    public AccountServiceClient(RestClient accountServiceRestClient) {
        this.restClient = accountServiceRestClient;
    }

    public AccountTransactionResponse applyTransaction(EventEntity event) {
        AccountTransactionRequest request = new AccountTransactionRequest(event.getEventId(), event.getType(),
                event.getAmount(), event.getCurrency(), event.getEventTimestamp());
        try {
            return restClient.post()
                    .uri("/accounts/{accountId}/transactions", event.getAccountId())
                    .body(request)
                    .retrieve()
                    .body(AccountTransactionResponse.class);
        } catch (RestClientException e) {
            throw new AccountServiceUnavailableException(
                    "Account Service is unavailable; could not apply transaction for event " + event.getEventId(), e);
        }
    }

    public AccountBalanceResponse getBalance(String accountId) {
        try {
            return restClient.get()
                    .uri("/accounts/{accountId}/balance", accountId)
                    .retrieve()
                    .body(AccountBalanceResponse.class);
        } catch (RestClientException e) {
            throw new AccountServiceUnavailableException(
                    "Account Service is unavailable; could not retrieve balance for account " + accountId, e);
        }
    }
}
