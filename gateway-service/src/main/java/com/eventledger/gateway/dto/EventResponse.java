package com.eventledger.gateway.dto;

import com.eventledger.gateway.entity.EventEntity;
import com.eventledger.gateway.entity.EventStatus;
import com.eventledger.gateway.entity.EventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public class EventResponse {

    private final String eventId;
    private final String accountId;
    private final EventType type;
    private final BigDecimal amount;
    private final String currency;
    private final Instant eventTimestamp;
    private final Map<String, Object> metadata;
    private final EventStatus status;
    private final Instant createdAt;

    public EventResponse(String eventId, String accountId, EventType type, BigDecimal amount, String currency,
            Instant eventTimestamp, Map<String, Object> metadata, EventStatus status, Instant createdAt) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
        this.metadata = metadata;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static EventResponse from(EventEntity entity) {
        return new EventResponse(entity.getEventId(), entity.getAccountId(), entity.getType(), entity.getAmount(),
                entity.getCurrency(), entity.getEventTimestamp(), entity.getMetadata(), entity.getStatus(),
                entity.getCreatedAt());
    }

    public String getEventId() {
        return eventId;
    }

    public String getAccountId() {
        return accountId;
    }

    public EventType getType() {
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

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public EventStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
