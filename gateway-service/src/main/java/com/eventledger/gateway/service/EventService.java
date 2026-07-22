package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.entity.EventEntity;
import com.eventledger.gateway.entity.EventStatus;
import com.eventledger.gateway.exception.EventNotFoundException;
import com.eventledger.gateway.repository.EventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final AccountServiceClient accountServiceClient;
    private final MeterRegistry meterRegistry;

    public EventService(EventRepository eventRepository, AccountServiceClient accountServiceClient,
            MeterRegistry meterRegistry) {
        this.eventRepository = eventRepository;
        this.accountServiceClient = accountServiceClient;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Persists the event as PENDING before calling the Account Service, then marks it APPLIED on
     * success. On failure, a freshly-inserted PENDING row is removed so the same eventId can be
     * legitimately retried later instead of being stuck; a PENDING row found from a prior crash is
     * reused (not re-inserted) and retried against the Account Service, which is itself idempotent
     * on eventId.
     */
    @Transactional
    public EventResult submitEvent(EventRequest request) {
        Optional<EventEntity> existing = eventRepository.findById(request.getEventId());
        if (existing.isPresent() && existing.get().getStatus() == EventStatus.APPLIED) {
            meterRegistry.counter("events.duplicate").increment();
            return new EventResult(EventResponse.from(existing.get()), false);
        }

        EventEntity event = existing.orElseGet(() -> eventRepository.save(new EventEntity(request.getEventId(),
                request.getAccountId(), request.getType(), request.getAmount(), request.getCurrency(),
                request.getEventTimestamp(), request.getMetadata(), EventStatus.PENDING, Instant.now())));

        try {
            accountServiceClient.applyTransaction(event);
        } catch (RuntimeException e) {
            if (existing.isEmpty()) {
                eventRepository.delete(event);
            }
            throw e;
        }

        event.markApplied();
        eventRepository.save(event);
        log.info("Applied event {} ({}) for account {}", event.getEventId(), event.getType(), event.getAccountId());
        return new EventResult(EventResponse.from(event), true);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(String eventId) {
        EventEntity event = eventRepository.findById(eventId)
                .filter(e -> e.getStatus() == EventStatus.APPLIED)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEventsForAccount(String accountId) {
        return eventRepository.findByAccountIdAndStatusOrderByEventTimestampAsc(accountId, EventStatus.APPLIED)
                .stream()
                .map(EventResponse::from)
                .toList();
    }
}
