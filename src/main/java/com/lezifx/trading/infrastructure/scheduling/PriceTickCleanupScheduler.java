package com.lezifx.trading.infrastructure.scheduling;

import com.lezifx.trading.repository.PriceTickRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class PriceTickCleanupScheduler {

    private static final long RETENTION_SECONDS = 7200L; // 2 hours

    private final PriceTickRepository priceTickRepository;

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        try {
            Instant cutoff = Instant.now().minusSeconds(RETENTION_SECONDS);
            int deleted = priceTickRepository.deleteByTickTimeBefore(cutoff);
            if (deleted > 0) {
                log.debug("Pruned {} stale price ticks older than 2 hours", deleted);
            }
        } catch (Exception e) {
            log.warn("Price tick cleanup error: {}", e.getMessage());
        }
    }
}