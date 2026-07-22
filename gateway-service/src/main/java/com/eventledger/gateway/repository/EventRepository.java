package com.eventledger.gateway.repository;

import com.eventledger.gateway.entity.EventEntity;
import com.eventledger.gateway.entity.EventStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<EventEntity, String> {

    List<EventEntity> findByAccountIdAndStatusOrderByEventTimestampAsc(String accountId, EventStatus status);
}
