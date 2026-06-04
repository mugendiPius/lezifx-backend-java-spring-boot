package com.lezifx.trading.infrastructure.scheduling;

import com.lezifx.trading.domain.enums.TenantStatus;
import com.lezifx.trading.repository.SocialFeedEventRepository;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.TradingPairRepository;
import com.lezifx.trading.service.trading.SocialFeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class SocialFeedScheduler {

    @Value("${platform.social-feed.min-real-trades-threshold:5}")
    private int minRealTradesThreshold;

    private final SocialFeedService socialFeedService;
    private final SocialFeedEventRepository socialFeedEventRepository;
    private final TenantRepository tenantRepository;
    private final TradingPairRepository tradingPairRepository;

    @Scheduled(fixedDelayString = "${platform.social-feed.simulated-interval-ms:8000}")
    public void runSimulatedFeed() {
        try {
            var activeTenants = tenantRepository.findByStatus(TenantStatus.ACTIVE);
            for (var tenant : activeTenants) {
                try {
                    long realCount = socialFeedEventRepository
                        .countByTenantIdAndIsSimulatedFalseAndCreatedAtAfter(
                            tenant.getId(), Instant.now().minusSeconds(60));

                    if (realCount < minRealTradesThreshold) {
                        var pairs = tradingPairRepository.findByTenantIdIsNullAndIsEnabledTrue();
                        if (pairs.isEmpty()) continue;

                        var pair = pairs.get(ThreadLocalRandom.current().nextInt(pairs.size()));
                        socialFeedService.generateAndBroadcastSimulated(
                            tenant.getId(), pair.getSymbol());
                    }
                } catch (Exception e) {
                    log.warn("Social feed error for tenant {}: {}", tenant.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Social feed scheduler error: {}", e.getMessage());
        }
    }
}