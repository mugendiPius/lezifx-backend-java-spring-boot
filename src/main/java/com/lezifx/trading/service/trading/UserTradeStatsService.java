package com.lezifx.trading.service.trading;

import com.lezifx.trading.repository.TradeSessionRepository;
import com.lezifx.trading.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserTradeStatsService {

    private final TradeSessionRepository     tradeSessionRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public record UserTradeStats(
        long consecutiveLosses,
        boolean isFirstTradeOfSession,
        boolean isReEngagement,
        int depositTier              // 0=basic, 1=silver(5k+), 2=gold(20k+), 3=platinum(100k+)
    ) {}

    @Transactional(readOnly = true)
    public UserTradeStats getStats(UUID userId, UUID tenantId, boolean isDemo) {
        long consecutiveLosses = tradeSessionRepository
            .countConsecutiveLosses(userId, tenantId, isDemo, Instant.EPOCH);

        Instant todayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
        long tradesToday = tradeSessionRepository.countTradesSince(userId, tenantId, todayStart);
        boolean isFirstTradeOfSession = tradesToday == 0;

        List<Instant> lastTrades = tradeSessionRepository
            .findLastRealTradeAt(userId, tenantId, PageRequest.of(0, 1));
        boolean isReEngagement = !lastTrades.isEmpty()
            && lastTrades.get(0).isBefore(Instant.now().minus(3, ChronoUnit.DAYS));

        BigDecimal totalDeposited = walletTransactionRepository.sumDepositsByUser(userId);
        int depositTier = depositTier(totalDeposited == null ? BigDecimal.ZERO : totalDeposited);

        return new UserTradeStats(consecutiveLosses, isFirstTradeOfSession, isReEngagement, depositTier);
    }

    private int depositTier(BigDecimal total) {
        double v = total.doubleValue();
        if (v >= 100_000) return 3;
        if (v >= 20_000)  return 2;
        if (v >= 5_000)   return 1;
        return 0;
    }
}
