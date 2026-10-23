package com.example.settlement.repository;

import com.example.settlement.entity.Reconciliation;
import com.example.settlement.repository.projection.CurrencyAmountAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReconciliationRepository extends JpaRepository<Reconciliation, Long>, JpaSpecificationExecutor<Reconciliation> {

    List<Reconciliation> findByInvoiceIdOrderByReconciliationDateDescIdDesc(Long invoiceId);

    Optional<Reconciliation> findFirstByInvoiceIdOrderByReconciliationDateDescIdDesc(Long invoiceId);

    /** Latest reconciliation record per invoice is the authoritative one; older rows are history. */
    @Query("""
            select r from Reconciliation r
            where r.resolved = false
              and r.status <> com.example.settlement.enums.ReconciliationStatus.MATCHED
            order by r.reconciliationDate asc, r.id asc
            """)
    List<Reconciliation> findUnresolvedDiscrepancies();

    @Query("""
            select r.currencyCode as currencyCode, coalesce(sum(r.varianceAmount), 0) as totalAmount
            from Reconciliation r
            where r.status <> com.example.settlement.enums.ReconciliationStatus.MATCHED
            group by r.currencyCode
            """)
    List<CurrencyAmountAggregate> sumVarianceByCurrency();

    long countByStatus(com.example.settlement.enums.ReconciliationStatus status);
}
