package com.lezifx.trading.service.admin;

import com.lezifx.trading.infrastructure.audit.AuditLogService;
import com.lezifx.trading.repository.TradingPairRepository;
import com.lezifx.trading.web.dto.response.TradingPairResponse;
import com.lezifx.trading.web.dto.response.TradingPairToggleResponse;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminAssetService {

    private final TradingPairRepository tradingPairRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<TradingPairResponse> listAssets(UUID tenantId) {
        var globalPairs = tradingPairRepository.findByTenantIdIsNullAndIsEnabledTrue();
        var tenantPairs = tradingPairRepository.findByTenantIdAndIsEnabledTrue(tenantId);

        return Stream.concat(globalPairs.stream(), tenantPairs.stream())
            .map(p -> TradingPairResponse.builder()
                .id(p.getId())
                .symbol(p.getSymbol())
                .name(p.getName())
                .baseAsset(p.getBaseAsset())
                .quoteAsset(p.getQuoteAsset())
                .category(p.getCategory())
                .basePrice(p.getBasePrice())
                .minStake(p.getMinStake())
                .maxStake(p.getMaxStake())
                .allowedDurations(p.getAllowedDurations())
                .build())
            .collect(Collectors.toList());
    }

    @Transactional
    public TradingPairToggleResponse toggleAsset(UUID pairId, UUID tenantId, String adminId) {
        var pair = tradingPairRepository.findById(pairId)
            .orElseThrow(() -> new BusinessException("PAIR_NOT_FOUND", "Trading pair not found"));

        boolean oldEnabled = Boolean.TRUE.equals(pair.getIsEnabled());
        pair.setIsEnabled(!oldEnabled);
        tradingPairRepository.save(pair);

        auditLogService.record(tenantId, adminId, "ADMIN", "TOGGLE_ASSET",
            "TradingPair", pairId,
            Map.of("enabled", oldEnabled),
            Map.of("enabled", !oldEnabled), null);

        return TradingPairToggleResponse.builder()
            .pairId(pairId)
            .enabled(Boolean.TRUE.equals(pair.getIsEnabled()))
            .build();
    }
}