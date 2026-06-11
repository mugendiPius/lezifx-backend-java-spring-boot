package com.lezifx.trading.infrastructure.scheduling;

import com.lezifx.trading.service.trading.PriceTickBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires every 500 ms  safe now that broadcastTicks() does zero DB I/O.
 * Previously at 1000 ms and was further delayed by DB write latency
 * (50-200 ms on Railway), causing jerky charts on the frontend.
 *
 * fixedDelay (not fixedRate) ensures ticks never stack up if a cycle
 * somehow takes longer than 500 ms (e.g. GC pause).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PriceTickScheduler {

    private final PriceTickBroadcastService priceTickBroadcastService;

    @Scheduled(fixedDelay = 500)
    public void runPriceTick() {
        try {
            priceTickBroadcastService.broadcastTicks();
        } catch (Exception e) {
            log.debug("Price tick cycle error: {}", e.getMessage());
        }
    }
}