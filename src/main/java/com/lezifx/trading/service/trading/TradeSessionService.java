package com.lezifx.trading.service.trading;

import com.lezifx.trading.domain.enums.TradeSessionStatus;
import com.lezifx.trading.domain.trading.TradeSession;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.TradingPairRepository;
import com.lezifx.trading.repository.TradeSessionRepository;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.service.platform.HouseBalanceService;
import com.lezifx.trading.service.wallet.WalletService;
import com.lezifx.trading.web.dto.request.BuyTradeRequest;
import com.lezifx.trading.web.dto.response.TradeSessionResponse;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TradeSessionService {

    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(30, 60, 120, 300);

    private final TradeSessionRepository tradeSessionRepository;
    private final TradingPairRepository  tradingPairRepository;
    private final TenantRepository       tenantRepository;
    private final UserRepository         userRepository;
    private final WalletService          walletService;
    private final PriceGeneratorService  priceGeneratorService;
    private final PayoutRateService      payoutRateService;
    private final HouseBalanceService    houseBalanceService;
    private final ActiveSessionCache     activeSessionCache;

    public TradeSessionResponse buy(BuyTradeRequest request, UUID userId, UUID tenantId) {
        if (!ALLOWED_DURATIONS.contains(request.getDurationSeconds())) {
            throw new BusinessException("INVALID_DURATION",
                "Duration must be one of: 30, 60, 120, 300 seconds");
        }

        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

        var pair = tradingPairRepository.findByTenantIdIsNullAndIsEnabledTrue()
            .stream()
            .filter(p -> p.getSymbol().equals(request.getPairSymbol()))
            .findFirst()
            .or(() -> tradingPairRepository.findByTenantIdAndIsEnabledTrue(tenantId)
                .stream()
                .filter(p -> p.getSymbol().equals(request.getPairSymbol()))
                .findFirst())
            .orElseThrow(() -> new BusinessException("PAIR_NOT_FOUND",
                "Trading pair not found or disabled: " + request.getPairSymbol()));

        if (pair.getMinStake() != null
                && request.getStakeAmount().compareTo(pair.getMinStake()) < 0) {
            throw new BusinessException("STAKE_TOO_LOW",
                "Minimum stake is " + pair.getMinStake());
        }
        if (pair.getMaxStake() != null
                && request.getStakeAmount().compareTo(pair.getMaxStake()) > 0) {
            throw new BusinessException("STAKE_TOO_HIGH",
                "Maximum stake is " + pair.getMaxStake());
        }

        int maxConcurrent = tenant.getMaxConcurrentTrades() != null
            ? tenant.getMaxConcurrentTrades() : 3;

        long userActive = tradeSessionRepository
            .countByUserIdAndTenantIdAndStatus(userId, tenantId, TradeSessionStatus.ACTIVE);
        if (userActive >= maxConcurrent) {
            throw new BusinessException("MAX_CONCURRENT_TRADES",
                "Maximum concurrent trades reached (" + maxConcurrent + ")");
        }

        long allActive = tradeSessionRepository
            .countByTenantIdAndStatusAndIsMarketerTradeFalse(tenantId, TradeSessionStatus.ACTIVE);

        boolean    isMarketer       = Boolean.TRUE.equals(user.getIsMarketer());
        BigDecimal entryPrice       = priceGeneratorService.getCurrentPrice(tenantId, request.getPairSymbol());
        BigDecimal houseRatio       = houseBalanceService.getHouseRatio(tenantId);
        BigDecimal lockedPayoutRate = payoutRateService.getPayoutRate(tenantId, request.getDurationSeconds());

        BigDecimal sealedExitPrice = priceGeneratorService.computeSealedExitPrice(
            request.getPairSymbol(),
            entryPrice,
            request.getDurationSeconds(),
            tenant.getPlatformMode(),
            (int) allActive,
            houseRatio
        );

        // FIX B3: save session BEFORE deducting wallet so session.getId() is available
        //         as the reference on the wallet transaction.
        Instant now = Instant.now();
        TradeSession session = TradeSession.builder()
            .tenant(tenant)
            .user(user)
            .pairSymbol(request.getPairSymbol())
            .isDemo(request.isDemo())
            .isMarketerTrade(isMarketer)
            .stakeAmount(request.getStakeAmount())
            .entryPrice(entryPrice)
            .sealedExitPrice(sealedExitPrice)
            .lockedPayoutRate(lockedPayoutRate)
            .durationSeconds(request.getDurationSeconds())
            .status(TradeSessionStatus.ACTIVE)
            .expiresAt(now.plusSeconds(request.getDurationSeconds()))
            .build();

        // FIX B1: saveAndFlush ensures @CreationTimestamp populates startedAt
        //         before mapToResponse is called.
        session = tradeSessionRepository.saveAndFlush(session);

        // FIX B3: deduct after save; pass session id as reference
        walletService.deductStake(userId, request.getStakeAmount(),
            request.isDemo(), isMarketer, session.getId());

        // Notify cache immediately so next tick cycle sees this session
        activeSessionCache.onSessionCreated(session);

        return mapToResponse(session);
    }

    @Transactional(readOnly = true)
    public TradeSessionResponse getActiveSession(UUID userId, UUID tenantId) {
        return tradeSessionRepository.findByUserIdAndTenantId(userId, tenantId)
            .stream()
            .filter(s -> s.getStatus() == TradeSessionStatus.ACTIVE)
            .findFirst()
            .map(this::mapToResponse)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<TradeSessionResponse> getSessionHistory(UUID userId, UUID tenantId,
                                                         int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("startedAt").descending());
        return tradeSessionRepository
            .findHistoryByUserIdAndTenantId(userId, tenantId, pageable)
            .getContent()
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    public TradeSessionResponse mapToResponse(TradeSession session) {
        var builder = TradeSessionResponse.builder()
            .id(session.getId())
            .tenantId(session.getTenant().getId())
            .userId(session.getUser().getId())
            .pairSymbol(session.getPairSymbol())
            .isDemo(Boolean.TRUE.equals(session.getIsDemo()))
            .isMarketerTrade(Boolean.TRUE.equals(session.getIsMarketerTrade()))
            .stakeAmount(session.getStakeAmount())
            .entryPrice(session.getEntryPrice())
            .lockedPayoutRate(session.getLockedPayoutRate())
            .durationSeconds(session.getDurationSeconds())
            .status(session.getStatus().name())
            .startedAt(session.getStartedAt())
            .expiresAt(session.getExpiresAt());

        if (session.getStatus() == TradeSessionStatus.COMPLETED
                || session.getStatus() == TradeSessionStatus.CANCELLED) {
            if (session.getOutcome() != null) {
                builder.outcome(session.getOutcome().name());
            }
            builder.profitAmount(session.getProfitAmount())
                   .actualExitPrice(session.getActualExitPrice())
                   .settledAt(session.getSettledAt());
        }

        return builder.build();
    }
}