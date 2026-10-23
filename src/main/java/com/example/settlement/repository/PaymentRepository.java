package com.example.settlement.repository;

import com.example.settlement.entity.Payment;
import com.example.settlement.enums.PaymentStatus;
import com.example.settlement.repository.projection.CurrencyAmountAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByPaymentReference(String paymentReference);

    boolean existsByPaymentReference(String paymentReference);

    List<Payment> findByInvoiceIdOrderByPaymentDateDescIdDesc(Long invoiceId);

    long countByInvoiceIdAndStatus(Long invoiceId, PaymentStatus status);

    /**
     * Net completed payments recorded against an invoice (reversed and failed
     * payments are excluded by the status filter).
     */
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.invoice.id = :invoiceId
              and p.status = com.example.settlement.enums.PaymentStatus.COMPLETED
            """)
    BigDecimal sumCompletedByInvoiceId(@Param("invoiceId") Long invoiceId);

    /** Completed payments for an invoice restricted to one currency, so amounts are never compared across currencies. */
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.invoice.id = :invoiceId
              and p.status = com.example.settlement.enums.PaymentStatus.COMPLETED
              and p.currencyCode = :currencyCode
            """)
    BigDecimal sumCompletedByInvoiceIdAndCurrency(@Param("invoiceId") Long invoiceId,
                                                  @Param("currencyCode") String currencyCode);

    /** Count of completed payments that are in a different currency and therefore cannot be matched. */
    @Query("""
            select count(p) from Payment p
            where p.invoice.id = :invoiceId
              and p.status = com.example.settlement.enums.PaymentStatus.COMPLETED
              and p.currencyCode <> :currencyCode
            """)
    long countCompletedInOtherCurrency(@Param("invoiceId") Long invoiceId,
                                       @Param("currencyCode") String currencyCode);

    @Query("""
            select p.currencyCode as currencyCode, coalesce(sum(p.amount), 0) as totalAmount
            from Payment p
            where p.status = com.example.settlement.enums.PaymentStatus.COMPLETED
              and (:direction is null or p.direction = :direction)
            group by p.currencyCode
            """)
    List<CurrencyAmountAggregate> sumCompletedByCurrency(@Param("direction") com.example.settlement.enums.PaymentDirection direction);

    long countByStatus(PaymentStatus status);
}
