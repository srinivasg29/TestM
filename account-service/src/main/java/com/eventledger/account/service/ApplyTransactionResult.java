package com.eventledger.account.service;

import com.eventledger.account.dto.TransactionResponse;

public record ApplyTransactionResult(TransactionResponse transaction, boolean created) {
}
