package com.example.settlement.repository;

import com.example.settlement.entity.Invoice;
import com.example.settlement.enums.InvoiceStatus;
import com.example.settlement.repository.projection.CurrencyAmountAggregate;
import com.example.settlement.repository.projection.InvoiceStatusAggregate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * Reads the invoice under a database row lock ({@code SELECT ... FOR UPDATE}).
     * Settlement recalculation uses this so that two concurrent payments cannot
     * compute the outstanding balance from stale data.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);

    boolean existsByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByTradeId(Long tradeId);

    boolean existsByTradeId(Long tradeId);

    long countByTradeId(Long tradeId);

    /**
     * Overdue candidates: past due date, positive outstanding balance and not cancelled.
     * A partially paid invoice is still returned while a balance remains.
     */
    @Query("""
            select i from Invoice i
            where i.status <> com.example.settlement.enums.InvoiceStatus.CANCELLED
              and i.dueDate < :asOf
              and i.totalAmount > i.amountPaid
            """)
    List<Invoice> findOverdueCandidates(@Param("asOf") LocalDate asOf);

    /** Invoices due within the window that still carry a balance. */
    @Query("""
            select i from Invoice i
            where i.status <> com.example.settlement.enums.InvoiceStatus.CANCELLED
              and i.dueDate >= :from
              and i.dueDate <= :to
              and i.totalAmount > i.amountPaid
            """)
    List<Invoice> findDueBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select i.status as status, count(i) as invoiceCount, coalesce(sum(i.totalAmount), 0) as totalAmount
            from Invoice i
            group by i.status
            """)
    List<InvoiceStatusAggregate> summariseByStatus();

    @Query("""
            select i.currencyCode as currencyCode, coalesce(sum(i.totalAmount), 0) as totalAmount
            from Invoice i
            where i.status <> com.example.settlement.enums.InvoiceStatus.CANCELLED
            group by i.currencyCode
            """)
    List<CurrencyAmountAggregate> sumTotalByCurrency();

    @Query("""
            select i.currencyCode as currencyCode,
                   coalesce(sum(i.totalAmount - i.amountPaid), 0) as totalAmount
            from Invoice i
            where i.status <> com.example.settlement.enums.InvoiceStatus.CANCELLED
            group by i.currencyCode
            """)
    List<CurrencyAmountAggregate> sumOutstandingByCurrency();

    /** Outstanding balance of every OVERDUE invoice, grouped by currency. */
    @Query("""
            select i.currencyCode as currencyCode,
                   coalesce(sum(i.totalAmount - i.amountPaid), 0) as totalAmount
            from Invoice i
            where i.status = com.example.settlement.enums.InvoiceStatus.OVERDUE
            group by i.currencyCode
            """)
    List<CurrencyAmountAggregate> sumOverdueBalanceByCurrency();

    @Query("""
            select coalesce(sum(i.totalAmount), 0) from Invoice i
            where i.status <> com.example.settlement.enums.InvoiceStatus.CANCELLED
            """)
    BigDecimal sumTotalInvoiced();

    @Query("""
            select coalesce(sum(i.amountPaid), 0) from Invoice i
            where i.status <> com.example.settlement.enums.InvoiceStatus.CANCELLED
            """)
    BigDecimal sumTotalPaid();

    long countByStatus(InvoiceStatus status);
}
