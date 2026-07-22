package com.eventledger.account.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventledger.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
class TransactionControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        String body = "{}";

        mockMvc.perform(post("/accounts/acct-1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonPositiveAmount() throws Exception {
        String body = """
                {
                  "eventId": "evt-1",
                  "type": "CREDIT",
                  "amount": 0,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/accounts/acct-1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownTransactionType() throws Exception {
        String body = """
                {
                  "eventId": "evt-1",
                  "type": "TRANSFER",
                  "amount": 10,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/accounts/acct-1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
