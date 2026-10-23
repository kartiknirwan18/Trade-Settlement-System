package com.example.settlement.service;

import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.response.AlertResponse;
import com.example.settlement.entity.OverdueAlert;
import com.example.settlement.enums.AlertSeverity;
import com.example.settlement.enums.AlertType;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/** Overdue and upcoming-due invoice alerting. */
public interface AlertService {

    /**
     * Refreshes invoice overdue state and raises the missing OVERDUE_INVOICE and
     * UPCOMING_DUE alerts. Existing alerts for the same invoice and condition are
     * never duplicated. Returns only the alerts created by this run.
     */
    List<AlertResponse> scanAndRaiseAlerts(LocalDate asOf);

    PageResponse<AlertResponse> search(Boolean acknowledged,
                                       AlertSeverity severity,
                                       AlertType alertType,
                                       Long invoiceId,
                                       String invoiceNumber,
                                       String currencyCode,
                                       LocalDate triggerDateFrom,
                                       LocalDate triggerDateTo,
                                       Pageable pageable);

    /** Unacknowledged overdue alerts, most severe and oldest first. */
    List<AlertResponse> findActiveOverdue();

    AlertResponse acknowledge(Long id);

    /** Loads the entity or throws {@code ResourceNotFoundException}; for use by other services. */
    OverdueAlert requireAlert(Long id);
}
