package com.eventledger.gateway.controller;

import com.eventledger.gateway.client.AccountBalanceResponse;
import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.dto.BalanceResponse;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.dto.EventResponse;
import com.eventledger.gateway.service.EventResult;
import com.eventledger.gateway.service.EventService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {

    private final EventService eventService;
    private final AccountServiceClient accountServiceClient;

    public EventController(EventService eventService, AccountServiceClient accountServiceClient) {
        this.eventService = eventService;
        this.accountServiceClient = accountServiceClient;
    }

    @PostMapping("/events")
    public ResponseEntity<EventResponse> submitEvent(@Valid @RequestBody EventRequest request) {
        EventResult result = eventService.submitEvent(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.event());
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable String id) {
        return ResponseEntity.ok(eventService.getEvent(id));
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventResponse>> listEvents(@RequestParam("account") String accountId) {
        return ResponseEntity.ok(eventService.listEventsForAccount(accountId));
    }

    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String accountId) {
        AccountBalanceResponse balance = accountServiceClient.getBalance(accountId);
        return ResponseEntity.ok(new BalanceResponse(balance.getAccountId(), balance.getBalance(), balance.getCurrency()));
    }
}
