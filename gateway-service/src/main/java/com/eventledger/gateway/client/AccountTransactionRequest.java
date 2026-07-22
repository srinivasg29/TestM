package com.eventledger.gateway.client;

import com.eventledger.gateway.entity.EventType;
import java.math.BigDecimal;
import java.time.Instant;

public class AccountTransactionRequest {

    private String eventId;
    private EventType type;
    private BigDecimal amount;
    private String currency;
    private Instant eventTimestamp;

    public AccountTransactionRequest() {
    }

    public AccountTransactionRequest(String eventId, EventType type, BigDecimal amount, String currency,
            Instant eventTimestamp) {
        this.eventId = eventId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Instant eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }
}
