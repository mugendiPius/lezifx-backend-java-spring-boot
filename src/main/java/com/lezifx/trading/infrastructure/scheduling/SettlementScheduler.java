package com.lezifx.trading.infrastructure.scheduling;

import com.lezifx.trading.service.trading.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementScheduler {

    private final SettlementService settlementService;

    @Scheduled(fixedDelay = 1000)
    public void runSettlement() {
        try {
            settlementService.settleExpiredSessions();
        } catch (Exception e) {
            log.debug("Settlement cycle error: {}", e.getMessage());
        }
    }
}