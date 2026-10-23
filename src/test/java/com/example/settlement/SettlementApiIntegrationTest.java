package com.example.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end REST tests: authentication and role enforcement, request
 * validation, the consistent error envelope and the full trade-to-settlement flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Settlement REST API")
class SettlementApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String uniqueReference() {
        return "TRD-IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String tradeBody(String reference) {
        return """
                {
                  "tradeReference": "%s",
                  "commodity": "Brent Crude Oil",
                  "tradeType": "SELL",
                  "buyerName": "Shell Trading Ltd",
                  "sellerName": "BP Energy",
                  "quantity": 50000.0000,
                  "unitOfMeasure": "BARREL",
                  "unitPrice": 82.4500,
                  "currencyCode": "USD",
                  "tradeDate": "2026-01-15",
                  "deliveryDate": "2026-02-01"
                }
                """.formatted(reference);
    }

    private long createTrade(String reference) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeBody(reference)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    @Test
    @DisplayName("requests without a token are rejected with 401")
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/trades"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the audit trail is off limits for the TRADER role")
    @WithMockUser(username = "trader1", roles = "TRADER")
    void tradersMayNotReadTheAuditTrail() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("invalid credentials produce 401 from the login endpoint")
    void loginWithUnknownUserIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nobody","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("bean validation failures return 400 with per-field details")
    @WithMockUser(username = "trader1", roles = "TRADER")
    void validationFailureReturnsEnrichedError() throws Exception {
        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commodity": "",
                                  "buyerName": "Buyer",
                                  "sellerName": "Seller",
                                  "quantity": 0,
                                  "unitOfMeasure": "BARREL",
                                  "unitPrice": -3,
                                  "currencyCode": "US",
                                  "tradeDate": "2026-01-15"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details[?(@.field=='commodity')]").exists())
                .andExpect(jsonPath("$.details[?(@.field=='tradeType')]").exists())
                .andExpect(jsonPath("$.details[?(@.field=='quantity')]").exists())
                .andExpect(jsonPath("$.details[?(@.field=='currencyCode')]").exists());
    }

    @Test
    @DisplayName("a malformed JSON body returns 400")
    @WithMockUser(username = "trader1", roles = "TRADER")
    void malformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("a created trade can be read back by id and by reference")
    @WithMockUser(username = "trader1", roles = "TRADER")
    void createAndReadTradeRoundTrip() throws Exception {
        String reference = uniqueReference();
        long id = createTrade(reference);

        mockMvc.perform(get("/api/v1/trades/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tradeReference").value(reference))
                .andExpect(jsonPath("$.data.invoiceEligible").value(false));

        mockMvc.perform(get("/api/v1/trades/reference/{reference}", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));
    }

    @Test
    @DisplayName("a duplicate trade reference returns 409")
    @WithMockUser(username = "trader1", roles = "TRADER")
    void duplicateReferenceReturnsConflict() throws Exception {
        String reference = uniqueReference();
        createTrade(reference);

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tradeBody(reference)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("an unknown trade id returns 404")
    @WithMockUser(username = "trader1", roles = "TRADER")
    void unknownTradeReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/trades/99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("invoicing a trade that is not COMPLETED returns 400")
    @WithMockUser(username = "finance1", roles = "FINANCE")
    void invoicingADraftTradeIsRejected() throws Exception {
        long id = createTrade(uniqueReference());

        mockMvc.perform(post("/api/v1/invoices/from-trade/{tradeId}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TRADE_NOT_INVOICE_ELIGIBLE"));
    }

    @Test
    @DisplayName("a payment without an invoice id returns 400")
    @WithMockUser(username = "finance1", roles = "FINANCE")
    void invalidPaymentRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentDate":"2026-01-20","amount":100,"paymentMethod":"BANK_TRANSFER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("trades cannot skip the CONFIRMED step of the state machine")
    @WithMockUser(username = "trader1", roles = "TRADER")
    void invalidTradeTransitionReturnsConflict() throws Exception {
        long id = createTrade(uniqueReference());

        mockMvc.perform(put("/api/v1/trades/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("dashboard endpoints are available to authenticated finance users")
    @WithMockUser(username = "finance1", roles = "FINANCE")
    void dashboardSummaryIsAvailable() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalTrades").isNumber())
                .andExpect(jsonPath("$.data.outstandingByCurrency").isArray());

        mockMvc.perform(get("/api/v1/dashboard/invoice-status"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/dashboard/payment-summary"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/dashboard/overdue-summary"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/dashboard/reconciliation-summary"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("trade -> complete -> invoice -> issue -> full payment -> reconcile settles the invoice")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void fullSettlementLifecycle() throws Exception {
        long tradeId = createTrade(uniqueReference());

        mockMvc.perform(put("/api/v1/trades/{id}", tradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(put("/api/v1/trades/{id}", tradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceEligible").value(true));

        MvcResult invoiceResult = mockMvc.perform(post("/api/v1/invoices/from-trade/{tradeId}", tradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.subtotal").value(4122500.00))
                .andExpect(jsonPath("$.data.taxAmount").value(206125.00))
                .andExpect(jsonPath("$.data.totalAmount").value(4328625.00))
                .andExpect(jsonPath("$.data.outstandingAmount").value(4328625.00))
                .andReturn();

        JsonNode invoiceData = objectMapper.readTree(invoiceResult.getResponse().getContentAsString()).path("data");
        long invoiceId = invoiceData.path("id").asLong();
        BigDecimal total = new BigDecimal(invoiceData.path("totalAmount").asText());

        mockMvc.perform(post("/api/v1/invoices/{id}/issue", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ISSUED"));

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invoiceId": %d,
                                  "paymentDate": "%s",
                                  "amount": %s,
                                  "paymentMethod": "BANK_TRANSFER",
                                  "status": "COMPLETED"
                                }
                                """.formatted(invoiceId, LocalDate.now(), total.toPlainString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.direction").value("INCOMING"));

        mockMvc.perform(get("/api/v1/invoices/{id}/outstanding", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outstandingAmount").value(0.00))
                .andExpect(jsonPath("$.data.status").value("PAID"));

        mockMvc.perform(post("/api/v1/reconciliations/invoice/{invoiceId}/run", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MATCHED"))
                .andExpect(jsonPath("$.data.varianceAmount").value(0.00))
                .andExpect(jsonPath("$.data.resolved").value(true));

        // The settlement activity must be visible in the audit trail.
        mockMvc.perform(get("/api/v1/audit-logs/{entityType}/{entityId}", "INVOICE", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("a reversed payment restores the outstanding balance")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void reversingAPaymentRestoresOutstandingBalance() throws Exception {
        long tradeId = createTrade(uniqueReference());

        mockMvc.perform(put("/api/v1/trades/{id}", tradeId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"));
        mockMvc.perform(put("/api/v1/trades/{id}", tradeId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"));

        MvcResult invoiceResult = mockMvc.perform(post("/api/v1/invoices/from-trade/{tradeId}", tradeId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        long invoiceId = objectMapper.readTree(invoiceResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/invoices/{id}/issue", invoiceId)).andExpect(status().isOk());

        MvcResult paymentResult = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invoiceId": %d,
                                  "paymentDate": "%s",
                                  "amount": 1000000.00,
                                  "paymentMethod": "BANK_TRANSFER",
                                  "status": "COMPLETED"
                                }
                                """.formatted(invoiceId, LocalDate.now())))
                .andExpect(status().isCreated())
                .andReturn();
        long paymentId = objectMapper.readTree(paymentResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/invoices/{id}/outstanding", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outstandingAmount").value(3328625.00))
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_PAID"));

        mockMvc.perform(post("/api/v1/payments/{id}/reverse", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Returned by the bank\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVERSED"));

        mockMvc.perform(get("/api/v1/invoices/{id}/outstanding", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outstandingAmount").value(4328625.00))
                .andExpect(jsonPath("$.data.status").value("ISSUED"));
    }

    @Test
    @DisplayName("overpaying an invoice returns 400")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void overpaymentIsRejected() throws Exception {
        long tradeId = createTrade(uniqueReference());
        mockMvc.perform(put("/api/v1/trades/{id}", tradeId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"));
        mockMvc.perform(put("/api/v1/trades/{id}", tradeId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"));

        MvcResult invoiceResult = mockMvc.perform(post("/api/v1/invoices/from-trade/{tradeId}", tradeId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        long invoiceId = objectMapper.readTree(invoiceResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/invoices/{id}/issue", invoiceId)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invoiceId": %d,
                                  "paymentDate": "%s",
                                  "amount": 99999999.00,
                                  "paymentMethod": "BANK_TRANSFER",
                                  "status": "COMPLETED"
                                }
                                """.formatted(invoiceId, LocalDate.now())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("OVERPAYMENT_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("a payment in the wrong currency is rejected")
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void currencyMismatchIsRejected() throws Exception {
        long tradeId = createTrade(uniqueReference());
        mockMvc.perform(put("/api/v1/trades/{id}", tradeId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"));
        mockMvc.perform(put("/api/v1/trades/{id}", tradeId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"));

        MvcResult invoiceResult = mockMvc.perform(post("/api/v1/invoices/from-trade/{tradeId}", tradeId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        long invoiceId = objectMapper.readTree(invoiceResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/invoices/{id}/issue", invoiceId)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invoiceId": %d,
                                  "paymentDate": "%s",
                                  "amount": 1000.00,
                                  "currencyCode": "EUR",
                                  "paymentMethod": "BANK_TRANSFER"
                                }
                                """.formatted(invoiceId, LocalDate.now())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENCY_MISMATCH"));
    }

    @Test
    @DisplayName("the Swagger / OpenAPI document is served without authentication")
    void openApiDocumentIsPublic() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("Trade Settlement");
    }
}
