package com.lezifx.trading.infrastructure.scheduling;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * FIX B4: Scheduler fully disabled  price ticks are no longer persisted.
 * Kept as a Spring bean so any existing @Autowired references compile cleanly.
 */
@Component
@Slf4j
public class PriceTickCleanupScheduler {

    @PostConstruct
    public void init() {
        log.info("PriceTickCleanupScheduler: disabled  price ticks are no longer persisted.");
    }
}