package com.eventledger.gateway.service;

import com.eventledger.gateway.dto.EventResponse;

public record EventResult(EventResponse event, boolean created) {
}
