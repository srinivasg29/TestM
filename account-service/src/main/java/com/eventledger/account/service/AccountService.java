package com.eventledger.account.service;

import com.eventledger.account.dto.AccountResponse;
import com.eventledger.account.dto.BalanceResponse;
import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.dto.TransactionResponse;
import com.eventledger.account.entity.TransactionEntity;
import com.eventledger.account.entity.TransactionType;
import com.eventledger.account.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final int RECENT_TRANSACTIONS_LIMIT = 20;

    private final TransactionRepository transactionRepository;

    public AccountService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public ApplyTransactionResult applyTransaction(String accountId, TransactionRequest request) {
        return transactionRepository.findByEventId(request.getEventId())
                .map(existing -> new ApplyTransactionResult(TransactionResponse.from(existing), false))
                .orElseGet(() -> insertNewTransaction(accountId, request));
    }

    private ApplyTransactionResult insertNewTransaction(String accountId, TransactionRequest request) {
        TransactionEntity entity = new TransactionEntity(request.getEventId(), accountId, request.getType(),
                request.getAmount(), request.getCurrency(), request.getEventTimestamp(), Instant.now());
        try {
            TransactionEntity saved = transactionRepository.save(entity);
            return new ApplyTransactionResult(TransactionResponse.from(saved), true);
        } catch (DataIntegrityViolationException e) {
            // Concurrent submission of the same eventId raced us to the unique constraint;
            // the other submission won, so treat this as a duplicate rather than an error.
            TransactionEntity existing = transactionRepository.findByEventId(request.getEventId())
                    .orElseThrow(() -> e);
            return new ApplyTransactionResult(TransactionResponse.from(existing), false);
        }
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountId) {
        List<TransactionEntity> transactions = transactionRepository.findByAccountIdOrderByEventTimestampAsc(accountId);
        BigDecimal balance = computeBalance(transactions);
        String currency = transactions.isEmpty() ? null : transactions.get(0).getCurrency();
        return new BalanceResponse(accountId, balance, currency);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountId) {
        List<TransactionEntity> chronological = transactionRepository.findByAccountIdOrderByEventTimestampAsc(accountId);
        BigDecimal balance = computeBalance(chronological);
        String currency = chronological.isEmpty() ? null : chronological.get(0).getCurrency();

        List<TransactionResponse> recent = transactionRepository.findByAccountIdOrderByEventTimestampDesc(accountId)
                .stream()
                .limit(RECENT_TRANSACTIONS_LIMIT)
                .map(TransactionResponse::from)
                .toList();

        return new AccountResponse(accountId, balance, currency, chronological.size(), recent);
    }

    private BigDecimal computeBalance(List<TransactionEntity> transactions) {
        BigDecimal balance = BigDecimal.ZERO;
        for (TransactionEntity transaction : transactions) {
            balance = transaction.getType() == TransactionType.CREDIT
                    ? balance.add(transaction.getAmount())
                    : balance.subtract(transaction.getAmount());
        }
        return balance;
    }
}
