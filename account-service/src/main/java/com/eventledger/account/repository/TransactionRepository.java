package com.eventledger.account.repository;

import com.eventledger.account.entity.TransactionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    Optional<TransactionEntity> findByEventId(String eventId);

    List<TransactionEntity> findByAccountIdOrderByEventTimestampAsc(String accountId);

    List<TransactionEntity> findByAccountIdOrderByEventTimestampDesc(String accountId);
}
