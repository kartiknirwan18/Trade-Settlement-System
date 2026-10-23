package com.example.settlement.service;

import com.example.settlement.audit.AuditActions;
import com.example.settlement.audit.AuditService;
import com.example.settlement.dto.request.TradeCreateRequest;
import com.example.settlement.dto.request.TradeUpdateRequest;
import com.example.settlement.dto.response.TradeResponse;
import com.example.settlement.entity.Trade;
import com.example.settlement.enums.TradeStatus;
import com.example.settlement.enums.TradeType;
import com.example.settlement.exception.BusinessRuleException;
import com.example.settlement.exception.DuplicateResourceException;
import com.example.settlement.exception.InvalidStateTransitionException;
import com.example.settlement.exception.ResourceNotFoundException;
import com.example.settlement.repository.TradeRepository;
import com.example.settlement.service.impl.TradeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for trade creation, validation and lifecycle transitions. */
@ExtendWith(MockitoExtension.class)
class TradeServiceImplTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private AuditService auditService;

    private TradeServiceImpl tradeService;

    @BeforeEach
    void setUp() {
        tradeService = new TradeServiceImpl(tradeRepository, auditService);
    }

    private static TradeCreateRequest copperTrade() {
        return new TradeCreateRequest(
                null,
                "LME Grade A Copper Cathode",
                TradeType.BUY,
                "Glencore Trading",
                "Codelco Chile",
                new BigDecimal("250.0000"),
                "tonne",
                new BigDecimal("8450.7500"),
                "usd",
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 2, 5),
                null,
                "Sample trade");
    }

    private static Trade existingTrade(TradeStatus status) {
        return Trade.builder()
                .id(7L)
                .tradeReference("TRD-20260110-AAAAAA")
                .commodity("Brent Crude Oil")
                .tradeType(TradeType.BUY)
                .buyerName("Shell Trading Ltd")
                .sellerName("BP Energy")
                .quantity(new BigDecimal("50000.0000"))
                .unitOfMeasure("BARREL")
                .unitPrice(new BigDecimal("82.4500"))
                .currencyCode("USD")
                .tradeDate(LocalDate.of(2026, 1, 5))
                .deliveryDate(LocalDate.of(2026, 1, 20))
                .status(status)
                .build();
    }

    @Test
    @DisplayName("a new trade defaults to DRAFT and gets a generated reference")
    void createAppliesDefaultsAndGeneratesReference() {
        when(tradeRepository.existsByTradeReference(anyString())).thenReturn(false);
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> {
            Trade toSave = invocation.getArgument(0);
            toSave.setId(1L);
            return toSave;
        });

        TradeResponse response = tradeService.create(copperTrade());

        assertThat(response.status()).isEqualTo(TradeStatus.DRAFT);
        assertThat(response.tradeReference()).startsWith("TRD-");
        assertThat(response.currencyCode()).isEqualTo("USD");
        assertThat(response.unitOfMeasure()).isEqualTo("TONNE");
        assertThat(response.grossValue()).isEqualByComparingTo("2112687.5000");
        assertThat(response.invoiceEligible()).isFalse();
        verify(auditService).record(eq("TRADE"), eq(1L), eq(AuditActions.TRADE_CREATED), isNull(), any(), anyString());
    }

    @Test
    @DisplayName("a client supplied reference is normalised to upper case")
    void createNormalisesSuppliedReference() {
        when(tradeRepository.existsByTradeReference("TRD-2026-0001")).thenReturn(false);
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> {
            Trade toSave = invocation.getArgument(0);
            toSave.setId(2L);
            return toSave;
        });

        TradeCreateRequest request = new TradeCreateRequest(
                "trd-2026-0001", "Milling Wheat Grade 2", TradeType.SELL, "Buyer", "Seller",
                new BigDecimal("100"), "MT", new BigDecimal("245.50"), "eur",
                LocalDate.of(2026, 3, 1), null, null, null);

        TradeResponse response = tradeService.create(request);

        assertThat(response.tradeReference()).isEqualTo("TRD-2026-0001");
        assertThat(response.currencyCode()).isEqualTo("EUR");
        // deliveryDate defaults to tradeDate
        assertThat(response.deliveryDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    @DisplayName("the delivery date may not precede the trade date")
    void createRejectsDeliveryBeforeTrade() {
        TradeCreateRequest request = new TradeCreateRequest(
                null, "Natural Gas", TradeType.BUY, "Buyer", "Seller",
                new BigDecimal("100"), "MMBTU", new BigDecimal("3.10"), "USD",
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 1), null, null);

        assertThatThrownBy(() -> tradeService.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo("INVALID_DELIVERY_DATE");
        verify(tradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("a duplicate trade reference is rejected with a conflict")
    void createRejectsDuplicateReference() {
        when(tradeRepository.existsByTradeReference("TRD-2026-0001")).thenReturn(true);

        TradeCreateRequest request = new TradeCreateRequest(
                "TRD-2026-0001", "Copper", TradeType.BUY, "Buyer", "Seller",
                new BigDecimal("10"), "TONNE", new BigDecimal("8450.75"), "USD",
                LocalDate.of(2026, 1, 10), null, null, null);

        assertThatThrownBy(() -> tradeService.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("tradeReference");
        verify(tradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("quantity must be strictly positive")
    void createRejectsNonPositiveQuantity() {
        TradeCreateRequest request = new TradeCreateRequest(
                null, "Copper", TradeType.BUY, "Buyer", "Seller",
                new BigDecimal("0"), "TONNE", new BigDecimal("8450.75"), "USD",
                LocalDate.of(2026, 1, 10), null, null, null);

        assertThatThrownBy(() -> tradeService.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    @DisplayName("a cancelled trade can no longer be modified")
    void updateRejectsTerminalTrade() {
        when(tradeRepository.findById(7L)).thenReturn(Optional.of(existingTrade(TradeStatus.CANCELLED)));

        TradeUpdateRequest request = new TradeUpdateRequest(
                "Updated commodity", null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> tradeService.update(7L, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getErrorCode())
                .isEqualTo("TRADE_NOT_MODIFIABLE");
    }

    @Test
    @DisplayName("DRAFT -> COMPLETED is rejected as an invalid transition")
    void updateRejectsInvalidTransition() {
        when(tradeRepository.findById(7L)).thenReturn(Optional.of(existingTrade(TradeStatus.DRAFT)));

        TradeUpdateRequest request = new TradeUpdateRequest(
                null, null, null, null, null, null, null, null, null, null, TradeStatus.COMPLETED, null);

        assertThatThrownBy(() -> tradeService.update(7L, request))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("a valid transition is applied and audited as a status change")
    void updateAppliesValidTransition() {
        when(tradeRepository.findById(7L)).thenReturn(Optional.of(existingTrade(TradeStatus.CONFIRMED)));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeUpdateRequest request = new TradeUpdateRequest(
                null, null, null, null, null, null, null, null, null, null, TradeStatus.COMPLETED, null);

        TradeResponse response = tradeService.update(7L, request);

        assertThat(response.status()).isEqualTo(TradeStatus.COMPLETED);
        assertThat(response.invoiceEligible()).isTrue();
        verify(auditService).record(eq("TRADE"), eq(7L), eq(AuditActions.TRADE_STATUS_CHANGED), any(), any(), anyString());
    }

    @Test
    @DisplayName("an unknown trade id raises a not-found error")
    void requireTradeThrowsWhenMissing() {
        when(tradeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tradeService.requireTrade(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("update without a status change is audited as a plain update")
    void updateWithoutStatusChangeIsAuditedAsUpdate() {
        Trade trade = existingTrade(TradeStatus.CONFIRMED);
        when(tradeRepository.findById(7L)).thenReturn(Optional.of(trade));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeUpdateRequest request = new TradeUpdateRequest(
                "Brent Crude Oil (Nov)", null, null, null, null, null, null, null, null, null, null, "Revised");

        TradeResponse response = tradeService.update(7L, request);

        ArgumentCaptor<Trade> captor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository).save(captor.capture());
        assertThat(captor.getValue().getCommodity()).isEqualTo("Brent Crude Oil (Nov)");
        assertThat(response.status()).isEqualTo(TradeStatus.CONFIRMED);
        verify(auditService).record(eq("TRADE"), eq(7L), eq(AuditActions.TRADE_UPDATED), any(), any(), anyString());
    }
}
