package com.lezifx.trading.web.controller;

import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.repository.TradingPairRepository;
import com.lezifx.trading.service.trading.PayoutRateService;
import com.lezifx.trading.service.trading.SocialFeedService;
import com.lezifx.trading.service.trading.TradeSessionService;
import com.lezifx.trading.web.dto.request.BuyTradeRequest;
import com.lezifx.trading.web.dto.response.SocialFeedEventDto;
import com.lezifx.trading.web.dto.response.TradeSessionResponse;
import com.lezifx.trading.web.dto.response.TradingPairResponse;
import com.lezifx.trading.web.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/trading")
@RequiredArgsConstructor
public class TradingController {

    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(30, 60, 120, 300);

    private final TradeSessionService tradeSessionService;
    private final PayoutRateService payoutRateService;
    private final SocialFeedService socialFeedService;
    private final TradingPairRepository tradingPairRepository;

    @GetMapping("/pairs")
    public ResponseEntity<List<TradingPairResponse>> getPairs() {
        UUID tenantId = TenantContext.get();

        var globalPairs = tradingPairRepository.findByTenantIdIsNullAndIsEnabledTrue();
        var tenantPairs = tradingPairRepository.findByTenantIdAndIsEnabledTrue(tenantId);

        List<TradingPairResponse> response = Stream.concat(globalPairs.stream(), tenantPairs.stream())
                .map(p -> TradingPairResponse.builder()
                        .id(p.getId())
                        .symbol(p.getSymbol())
                        .name(p.getName())
                        .baseAsset(p.getBaseAsset())
                        .quoteAsset(p.getQuoteAsset())
                        .category(p.getCategory())
                        .basePrice(p.getBasePrice())
                        .isEnabled(Boolean.TRUE.equals(p.getIsEnabled()))  // ADD THIS LINE
                        .minStake(p.getMinStake())
                        .maxStake(p.getMaxStake())
                        .allowedDurations(p.getAllowedDurations())
                        .build())
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/rates")
    public ResponseEntity<Map<Integer, BigDecimal>> getRates() {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(payoutRateService.getAllRatesForTenant(tenantId));
    }

    @PostMapping("/buy")
    public ResponseEntity<TradeSessionResponse> buy(@Valid @RequestBody BuyTradeRequest request,
                                                     @AuthenticationPrincipal String userId) {
        if (!ALLOWED_DURATIONS.contains(request.getDurationSeconds())) {
            throw new BusinessException("INVALID_DURATION",
                "Duration must be one of: 30, 60, 120, 300 seconds");
        }

        UUID tenantId = TenantContext.get();
        TradeSessionResponse response = tradeSessionService.buy(
            request, UUID.fromString(userId), tenantId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/session/active")
    public ResponseEntity<TradeSessionResponse> getActiveSession(
            @AuthenticationPrincipal String userId) {
        UUID tenantId = TenantContext.get();
        TradeSessionResponse session = tradeSessionService.getActiveSession(
            UUID.fromString(userId), tenantId);

        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(session);
    }

    @GetMapping("/session/history")
    public ResponseEntity<List<TradeSessionResponse>> getSessionHistory(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(
            tradeSessionService.getSessionHistory(UUID.fromString(userId), tenantId, page, size)
        );
    }

    @GetMapping("/feed")
    public ResponseEntity<List<SocialFeedEventDto>> getFeed() {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(socialFeedService.getRecentFeed(tenantId));
    }
}