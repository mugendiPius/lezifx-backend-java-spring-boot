package com.lezifx.trading.service.wallet;

import com.lezifx.trading.domain.enums.TradeOutcome;
import com.lezifx.trading.domain.enums.TradeSessionStatus;
import com.lezifx.trading.domain.enums.WalletTransactionType;
import com.lezifx.trading.domain.wallet.WalletTransaction;
import com.lezifx.trading.repository.DepositRequestRepository;
import com.lezifx.trading.repository.TradeSessionRepository;
import com.lezifx.trading.repository.WalletRepository;
import com.lezifx.trading.repository.WalletTransactionRepository;
import com.lezifx.trading.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final DepositRequestRepository depositRequestRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final TradeSessionRepository tradeSessionRepository;

    @Transactional(readOnly = true)
    public Page<WalletTransaction> getTransactions(UUID userId, UUID tenantId,
                                                    String type, Boolean isDemo,
                                                    int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        if (type != null && !type.isBlank()) {
            try {
                WalletTransactionType txType = WalletTransactionType.valueOf(type.toUpperCase());
                return walletTransactionRepository
                    .findByUserIdAndTypeOrderByCreatedAtDesc(userId, txType, pageable);
            } catch (IllegalArgumentException e) {
                // unknown type — fall through to unfiltered
            }
        }

        if (isDemo != null) {
            return walletTransactionRepository
                .findByUserIdAndIsDemoOrderByCreatedAtDesc(userId, isDemo, pageable);
        }

        return walletTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<com.lezifx.trading.domain.mpesa.DepositRequest> getDeposits(
            UUID userId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return depositRequestRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<com.lezifx.trading.domain.mpesa.WithdrawalRequest> getWithdrawals(
            UUID userId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return withdrawalRequestRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPlayerStats(UUID userId, UUID tenantId) {
        long totalTrades = tradeSessionRepository
            .countByUserIdAndTenantIdAndStatus(userId, tenantId, TradeSessionStatus.COMPLETED);

        long wins = tradeSessionRepository
            .countByUserIdAndTenantIdAndStatusAndOutcomeAndIsDemo(
                userId, tenantId, TradeSessionStatus.COMPLETED, TradeOutcome.WIN, false);

        long losses = totalTrades - wins;
        double winRate = totalTrades > 0 ? (double) wins / totalTrades : 0.0;

        BigDecimal totalStaked = walletTransactionRepository.sumStakedByUser(userId);
        if (totalStaked == null) totalStaked = BigDecimal.ZERO;
        // TRADE_STAKE stored as negative — ABS handled in query, but ensure positive
        totalStaked = totalStaked.abs();

        BigDecimal totalProfit = tradeSessionRepository.sumProfitByUser(userId, tenantId);
        if (totalProfit == null) totalProfit = BigDecimal.ZERO;

        BigDecimal totalDeposited = walletTransactionRepository.sumDepositsByUser(userId);
        if (totalDeposited == null) totalDeposited = BigDecimal.ZERO;

        BigDecimal totalWithdrawn = walletTransactionRepository.sumWithdrawalsByUser(userId);
        if (totalWithdrawn == null) totalWithdrawn = BigDecimal.ZERO;
        totalWithdrawn = totalWithdrawn.abs();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTrades", totalTrades);
        stats.put("wins", wins);
        stats.put("losses", losses);
        stats.put("winRate", BigDecimal.valueOf(winRate).setScale(4, RoundingMode.HALF_UP));
        stats.put("totalStaked", totalStaked);
        stats.put("totalProfit", totalProfit);
        stats.put("totalDeposited", totalDeposited);
        stats.put("totalWithdrawn", totalWithdrawn);
        return stats;
    }
}