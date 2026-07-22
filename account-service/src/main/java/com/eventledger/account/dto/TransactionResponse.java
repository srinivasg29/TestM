package com.eventledger.account.dto;

import com.eventledger.account.entity.TransactionEntity;
import com.eventledger.account.entity.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public class TransactionResponse {

    private final Long id;
    private final String eventId;
    private final String accountId;
    private final TransactionType type;
    private final BigDecimal amount;
    private final String currency;
    private final Instant eventTimestamp;
    private final Instant createdAt;

    public TransactionResponse(Long id, String eventId, String accountId, TransactionType type, BigDecimal amount,
            String currency, Instant eventTimestamp, Instant createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
        this.createdAt = createdAt;
    }

    public static TransactionResponse from(TransactionEntity entity) {
        return new TransactionResponse(entity.getId(), entity.getEventId(), entity.getAccountId(), entity.getType(),
                entity.getAmount(), entity.getCurrency(), entity.getEventTimestamp(), entity.getCreatedAt());
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
