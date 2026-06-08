package com.lezifx.trading.service.trading;

import com.lezifx.trading.domain.enums.TradeOutcome;
import com.lezifx.trading.domain.enums.TradeSessionStatus;
import com.lezifx.trading.domain.trading.TradeSession;
import com.lezifx.trading.repository.TradeSessionRepository;
import com.lezifx.trading.service.platform.HouseBalanceService;
import com.lezifx.trading.service.wallet.WalletService;
import com.lezifx.trading.web.dto.event.TradeResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private final TradeSessionRepository tradeSessionRepository;
    private final WalletService          walletService;
    private final HouseBalanceService    houseBalanceService;
    private final SocialFeedService      socialFeedService;
    private final PayoutRateService      payoutRateService;
    private final SimpMessagingTemplate  messagingTemplate;
    private final ActiveSessionCache     activeSessionCache;

    @Transactional
    public void settleExpiredSessions() {
        List<TradeSession> expired = tradeSessionRepository
            .findByStatusAndExpiresAtBefore(TradeSessionStatus.ACTIVE, Instant.now());

        for (TradeSession session : expired) {
            try {
                settle(session);
            } catch (Exception e) {
                log.error("Settlement failed for session {}: {}", session.getId(), e.getMessage(), e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settle(TradeSession session) {
        TradeSession fresh = tradeSessionRepository.findById(session.getId()).orElse(null);
        if (fresh == null || fresh.getStatus() != TradeSessionStatus.ACTIVE) return;

        fresh.setStatus(TradeSessionStatus.SETTLING);
        tradeSessionRepository.save(fresh);

        boolean playerWins = fresh.getSealedExitPrice().compareTo(fresh.getEntryPrice()) > 0;

        BigDecimal profitAmount;
        BigDecimal returnAmount;
        TradeOutcome outcome;

        if (playerWins) {
            profitAmount = fresh.getStakeAmount()
                .multiply(fresh.getLockedPayoutRate())
                .setScale(2, java.math.RoundingMode.HALF_UP);
            returnAmount = fresh.getStakeAmount().add(profitAmount);
            outcome = TradeOutcome.WIN;
        } else {
            profitAmount = fresh.getStakeAmount().negate();
            returnAmount = BigDecimal.ZERO;
            outcome = TradeOutcome.LOSS;
        }

        UUID userId   = fresh.getUser().getId();
        UUID tenantId = fresh.getTenant().getId();

        walletService.creditSettlement(
            userId,
            returnAmount,
            Boolean.TRUE.equals(fresh.getIsDemo()),
            Boolean.TRUE.equals(fresh.getIsMarketerTrade()),
            fresh.getId(),
            outcome
        );

        if (!Boolean.TRUE.equals(fresh.getIsMarketerTrade())
                && !Boolean.TRUE.equals(fresh.getIsDemo())) {
            BigDecimal houseChange = fresh.getStakeAmount().subtract(returnAmount);
            houseBalanceService.adjustBalance(tenantId, houseChange);
        }

        fresh.setOutcome(outcome);
        fresh.setProfitAmount(profitAmount);
        fresh.setActualExitPrice(fresh.getSealedExitPrice());
        fresh.setSettledAt(Instant.now());
        fresh.setStatus(TradeSessionStatus.COMPLETED);
        tradeSessionRepository.save(fresh);

        // Evict from cache immediately — next tick cycle will not process this session
        activeSessionCache.onSessionExpired(tenantId, fresh.getPairSymbol(), fresh.getId());

        payoutRateService.evictRatesForTenant(tenantId);

        if (outcome == TradeOutcome.WIN) {
            try {
                socialFeedService.broadcastRealWin(fresh);
            } catch (Exception e) {
                log.warn("Could not broadcast win for session {}: {}", fresh.getId(), e.getMessage());
            }
        }

        try {
            BigDecimal newBalance = walletService.getBalance(
                userId,
                Boolean.TRUE.equals(fresh.getIsDemo()),
                Boolean.TRUE.equals(fresh.getIsMarketerTrade())
            );

            TradeResultEvent event = TradeResultEvent.builder()
                .sessionId(fresh.getId())
                .outcome(outcome.name())
                .profitAmount(profitAmount)
                .actualExitPrice(fresh.getActualExitPrice())
                .newBalance(newBalance)
                .settledAt(fresh.getSettledAt())
                .build();

            messagingTemplate.convertAndSendToUser(
                userId.toString(), "/queue/trade-result", event);
        } catch (Exception e) {
            log.warn("Could not push trade result to user {}: {}", userId, e.getMessage());
        }
    }
}