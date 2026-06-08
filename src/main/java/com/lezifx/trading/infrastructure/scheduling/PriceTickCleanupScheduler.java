package com.lezifx.trading.infrastructure.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PriceTick rows are no longer written during the broadcast hot path,
 * so there is nothing to clean up.  This scheduler is kept as a no-op
 * so the class reference in Spring context does not break any existing
 * bean wiring, and can be re-enabled if tick persistence is needed later.
 */
@Component
@Slf4j
public class PriceTickCleanupScheduler {

    @Scheduled(fixedDelay = 3_600_000)   // 1 hour — effectively disabled
    public void cleanup() {
        // no-op: PriceTick DB writes removed from hot path in PriceTickBroadcastService
        log.debug("PriceTickCleanupScheduler: no-op (tick persistence disabled)");
    }
}