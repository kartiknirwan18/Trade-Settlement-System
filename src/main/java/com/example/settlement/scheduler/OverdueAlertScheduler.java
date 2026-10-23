package com.example.settlement.scheduler;

import com.example.settlement.dto.response.AlertResponse;
import com.example.settlement.service.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Periodic overdue invoice monitoring. The interval is driven by
 * {@code settlement.overdue.cron} and the job can be switched off entirely with
 * {@code settlement.overdue.scheduling-enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "settlement.overdue", name = "scheduling-enabled",
        havingValue = "true", matchIfMissing = true)
public class OverdueAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueAlertScheduler.class);

    private final AlertService alertService;

    public OverdueAlertScheduler(AlertService alertService) {
        this.alertService = alertService;
    }

    @Scheduled(cron = "${settlement.overdue.cron:0 0 * * * *}")
    public void monitorOverdueInvoices() {
        LocalDate asOf = LocalDate.now();
        try {
            List<AlertResponse> raised = alertService.scanAndRaiseAlerts(asOf);
            log.info("Overdue monitoring job finished for {}; {} new alert(s)", asOf, raised.size());
        } catch (RuntimeException ex) {
            // A failing monitoring run must never kill the scheduler thread.
            log.error("Overdue monitoring job failed for {}", asOf, ex);
        }
    }
}
