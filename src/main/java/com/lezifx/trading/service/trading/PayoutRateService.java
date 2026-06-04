package com.lezifx.trading.service.trading;

import com.lezifx.trading.domain.enums.TradeSessionStatus;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.TradeSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayoutRateService {

    private static final List<Integer> ALLOWED_DURATIONS = List.of(30, 60, 120, 300);

    private final TenantRepository tenantRepository;
    private final TradeSessionRepository tradeSessionRepository;

    @Cacheable(value = "payoutRates", key = "#tenantId + '_' + #durationSeconds")
    @Transactional(readOnly = true)
    public BigDecimal getPayoutRate(UUID tenantId, int durationSeconds) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        double basePayout = switch (durationSeconds) {
            case 30  -> 0.70;
            case 60  -> 0.78;
            case 120 -> 0.86;
            case 300 -> 0.94;
            default  -> 0.70;
        };

        long activeSessions = tradeSessionRepository
            .countByTenantIdAndStatusAndIsMarketerTradeFalse(tenantId, TradeSessionStatus.ACTIVE);
        double volumeBonus = Math.min(activeSessions / 100.0, 0.04);

        BigDecimal houseBalance = tenant.getHouseBalance() != null
            ? tenant.getHouseBalance() : BigDecimal.ZERO;
        BigDecimal floorBalance = tenant.getFloorBalance() != null
            ? tenant.getFloorBalance() : BigDecimal.valueOf(500000);

        double houseRatio = floorBalance.compareTo(BigDecimal.ZERO) == 0
            ? 999.0
            : houseBalance.doubleValue() / floorBalance.doubleValue();

        double balanceMultiplier;
        if (houseRatio >= 2.5) {
            balanceMultiplier = 1.02;
        } else if (houseRatio >= 1.2) {
            // linear interpolation between 0.95 and 1.02
            balanceMultiplier = 0.95 + (houseRatio - 1.2) / (2.5 - 1.2) * (1.02 - 0.95);
        } else {
            balanceMultiplier = 0.95;
        }

        double rawRate = basePayout * balanceMultiplier + volumeBonus;
        double finalRate = Math.max(0.60, Math.min(0.96, rawRate));

        return BigDecimal.valueOf(finalRate).setScale(4, RoundingMode.HALF_UP);
    }

    public Map<Integer, BigDecimal> getAllRatesForTenant(UUID tenantId) {
        Map<Integer, BigDecimal> rates = new LinkedHashMap<>();
        for (int duration : ALLOWED_DURATIONS) {
            rates.put(duration, getPayoutRate(tenantId, duration));
        }
        return rates;
    }

    @CacheEvict(value = "payoutRates", allEntries = true)
    public void evictRatesForTenant(UUID tenantId) {
        // cache evicted by annotation
    }
}