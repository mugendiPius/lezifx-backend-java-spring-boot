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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PayoutRateService
 *
 * FIX: Removed @Cacheable — the cache key never changed so every call
 * returned the same static value, giving the frontend a flat line of rates
 * with no oscillation.
 *
 * Replacement strategy: rates are recomputed each call but pinned to a
 * 60-second window using a per-tenant seed, so:
 *  - All users on the same tenant see the same rate within a minute.
 *  - Rates shift every minute with a ±3 % jitter, matching the frontend mock.
 *  - House-balance and volume adjustments are still applied on top.
 */
@Service
@RequiredArgsConstructor
public class PayoutRateService {

    private static final List<Integer> ALLOWED_DURATIONS = List.of(30, 60, 120, 300);

    // Jitter amplitude: ±3 % around the computed rate
    private static final double JITTER_AMP = 0.03;

    private final TenantRepository       tenantRepository;
    private final TradeSessionRepository tradeSessionRepository;

    @Transactional(readOnly = true)
    public BigDecimal getPayoutRate(UUID tenantId, int durationSeconds) {
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        // ── 1. Base payout by duration ────────────────────────────────────────
        double basePayout = switch (durationSeconds) {
            case  30 -> 0.70;
            case  60 -> 0.78;
            case 120 -> 0.86;
            case 300 -> 0.94;
            default  -> 0.70;
        };

        // ── 2. Volume bonus ───────────────────────────────────────────────────
        long activeSessions = tradeSessionRepository
                .countByTenantIdAndStatusAndIsMarketerTradeFalse(tenantId, TradeSessionStatus.ACTIVE);
        double volumeBonus = Math.min(activeSessions / 100.0, 0.04);

        // ── 3. House-balance multiplier ───────────────────────────────────────
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

        // ── 4. Per-minute deterministic jitter ────────────────────────────────
        // Seed = tenantId hash XOR current minute-epoch so it changes every
        // 60 seconds but is the same for all users on the same tenant in that
        // window. This gives the "oscillating rates" the frontend expects.
        long minuteEpoch = System.currentTimeMillis() / 60_000L;
        long seed = (tenantId.getLeastSignificantBits() ^ tenantId.getMostSignificantBits())
                ^ (minuteEpoch * 2_654_435_761L)
                ^ (long) durationSeconds;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        // We can't seed ThreadLocalRandom, so use a cheap LCG to derive a
        // deterministic value from our seed, then map to [-JITTER_AMP, +JITTER_AMP].
        long lcg = (seed * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L);
        double jitter = ((double) (lcg & 0xFFFFFFFFL) / 0xFFFFFFFFL - 0.5) * 2.0 * JITTER_AMP;

        double finalRate = Math.max(0.60, Math.min(0.96, rawRate + jitter));

        return BigDecimal.valueOf(finalRate).setScale(4, RoundingMode.HALF_UP);
    }

    public Map<Integer, BigDecimal> getAllRatesForTenant(UUID tenantId) {
        Map<Integer, BigDecimal> rates = new LinkedHashMap<>();
        for (int duration : ALLOWED_DURATIONS) {
            rates.put(duration, getPayoutRate(tenantId, duration));
        }
        return rates;
    }

    /**
     * Kept for compatibility with any callers that were evicting the cache.
     * Now a no-op since we removed @Cacheable.
     */
    public void evictRatesForTenant(UUID tenantId) {
        // no-op
    }
}