package com.lezifx.trading.service.trading;

import com.lezifx.trading.domain.enums.TradeSessionStatus;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.TradeSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PayoutRateService
 *
 * Rates are per-user, computed from:
 *   1. Base rate by duration (30/60/120/300s)
 *   2. House-balance multiplier (tenant health)
 *   3. Volume bonus (active sessions)
 *   4. Per-user modifiers: deposit tier, losing streak, session warmup, re-engagement
 *   5. Per-minute deterministic jitter (same tenant/duration combo within 60s window)
 *
 * Final rate clamped to [0.60, 0.96] — house edge never drops below ~4%.
 */
@Service
@RequiredArgsConstructor
public class PayoutRateService {

    private static final List<Integer> ALLOWED_DURATIONS = List.of(30, 60, 120, 300);
    private static final double JITTER_AMP = 0.03;

    private final TenantRepository        tenantRepository;
    private final TradeSessionRepository  tradeSessionRepository;

    @Transactional(readOnly = true)
    public BigDecimal getPayoutRate(UUID tenantId, UUID userId,
                                    int durationSeconds,
                                    UserTradeStatsService.UserTradeStats stats) {
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        // 1. Base rate by duration
        double basePayout = switch (durationSeconds) {
            case  30 -> 0.70;
            case  60 -> 0.78;
            case 120 -> 0.86;
            case 300 -> 0.94;
            default  -> 0.70;
        };

        // 2. Volume bonus
        long activeSessions = tradeSessionRepository
                .countByTenantIdAndStatusAndIsMarketerTradeFalse(tenantId, TradeSessionStatus.ACTIVE);
        double volumeBonus = Math.min(activeSessions / 100.0, 0.04);

        // 3. House-balance multiplier
        BigDecimal houseBalance = tenant.getHouseBalance() != null
                ? tenant.getHouseBalance() : BigDecimal.ZERO;
        BigDecimal floorBalance = tenant.getFloorBalance() != null
                ? tenant.getFloorBalance() : BigDecimal.valueOf(500_000);

        double houseRatio = floorBalance.compareTo(BigDecimal.ZERO) == 0
                ? 999.0
                : houseBalance.doubleValue() / floorBalance.doubleValue();

        double balanceMultiplier;
        if (houseRatio >= 2.5) {
            balanceMultiplier = 1.02;
        } else if (houseRatio >= 1.2) {
            balanceMultiplier = 0.95 + (houseRatio - 1.2) / (2.5 - 1.2) * (1.02 - 0.95);
        } else {
            balanceMultiplier = 0.95;
        }

        double rawRate = basePayout * balanceMultiplier + volumeBonus;

        // 4. Per-user modifiers
        double userBonus = 0.0;

        if (stats != null) {
            // Losing-streak bonus — subtle reward to keep player engaged
            long losses = stats.consecutiveLosses();
            if (losses >= 3) userBonus += 0.030;
            else if (losses == 2) userBonus += 0.015;

            // First trade of the day — welcome-back bonus
            if (stats.isFirstTradeOfSession()) userBonus += 0.020;

            // Re-engagement — user returns after 3+ days away
            if (stats.isReEngagement()) userBonus += 0.025;

            // Deposit-tier loyalty bonus
            userBonus += switch (stats.depositTier()) {
                case 3 -> 0.040;  // platinum (100k+ deposited)
                case 2 -> 0.025;  // gold (20k+)
                case 1 -> 0.010;  // silver (5k+)
                default -> 0.0;
            };
        }

        // 5. Per-minute deterministic jitter (same for all users on same tenant within a minute)
        long minuteEpoch = System.currentTimeMillis() / 60_000L;
        long seed = (tenantId.getLeastSignificantBits() ^ tenantId.getMostSignificantBits())
                ^ (minuteEpoch * 2_654_435_761L)
                ^ (long) durationSeconds;

        long lcg = (seed * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L);
        double jitter = ((double) (lcg & 0xFFFFFFFFL) / 0xFFFFFFFFL - 0.5) * 2.0 * JITTER_AMP;

        double finalRate = Math.max(0.60, Math.min(0.96, rawRate + userBonus + jitter));

        return BigDecimal.valueOf(finalRate).setScale(4, RoundingMode.HALF_UP);
    }

    /** Convenience overload for cases where user stats are not needed (e.g. rates display). */
    @Transactional(readOnly = true)
    public BigDecimal getPayoutRate(UUID tenantId, int durationSeconds) {
        return getPayoutRate(tenantId, null, durationSeconds, null);
    }

    public Map<Integer, BigDecimal> getAllRatesForTenant(UUID tenantId) {
        Map<Integer, BigDecimal> rates = new LinkedHashMap<>();
        for (int duration : ALLOWED_DURATIONS) {
            rates.put(duration, getPayoutRate(tenantId, duration));
        }
        return rates;
    }

    public void evictRatesForTenant(UUID tenantId) {
        // no-op — no cache in use
    }
}
