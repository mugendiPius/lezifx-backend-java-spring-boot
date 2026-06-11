package com.lezifx.trading.service.wallet;

import com.lezifx.trading.domain.enums.TradeOutcome;
import com.lezifx.trading.domain.enums.WalletTransactionType;
import com.lezifx.trading.domain.user.User;
import com.lezifx.trading.domain.wallet.Wallet;
import com.lezifx.trading.domain.wallet.WalletTransaction;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.repository.WalletRepository;
import com.lezifx.trading.repository.WalletTransactionRepository;
import com.lezifx.trading.web.dto.event.BalanceUpdateEvent;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void deductStake(UUID userId, BigDecimal amount, boolean isDemo,
                             boolean isMarketer, UUID sessionId) {
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
            .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found"));

        BigDecimal balanceBefore;
        BigDecimal balanceAfter;

        if (isDemo) {
            balanceBefore = wallet.getDemoBalance();
            if (balanceBefore.compareTo(amount) < 0) {
                throw new BusinessException("INSUFFICIENT_BALANCE", "Insufficient demo balance");
            }
            balanceAfter = balanceBefore.subtract(amount);
            wallet.setDemoBalance(balanceAfter);
            walletRepository.save(wallet);

        } else if (isMarketer) {
            // Load user directly  avoids N+1 through lazy wallet.getUser()
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
            balanceBefore = user.getMarketerBalance();
            if (balanceBefore.compareTo(amount) < 0) {
                throw new BusinessException("INSUFFICIENT_BALANCE", "Insufficient marketer balance");
            }
            balanceAfter = balanceBefore.subtract(amount);
            user.setMarketerBalance(balanceAfter);
            userRepository.save(user);

        } else {
            balanceBefore = wallet.getLiveBalance();
            if (balanceBefore.compareTo(amount) < 0) {
                throw new BusinessException("INSUFFICIENT_BALANCE", "Insufficient balance");
            }
            balanceAfter = balanceBefore.subtract(amount);
            wallet.setLiveBalance(balanceAfter);
            walletRepository.save(wallet);
        }

        WalletTransaction tx = WalletTransaction.builder()
            .wallet(wallet)
            .tenant(wallet.getTenant())
            .type(WalletTransactionType.TRADE_STAKE)
            .amount(amount.negate())
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceAfter)
            .isDemo(isDemo)
            .isMarketerTransaction(isMarketer)
            .referenceId(sessionId)
            .referenceType("TRADE_SESSION")
            .description("Trade stake deducted")
            .build();
        walletTransactionRepository.save(tx);

        pushBalanceUpdate(userId, balanceAfter, isDemo);
    }

    @Transactional
    public void creditSettlement(UUID userId, BigDecimal returnAmount,
                                  boolean isDemo, boolean isMarketer,
                                  UUID sessionId, TradeOutcome outcome) {
        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found"));

        BigDecimal balanceBefore;
        BigDecimal balanceAfter;

        if (isDemo) {
            balanceBefore = wallet.getDemoBalance();
            balanceAfter = returnAmount.compareTo(BigDecimal.ZERO) > 0
                ? balanceBefore.add(returnAmount) : balanceBefore;
            if (returnAmount.compareTo(BigDecimal.ZERO) > 0) {
                wallet.setDemoBalance(balanceAfter);
                walletRepository.save(wallet);
            }

        } else if (isMarketer) {
            // Load user directly  avoids N+1 through lazy wallet.getUser()
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
            balanceBefore = user.getMarketerBalance();
            balanceAfter = returnAmount.compareTo(BigDecimal.ZERO) > 0
                ? balanceBefore.add(returnAmount) : balanceBefore;
            if (returnAmount.compareTo(BigDecimal.ZERO) > 0) {
                user.setMarketerBalance(balanceAfter);
                userRepository.save(user);
            }

        } else {
            balanceBefore = wallet.getLiveBalance();
            balanceAfter = returnAmount.compareTo(BigDecimal.ZERO) > 0
                ? balanceBefore.add(returnAmount) : balanceBefore;
            if (returnAmount.compareTo(BigDecimal.ZERO) > 0) {
                wallet.setLiveBalance(balanceAfter);
                walletRepository.save(wallet);
            }
        }

        WalletTransactionType txType = outcome == TradeOutcome.WIN
            ? (isMarketer ? WalletTransactionType.MARKETER_TRADE_WIN : WalletTransactionType.TRADE_WIN)
            : (isMarketer ? WalletTransactionType.MARKETER_TRADE_LOSS : WalletTransactionType.TRADE_LOSS);

        WalletTransaction tx = WalletTransaction.builder()
            .wallet(wallet)
            .tenant(wallet.getTenant())
            .type(txType)
            .amount(returnAmount)
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceAfter)
            .isDemo(isDemo)
            .isMarketerTransaction(isMarketer)
            .referenceId(sessionId)
            .referenceType("TRADE_SESSION")
            .description(outcome == TradeOutcome.WIN ? "Trade win payout" : "Trade loss")
            .build();
        walletTransactionRepository.save(tx);

        pushBalanceUpdate(userId, balanceAfter, isDemo);
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID userId, boolean isDemo, boolean isMarketer) {
        if (isMarketer) {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
            return user.getMarketerBalance();
        }
        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found"));
        return isDemo ? wallet.getDemoBalance() : wallet.getLiveBalance();
    }

    private void pushBalanceUpdate(UUID userId, BigDecimal newBalance, boolean isDemo) {
        try {
            BalanceUpdateEvent event = BalanceUpdateEvent.builder()
                .userId(userId)
                .newBalance(newBalance)
                .isDemo(isDemo)
                .timestamp(Instant.now())
                .build();
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/balance", event);
        } catch (Exception e) {
            log.warn("Could not push balance update to user {}: {}", userId, e.getMessage());
        }
    }
}