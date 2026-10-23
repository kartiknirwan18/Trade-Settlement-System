package com.example.settlement.config;

import com.example.settlement.dto.request.InvoiceGenerateRequest;
import com.example.settlement.dto.request.PaymentCreateRequest;
import com.example.settlement.dto.request.TradeCreateRequest;
import com.example.settlement.dto.response.InvoiceResponse;
import com.example.settlement.dto.response.PaymentResponse;
import com.example.settlement.dto.response.TradeResponse;
import com.example.settlement.entity.Trade;
import com.example.settlement.enums.PaymentMethod;
import com.example.settlement.enums.PaymentStatus;
import com.example.settlement.enums.Role;
import com.example.settlement.enums.TradeStatus;
import com.example.settlement.enums.TradeType;
import com.example.settlement.repository.TradeRepository;
import com.example.settlement.repository.UserRepository;
import com.example.settlement.service.AlertService;
import com.example.settlement.service.InvoiceService;
import com.example.settlement.service.PaymentService;
import com.example.settlement.service.ReconciliationService;
import com.example.settlement.service.TradeService;
import com.example.settlement.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inserts realistic demonstration data for crude oil, natural gas, wheat, aluminium
 * and copper so that a freshly started instance is immediately explorable.
 *
 * <p>Everything created here is clearly labelled <strong>SAMPLE DATA</strong> and the
 * seeder is skipped when business data is already present. Disable it entirely with
 * {@code settlement.seed-sample-data=false}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "settlement", name = "seed-sample-data", havingValue = "true", matchIfMissing = true)
