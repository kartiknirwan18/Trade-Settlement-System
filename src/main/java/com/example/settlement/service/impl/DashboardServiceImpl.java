package com.example.settlement.service.impl;

import com.example.settlement.config.SettlementProperties;
import com.example.settlement.dto.response.CurrencyAmountResponse;
import com.example.settlement.dto.response.DashboardSummaryResponse;
import com.example.settlement.dto.response.InvoiceStatusSummaryResponse;
import com.example.settlement.dto.response.OverdueSummaryResponse;
import com.example.settlement.dto.response.PaymentSummaryResponse;
import com.example.settlement.dto.response.ReconciliationSummaryResponse;
import com.example.settlement.entity.Invoice;
import com.example.settlement.enums.AlertType;
import com.example.settlement.enums.InvoiceStatus;
import com.example.settlement.enums.PaymentDirection;
import com.example.settlement.enums.PaymentStatus;
import com.example.settlement.enums.ReconciliationStatus;
import com.example.settlement.enums.TradeStatus;
import com.example.settlement.repository.InvoiceRepository;
import com.example.settlement.repository.OverdueAlertRepository;
import com.example.settlement.repository.PaymentRepository;
import com.example.settlement.repository.ReconciliationRepository;
import com.example.settlement.repository.TradeRepository;
import com.example.settlement.repository.projection.CurrencyAmountAggregate;
import com.example.settlement.repository.projection.InvoiceStatusAggregate;
import com.example.settlement.service.DashboardService;
import com.example.settlement.util.InvoiceCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final TradeRepository tradeRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final OverdueAlertRepository alertRepository;
    private final SettlementProperties properties;
    private final InvoiceCalculator calculator;

    public DashboardServiceImpl(TradeRepository tradeRepository,
                                InvoiceRepository invoiceRepository,
                                PaymentRepository paymentRepository,
                                ReconciliationRepository reconciliationRepository,
                                OverdueAlertRepository alertRepository,
                                SettlementProperties properties,
                                InvoiceCalculator calculator) {
        this.tradeRepository = tradeRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.alertRepository = alertRepository;
        this.properties = properties;
        this.calculator = calculator;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        long openInvoices = invoiceRepository.countByStatus(InvoiceStatus.ISSUED)
                + invoiceRepository.countByStatus(InvoiceStatus.PARTIALLY_PAID)
                + invoiceRepository.countByStatus(InvoiceStatus.OVERDUE);

        return new DashboardSummaryResponse(
                tradeRepository.count(),
                tradeRepository.countByStatus(TradeStatus.COMPLETED),
                invoiceRepository.count(),
                openInvoices,
                invoiceRepository.countByStatus(InvoiceStatus.PAID),
                invoiceRepository.countByStatus(InvoiceStatus.OVERDUE),
                paymentRepository.count(),
                paymentRepository.countByStatus(PaymentStatus.COMPLETED),
                reconciliationRepository.findUnresolvedDiscrepancies().size(),
                alertRepository.countByAcknowledgedFalse(),
                amount(invoiceRepository.sumTotalInvoiced()),
                amount(invoiceRepository.sumTotalPaid()),
                currencies(invoiceRepository.sumTotalByCurrency()),
                currencies(invoiceRepository.sumOutstandingByCurrency()));
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceStatusSummaryResponse invoiceStatus() {
        List<InvoiceStatusSummaryResponse.StatusBucket> buckets = invoiceRepository.summariseByStatus().stream()
                .map(this::toBucket)
                .toList();
        BigDecimal totalAmount = buckets.stream()
                .map(InvoiceStatusSummaryResponse.StatusBucket::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOutstanding = currencies(invoiceRepository.sumOutstandingByCurrency()).stream()
                .map(CurrencyAmountResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new InvoiceStatusSummaryResponse(invoiceRepository.count(), amount(totalAmount), amount(totalOutstanding), buckets);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentSummaryResponse paymentSummary() {
        return new PaymentSummaryResponse(
                paymentRepository.count(),
                paymentRepository.countByStatus(PaymentStatus.COMPLETED),
                paymentRepository.countByStatus(PaymentStatus.PENDING),
                paymentRepository.countByStatus(PaymentStatus.FAILED),
                paymentRepository.countByStatus(PaymentStatus.REVERSED),
                currencies(paymentRepository.sumCompletedByCurrency(PaymentDirection.INCOMING)),
                currencies(paymentRepository.sumCompletedByCurrency(PaymentDirection.OUTGOING)));
    }

    @Override
    @Transactional(readOnly = true)
    public OverdueSummaryResponse overdueSummary() {
        List<CurrencyAmountResponse> overdueByCurrency = currencies(invoiceRepository.sumOverdueBalanceByCurrency());
        BigDecimal totalOverdue = overdueByCurrency.stream()
                .map(CurrencyAmountResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate today = LocalDate.now();
        int window = Math.max(0, properties.getOverdue().getUpcomingDueDays());
        List<OverdueSummaryResponse.UpcomingDue> upcoming = invoiceRepository
                .findDueBetween(today, today.plusDays(window)).stream()
                .map(invoice -> toUpcoming(invoice, today))
                .toList();

        return new OverdueSummaryResponse(
                invoiceRepository.countByStatus(InvoiceStatus.OVERDUE),
                amount(totalOverdue),
                overdueByCurrency,
                alertRepository.countByAcknowledgedFalse(),
                alertRepository.countByAlertType(AlertType.OVERDUE_INVOICE),
                upcoming);
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationSummaryResponse reconciliationSummary() {
        return new ReconciliationSummaryResponse(
                reconciliationRepository.count(),
                reconciliationRepository.countByStatus(ReconciliationStatus.MATCHED),
                reconciliationRepository.countByStatus(ReconciliationStatus.UNDERPAID),
                reconciliationRepository.countByStatus(ReconciliationStatus.OVERPAID),
                reconciliationRepository.countByStatus(ReconciliationStatus.UNMATCHED),
                reconciliationRepository.countByStatus(ReconciliationStatus.REVIEW_REQUIRED),
                reconciliationRepository.findUnresolvedDiscrepancies().size(),
                currencies(reconciliationRepository.sumVarianceByCurrency()));
    }

    private InvoiceStatusSummaryResponse.StatusBucket toBucket(InvoiceStatusAggregate aggregate) {
        return new InvoiceStatusSummaryResponse.StatusBucket(
                aggregate.getStatus(), aggregate.getInvoiceCount(), amount(aggregate.getTotalAmount()));
    }

    private OverdueSummaryResponse.UpcomingDue toUpcoming(Invoice invoice, LocalDate today) {
        return new OverdueSummaryResponse.UpcomingDue(
                invoice.getInvoiceNumber(),
                invoice.getTradeReference(),
                invoice.getBuyerName(),
                invoice.getCurrencyCode(),
                invoice.getDueDate(),
                ChronoUnit.DAYS.between(today, invoice.getDueDate()),
                calculator.round(invoice.outstandingAmount()));
    }

    private List<CurrencyAmountResponse> currencies(List<CurrencyAmountAggregate> aggregates) {
        return aggregates.stream()
                .map(a -> new CurrencyAmountResponse(a.getCurrencyCode(), amount(a.getTotalAmount())))
                .toList();
    }

    private BigDecimal amount(BigDecimal value) {
        return calculator.round(value == null ? BigDecimal.ZERO : value);
    }
}
