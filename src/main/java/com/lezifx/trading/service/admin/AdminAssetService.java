package com.lezifx.trading.service.admin;

import com.lezifx.trading.infrastructure.audit.AuditLogService;
import com.lezifx.trading.repository.TradingPairRepository;
import com.lezifx.trading.web.dto.response.TradingPairResponse;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAssetService {

    private final TradingPairRepository tradingPairRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<TradingPairResponse> listAssets(UUID tenantId) {
        // findAll() avoids needing new repo methods. Filter to global + this tenant.
        return tradingPairRepository.findAll().stream()
            .filter(p -> p.getTenantId() == null || tenantId.equals(p.getTenantId()))
            .map(p -> TradingPairResponse.builder()
                .id(p.getId())
                .symbol(p.getSymbol())
                .name(p.getName())
                .baseAsset(p.getBaseAsset())
                .quoteAsset(p.getQuoteAsset())
                .category(p.getCategory())
                .basePrice(p.getBasePrice())
                .enabled(Boolean.TRUE.equals(p.getIsEnabled()))
                .minStake(p.getMinStake())
                .maxStake(p.getMaxStake())
                .allowedDurations(p.getAllowedDurations())
                .build())
            .collect(Collectors.toList());
    }

    @Transactional
    public TradingPairResponse setAssetEnabled(UUID pairId, UUID tenantId,
                                               boolean isEnabled, String adminId) {
        var pair = tradingPairRepository.findById(pairId)
            .orElseThrow(() -> new BusinessException("PAIR_NOT_FOUND", "Trading pair not found"));

        boolean oldEnabled = Boolean.TRUE.equals(pair.getIsEnabled());
        pair.setIsEnabled(isEnabled);
        tradingPairRepository.save(pair);

        auditLogService.record(tenantId, adminId, "ADMIN", "SET_ASSET_ENABLED",
            "TradingPair", pairId,
            Map.of("enabled", oldEnabled),
            Map.of("enabled", isEnabled), null);

        return TradingPairResponse.builder()
            .id(pair.getId())
            .symbol(pair.getSymbol())
            .name(pair.getName())
            .baseAsset(pair.getBaseAsset())
            .quoteAsset(pair.getQuoteAsset())
            .category(pair.getCategory())
            .basePrice(pair.getBasePrice())
            .enabled(Boolean.TRUE.equals(pair.getIsEnabled()))
            .minStake(pair.getMinStake())
            .maxStake(pair.getMaxStake())
            .allowedDurations(pair.getAllowedDurations())
            .build();
    }
}