package com.eventledger.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.eventledger.account.dto.AccountResponse;
import com.eventledger.account.dto.BalanceResponse;
import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.entity.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    private TransactionRequest request(String eventId, TransactionType type, String amount, Instant timestamp) {
        TransactionRequest request = new TransactionRequest();
        request.setEventId(eventId);
        request.setType(type);
        request.setAmount(new BigDecimal(amount));
        request.setCurrency("USD");
        request.setEventTimestamp(timestamp);
        return request;
    }

    @Test
    void applyTransaction_createsNewTransactionOnFirstSubmission() {
        ApplyTransactionResult result = accountService.applyTransaction("acct-1",
                request("evt-1", TransactionType.CREDIT, "100.00", Instant.now()));

        assertThat(result.created()).isTrue();
        assertThat(result.transaction().getEventId()).isEqualTo("evt-1");
        assertThat(result.transaction().getAccountId()).isEqualTo("acct-1");
    }

    @Test
    void applyTransaction_duplicateEventId_doesNotDoubleApplyOrChangeBalance() {
        TransactionRequest duplicateEvent = request("evt-dup", TransactionType.CREDIT, "50.00", Instant.now());

        ApplyTransactionResult first = accountService.applyTransaction("acct-2", duplicateEvent);
        ApplyTransactionResult second = accountService.applyTransaction("acct-2", duplicateEvent);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.transaction().getId()).isEqualTo(first.transaction().getId());

        BalanceResponse balance = accountService.getBalance("acct-2");
        assertThat(balance.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void getBalance_isCreditsMinusDebits() {
        accountService.applyTransaction("acct-3", request("evt-3a", TransactionType.CREDIT, "200.00", Instant.now()));
        accountService.applyTransaction("acct-3", request("evt-3b", TransactionType.DEBIT, "75.50", Instant.now()));
        accountService.applyTransaction("acct-3", request("evt-3c", TransactionType.CREDIT, "10.00", Instant.now()));

        BalanceResponse balance = accountService.getBalance("acct-3");

        assertThat(balance.getBalance()).isEqualByComparingTo("134.50");
    }

    @Test
    void getBalance_isUnaffectedByArrivalOrderOfOutOfOrderEvents() {
        Instant t1 = Instant.now().minus(3, ChronoUnit.DAYS);
        Instant t2 = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant t3 = Instant.now().minus(1, ChronoUnit.DAYS);

        // Arrive out of chronological order: t3 first, then t1, then t2.
        accountService.applyTransaction("acct-4", request("evt-4c", TransactionType.DEBIT, "30.00", t3));
        accountService.applyTransaction("acct-4", request("evt-4a", TransactionType.CREDIT, "100.00", t1));
        accountService.applyTransaction("acct-4", request("evt-4b", TransactionType.CREDIT, "20.00", t2));

        BalanceResponse balance = accountService.getBalance("acct-4");

        assertThat(balance.getBalance()).isEqualByComparingTo("90.00");
    }

    @Test
    void getAccount_recentTransactionsAreOrderedByEventTimestampNotArrivalOrder() {
        Instant t1 = Instant.now().minus(3, ChronoUnit.DAYS);
        Instant t2 = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant t3 = Instant.now().minus(1, ChronoUnit.DAYS);

        accountService.applyTransaction("acct-5", request("evt-5b", TransactionType.CREDIT, "20.00", t2));
        accountService.applyTransaction("acct-5", request("evt-5c", TransactionType.CREDIT, "30.00", t3));
        accountService.applyTransaction("acct-5", request("evt-5a", TransactionType.CREDIT, "10.00", t1));

        AccountResponse account = accountService.getAccount("acct-5");

        assertThat(account.getTransactionCount()).isEqualTo(3);
        assertThat(account.getRecentTransactions()).extracting("eventId")
                .containsExactly("evt-5c", "evt-5b", "evt-5a");
    }

    @Test
    void getBalance_forUnknownAccountIsZeroNotAnError() {
        BalanceResponse balance = accountService.getBalance("acct-never-seen");

        assertThat(balance.getBalance()).isEqualByComparingTo("0");
        assertThat(balance.getCurrency()).isNull();
    }
}