public class SampleDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    private final UserRepository userRepository;
    private final TradeRepository tradeRepository;
    private final UserService userService;
    private final TradeService tradeService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final ReconciliationService reconciliationService;
    private final AlertService alertService;

    public SampleDataSeeder(UserRepository userRepository,
                            TradeRepository tradeRepository,
                            UserService userService,
                            TradeService tradeService,
                            InvoiceService invoiceService,
                            PaymentService paymentService,
                            ReconciliationService reconciliationService,
                            AlertService alertService) {
        this.userRepository = userRepository;
        this.tradeRepository = tradeRepository;
        this.userService = userService;
        this.tradeService = tradeService;
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
        this.reconciliationService = reconciliationService;
        this.alertService = alertService;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedUsers();
        if (tradeRepository.count() > 0) {
            log.info("Business data already present - sample trades, invoices and payments were not re-seeded");
            return;
        }
        try {
            seedBusinessData();
            log.info("SAMPLE DATA loaded: 7 trades, 5 invoices, payments, reconciliations and overdue alerts");
        } catch (RuntimeException ex) {
            // Sample data is a convenience; a failure here must never stop the application.
            log.error("SAMPLE DATA could not be loaded", ex);
        }
    }

    private void seedUsers() {
        createUserIfAbsent("admin", "System Administrator", Role.ADMIN, "Admin@123");
        createUserIfAbsent("finance", "Finance Controller", Role.FINANCE, "Finance@123");
        createUserIfAbsent("trader", "Commodity Trader", Role.TRADER, "Trader@123");
        createUserIfAbsent("auditor", "Internal Auditor", Role.AUDITOR, "Auditor@123");
    }

    private void createUserIfAbsent(String username, String fullName, Role role, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        userService.createUser(username, username + "@settlement.local", rawPassword, fullName, role);
        log.info("SAMPLE DATA: created {} user '{}'", role, username);
    }

    private void seedBusinessData() {
        LocalDate today = LocalDate.now();

        // --- Trades ---------------------------------------------------------
        TradeResponse crudeOil = tradeService.create(new TradeCreateRequest(
                "TRD-SAMPLE-0001", "Brent Crude Oil", TradeType.BUY,
                "Northbridge Refining Co", "Sahara Crude Export Ltd",
                new BigDecimal("50000.0000"), "BARREL", new BigDecimal("82.4500"), "USD",
                today.minusDays(45), today.minusDays(32), TradeStatus.COMPLETED,
                "SAMPLE DATA - spot cargo, Incoterms FOB Ras Tanura"));

        TradeResponse naturalGas = tradeService.create(new TradeCreateRequest(
                "TRD-SAMPLE-0002", "Henry Hub Natural Gas", TradeType.SELL,
                "Meridian Power Utilities", "GulfStream Gas Partners",
                new BigDecimal("1200000.0000"), "MMBTU", new BigDecimal("3.1500"), "USD",
                today.minusDays(26), today.minusDays(18), TradeStatus.COMPLETED,
                "SAMPLE DATA - monthly nomination delivered into the Henry Hub grid"));

        TradeResponse wheat = tradeService.create(new TradeCreateRequest(
                "TRD-SAMPLE-0003", "Milling Wheat Grade 2", TradeType.SELL,
                "Andean Flour Mills SA", "Prairie Grain Trading",
                new BigDecimal("25000.0000"), "MT", new BigDecimal("268.5000"), "USD",
                today.minusDays(95), today.minusDays(80), TradeStatus.COMPLETED,
                "SAMPLE DATA - bulk vessel, CFR Santos"));

        TradeResponse copper = tradeService.create(new TradeCreateRequest(
                "TRD-SAMPLE-0004", "LME Grade A Copper Cathode", TradeType.BUY,
                "Voltaic Cable Manufacturing", "Atacama Copper Mining SA",
                new BigDecimal("500.0000"), "MT", new BigDecimal("9120.0000"), "USD",
                today.minusDays(72), today.minusDays(56), TradeStatus.COMPLETED,
                "SAMPLE DATA - LME registered brand, warehouse Antofagasta"));

        TradeResponse aluminium = tradeService.create(new TradeCreateRequest(
                "TRD-SAMPLE-0005", "Aluminium Ingots P1020", TradeType.SELL,
                "Rheintal Automotive GmbH", "Nordic Aluminium Smelter AS",
                new BigDecimal("2000.0000"), "MT", new BigDecimal("2450.0000"), "EUR",
                today.minusDays(12), today.minusDays(9), TradeStatus.COMPLETED,
                "SAMPLE DATA - delivered DAP Duisburg"));

        // Not invoice-eligible: demonstrates the status guard.
        tradeService.create(new TradeCreateRequest(
                "TRD-SAMPLE-0006", "Brent Crude Oil", TradeType.BUY,
                "Northbridge Refining Co", "Sahara Crude Export Ltd",
                new BigDecimal("30000.0000"), "BARREL", new BigDecimal("84.1000"), "USD",
                today.minusDays(2), today.plusDays(20), TradeStatus.DRAFT,
                "SAMPLE DATA - draft, not yet eligible for invoicing"));

        tradeService.create(new TradeCreateRequest(
                "TRD-SAMPLE-0007", "Copper Concentrate", TradeType.BUY,
                "Voltaic Cable Manufacturing", "Andes Minerals Pty",
                new BigDecimal("1200.0000"), "MT", new BigDecimal("2210.0000"), "USD",
                today.minusDays(5), today.plusDays(25), TradeStatus.CONFIRMED,
                "SAMPLE DATA - confirmed but not completed"));

        // --- Invoices -------------------------------------------------------
        // Overdue payable invoice with charges and a discount.
        InvoiceResponse crudeInvoice = invoiceService.generateFromTrade(crudeOil.id(), new InvoiceGenerateRequest(
                "Brent Crude Oil - 50,000 barrels", null, today.minusDays(40), today.minusDays(10),
                new BigDecimal("5.00"), new BigDecimal("12500.0000"), new BigDecimal("5000.0000"), "USD",
                "SAMPLE DATA - freight surcharge and early-settlement discount applied"));
        invoiceService.issue(crudeInvoice.id());

        // Fully settled receivable invoice -> PAID and reconciles as MATCHED.
        InvoiceResponse gasInvoice = invoiceService.generateFromTrade(naturalGas.id(), new InvoiceGenerateRequest(
                "Henry Hub Natural Gas - 1,200,000 MMBTU", null, today.minusDays(22), today.plusDays(6),
                new BigDecimal("5.00"), null, null, "USD",
                "SAMPLE DATA - standard 30 day terms"));
        invoiceService.issue(gasInvoice.id());

        // Long overdue receivable invoice with no payments at all -> UNMATCHED.
        InvoiceResponse wheatInvoice = invoiceService.generateFromTrade(wheat.id(), new InvoiceGenerateRequest(
                "Milling Wheat Grade 2 - 25,000 MT", null, today.minusDays(90), today.minusDays(60),
                new BigDecimal("5.00"), new BigDecimal("4500.0000"), null, "USD",
                "SAMPLE DATA - buyer has requested an extended payment plan"));
        invoiceService.issue(wheatInvoice.id());

        // Partially settled overdue payable invoice -> UNDERPAID.
        InvoiceResponse copperInvoice = invoiceService.generateFromTrade(copper.id(), new InvoiceGenerateRequest(
                "LME Grade A Copper Cathode - 500 MT", null, today.minusDays(68), today.minusDays(38),
                new BigDecimal("5.00"), null, null, "USD",
                "SAMPLE DATA - two shipments invoiced on one document"));
        invoiceService.issue(copperInvoice.id());

        // Issued in EUR and falling due shortly -> drives an UPCOMING_DUE alert.
        InvoiceResponse aluminiumInvoice = invoiceService.generateFromTrade(aluminium.id(), new InvoiceGenerateRequest(
                "Aluminium Ingots P1020 - 2,000 MT", null, today.minusDays(10), today.plusDays(5),
                new BigDecimal("5.00"), null, null, "EUR",
                "SAMPLE DATA - EUR denominated sale, demonstrates multi-currency totals"));
        invoiceService.issue(aluminiumInvoice.id());

        // --- Payments -------------------------------------------------------
        paymentService.create(paymentRequest(crudeInvoice.id(), today.minusDays(12),
                new BigDecimal("1500000.0000"), "BANK-TRF-88213", PaymentStatus.PENDING,
                "SAMPLE DATA - first instalment"));

        // A failed attempt is kept for the audit trail but never reduces the balance.
        PaymentResponse failed = paymentService.create(paymentRequest(crudeInvoice.id(), today.minusDays(9),
                new BigDecimal("250000.0000"), "BANK-TRF-88502", PaymentStatus.PENDING,
                "SAMPLE DATA - rejected by the sending bank"));
        paymentService.fail(failed.id(), "SAMPLE DATA - rejected by the sending bank (insufficient funds)");

        // A completed payment that is later reversed; the reversal stays auditable.
        PaymentResponse toReverse = paymentService.create(paymentRequest(crudeInvoice.id(), today.minusDays(7),
                new BigDecimal("100000.0000"), "BANK-TRF-88991", PaymentStatus.PENDING,
                "SAMPLE DATA - duplicate transfer, reversed"));
        paymentService.complete(toReverse.id());
        paymentService.reverse(toReverse.id(), "SAMPLE DATA - duplicate transfer identified by the treasury desk");

        // Full settlement of the gas invoice.
        PaymentResponse gasPayment = paymentService.create(paymentRequest(gasInvoice.id(), today.minusDays(20),
                new BigDecimal("3969000.0000"), "SWIFT-GAS-4471", PaymentStatus.PENDING,
                "SAMPLE DATA - single settlement covering the full invoice"));
        paymentService.complete(gasPayment.id());

        // Partial settlement of the copper invoice.
        PaymentResponse copperPayment = paymentService.create(paymentRequest(copperInvoice.id(), today.minusDays(40),
                new BigDecimal("4000000.0000"), "SWIFT-CU-2207", PaymentStatus.PENDING,
                "SAMPLE DATA - part settlement agreed with the supplier"));
        paymentService.complete(copperPayment.id());

        // --- Reconciliation -------------------------------------------------
        reconciliationService.runForInvoice(crudeInvoice.id());    // UNDERPAID
        reconciliationService.runForInvoice(gasInvoice.id());      // MATCHED, auto-resolved
        reconciliationService.runForInvoice(wheatInvoice.id());    // UNMATCHED, no payments yet
        reconciliationService.runForInvoice(copperInvoice.id());   // UNDERPAID
        reconciliationService.runForInvoice(aluminiumInvoice.id()); // UNMATCHED, nothing received yet

        // --- Overdue alerts -------------------------------------------------
        // Runs the same logic the scheduled monitoring job uses, so OVERDUE_INVOICE
        // and UPCOMING_DUE alerts are present immediately after start-up.
        alertService.scanAndRaiseAlerts(today);
    }

    private PaymentCreateRequest paymentRequest(Long invoiceId, LocalDate paymentDate, BigDecimal amount,
                                                String transactionReference, PaymentStatus status, String notes) {
        // The payment direction and currency are derived and validated against the
        // invoice, so the sample data exercises the same guard rails as the API.
        return new PaymentCreateRequest(invoiceId, null, null, paymentDate, amount, null,
                PaymentMethod.BANK_TRANSFER, transactionReference, status, notes);
    }
}
