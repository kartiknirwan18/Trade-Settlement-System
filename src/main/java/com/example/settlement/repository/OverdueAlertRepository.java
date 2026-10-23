package com.example.settlement.repository;

import com.example.settlement.entity.OverdueAlert;
import com.example.settlement.enums.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface OverdueAlertRepository extends JpaRepository<OverdueAlert, Long>, JpaSpecificationExecutor<OverdueAlert> {

    boolean existsByInvoiceIdAndAlertType(Long invoiceId, AlertType alertType);

    Optional<OverdueAlert> findByInvoiceIdAndAlertType(Long invoiceId, AlertType alertType);

    List<OverdueAlert> findByInvoiceId(Long invoiceId);

    List<OverdueAlert> findByAcknowledgedFalseOrderBySeverityAscTriggerDateAsc();

    long countByAcknowledgedFalse();

    @Query("""
            select coalesce(sum(a.invoice.totalAmount - a.invoice.amountPaid), 0)
            from OverdueAlert a
            where a.alertType = com.example.settlement.enums.AlertType.OVERDUE_INVOICE
              and a.acknowledged = false
            """)
    BigDecimal sumUnacknowledgedOverdueBalance();

    long countByAlertType(AlertType alertType);
}
