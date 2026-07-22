package com.eventledger.account.dto;

import java.math.BigDecimal;
import java.util.List;

public class AccountResponse {

    private final String accountId;
    private final BigDecimal balance;
    private final String currency;
    private final int transactionCount;
    private final List<TransactionResponse> recentTransactions;

    public AccountResponse(String accountId, BigDecimal balance, String currency, int transactionCount,
            List<TransactionResponse> recentTransactions) {
        this.accountId = accountId;
        this.balance = balance;
        this.currency = currency;
        this.transactionCount = transactionCount;
        this.recentTransactions = recentTransactions;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public List<TransactionResponse> getRecentTransactions() {
        return recentTransactions;
    }
}
