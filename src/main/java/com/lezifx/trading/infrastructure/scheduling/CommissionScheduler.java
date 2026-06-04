package com.lezifx.trading.infrastructure.scheduling;

import com.lezifx.trading.service.superadmin.CommissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommissionScheduler {

    private final CommissionService commissionService;

    @Scheduled(cron = "0 0 0 */14 * *")
    public void runCommissionCalculation() {
        log.info("Running scheduled commission calculation");
        try {
            commissionService.calculateAndSaveCommissions();
            log.info("Scheduled commission calculation completed successfully");
        } catch (Exception e) {
            log.error("Commission calculation failed: {}", e.getMessage(), e);
        }
    }
}