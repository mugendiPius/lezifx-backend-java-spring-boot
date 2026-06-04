package com.lezifx.trading.infrastructure.scheduling;

import com.lezifx.trading.service.trading.PriceTickBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PriceTickScheduler {

    private final PriceTickBroadcastService priceTickBroadcastService;

    @Scheduled(fixedDelay = 1000)
    public void runPriceTick() {
        try {
            priceTickBroadcastService.broadcastTicks();
        } catch (Exception e) {
            log.debug("Price tick error: {}", e.getMessage());
        }
    }
}