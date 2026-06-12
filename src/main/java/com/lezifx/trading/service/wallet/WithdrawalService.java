package com.lezifx.trading.service.wallet;

import com.lezifx.trading.domain.enums.WalletTransactionType;
import com.lezifx.trading.domain.enums.WithdrawalStatus;
import com.lezifx.trading.domain.mpesa.WithdrawalRequest;
import com.lezifx.trading.domain.user.User;
import com.lezifx.trading.domain.wallet.Wallet;
import com.lezifx.trading.domain.wallet.WalletTransaction;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.repository.WalletRepository;
import com.lezifx.trading.repository.WalletTransactionRepository;
import com.lezifx.trading.repository.WithdrawalRequestRepository;
import com.lezifx.trading.service.mpesa.B2CService;
import com.lezifx.trading.web.dto.event.BalanceUpdateEvent;
import com.lezifx.trading.web.dto.response.WithdrawalResponse;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    @org.springframework.context.annotation.Lazy
    private final B2CService b2cService;

    @Transactional
    public WithdrawalResponse initiateWithdrawal(UUID userId, UUID tenantId,
                                                  BigDecimal amount, String phoneNumber) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        boolean isMarketer = Boolean.TRUE.equals(user.getIsMarketer());

        if (isMarketer) {
            // Marketer withdrawal path
            if (!Boolean.TRUE.equals(tenant.getMarketerWithdrawalEnabled())) {
                throw new BusinessException("MARKETER_WITHDRAWAL_DISABLED",
                    "Marketer withdrawals are not enabled for this tenant");
            }
            if (tenant.getMarketerMaxWithdrawal() != null
                    && amount.compareTo(tenant.getMarketerMaxWithdrawal()) > 0) {
                throw new BusinessException("MARKETER_WITHDRAWAL_LIMIT",
                    "Amount exceeds marketer withdrawal limit of " + tenant.getMarketerMaxWithdrawal());
            }
            BigDecimal marketerBalance = user.getMarketerBalance() != null
                ? user.getMarketerBalance() : BigDecimal.ZERO;
            if (amount.compareTo(marketerBalance) > 0) {
                throw new BusinessException("INSUFFICIENT_MARKETER_BALANCE",
                    "Insufficient marketer balance");
            }

            // Deduct from marketerBalance
            BigDecimal balanceBefore = marketerBalance;
            BigDecimal balanceAfter = balanceBefore.subtract(amount);
            user.setMarketerBalance(balanceAfter);
            userRepository.save(user);

            // Need wallet for WalletTransaction association
            Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found"));

            WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .tenant(tenant)
                .type(WalletTransactionType.WITHDRAWAL)
                .amount(amount.negate())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .isDemo(false)
                .isMarketerTransaction(true)
                .description("Marketer withdrawal request")
                .build();
            walletTransactionRepository.save(tx);

            WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .tenant(tenant)
                .user(user)
                .amount(amount)
                .phoneNumber(phoneNumber)
                .status(WithdrawalStatus.PENDING)
                .isMarketerWithdrawal(true)
                .build();
            withdrawal = withdrawalRequestRepository.save(withdrawal);

            tx.setReferenceId(withdrawal.getId());
            tx.setReferenceType("WITHDRAWAL_REQUEST");
            walletTransactionRepository.save(tx);

            pushBalanceUpdate(userId, balanceAfter);

            return WithdrawalResponse.builder()
                .withdrawalId(withdrawal.getId())
                .amount(amount)
                .status(withdrawal.getStatus().name())
                .message("Marketer withdrawal submitted and pending admin approval")
                .createdAt(withdrawal.getCreatedAt())
                .build();
        }

        // Regular player withdrawal path
        if (tenant.getMinWithdrawal() != null && amount.compareTo(tenant.getMinWithdrawal()) < 0) {
            throw new BusinessException("AMOUNT_TOO_LOW",
                "Minimum withdrawal is " + tenant.getMinWithdrawal());
        }
        if (tenant.getMaxWithdrawal() != null && amount.compareTo(tenant.getMaxWithdrawal()) > 0) {
            throw new BusinessException("AMOUNT_TOO_HIGH",
                "Maximum withdrawal is " + tenant.getMaxWithdrawal());
        }

        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
            .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found"));

        if (wallet.getLiveBalance().compareTo(amount) < 0) {
            throw new BusinessException("INSUFFICIENT_BALANCE", "Insufficient balance for withdrawal");
        }

        BigDecimal balanceBefore = wallet.getLiveBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        wallet.setLiveBalance(balanceAfter);
        walletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
            .wallet(wallet)
            .tenant(tenant)
            .type(WalletTransactionType.WITHDRAWAL)
            .amount(amount.negate())
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceAfter)
            .isDemo(false)
            .isMarketerTransaction(false)
            .description("Withdrawal request")
            .build();
        walletTransactionRepository.save(tx);

        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
            .tenant(tenant)
            .user(user)
            .amount(amount)
            .phoneNumber(phoneNumber)
            .status(WithdrawalStatus.PENDING)
            .isMarketerWithdrawal(false)
            .build();

        BigDecimal autoLimit = tenant.getAutoWithdrawalLimit();
        boolean autoApprove = autoLimit != null && amount.compareTo(autoLimit) <= 0;
        if (autoApprove) {
            withdrawal.setStatus(WithdrawalStatus.APPROVED);
            withdrawal.setApprovedAt(Instant.now());
        }

        withdrawal = withdrawalRequestRepository.save(withdrawal);

        if (autoApprove) {
            log.info("Auto-approved withdrawal {} for user {}", withdrawal.getId(), userId);
            final java.util.UUID withdrawalId = withdrawal.getId();
            try {
                b2cService.initiateB2c(withdrawalId);
            } catch (Exception e) {
                log.error("B2C dispatch failed for auto-approved withdrawal {}: {}", withdrawalId, e.getMessage());
            }
        }

        tx.setReferenceId(withdrawal.getId());
        tx.setReferenceType("WITHDRAWAL_REQUEST");
        walletTransactionRepository.save(tx);

        pushBalanceUpdate(userId, balanceAfter);

        String message = autoApprove
            ? "Withdrawal approved and queued for processing"
            : "Withdrawal submitted and pending admin approval";

        return WithdrawalResponse.builder()
            .withdrawalId(withdrawal.getId())
            .amount(amount)
            .status(withdrawal.getStatus().name())
            .message(message)
            .createdAt(withdrawal.getCreatedAt())
            .build();
    }

    @Transactional
    public void refundFailedWithdrawal(UUID withdrawalId) {
        WithdrawalRequest withdrawal = withdrawalRequestRepository.findById(withdrawalId)
            .orElseThrow(() -> new BusinessException("WITHDRAWAL_NOT_FOUND", "Withdrawal not found"));

        if (!Set.of(WithdrawalStatus.FAILED, WithdrawalStatus.REJECTED)
                .contains(withdrawal.getStatus())) {
            throw new BusinessException("NOT_REFUNDABLE",
                "Withdrawal cannot be refunded in status: " + withdrawal.getStatus());
        }

        boolean isMarketer = Boolean.TRUE.equals(withdrawal.getIsMarketerWithdrawal());
        UUID userId = withdrawal.getUser().getId();

        if (isMarketer) {
            // Refund to marketerBalance
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
            BigDecimal balanceBefore = user.getMarketerBalance() != null
                ? user.getMarketerBalance() : BigDecimal.ZERO;
            BigDecimal balanceAfter = balanceBefore.add(withdrawal.getAmount());
            user.setMarketerBalance(balanceAfter);
            userRepository.save(user);

            Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found"));

            WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .tenant(withdrawal.getTenant())
                .type(WalletTransactionType.TRADE_REFUND)
                .amount(withdrawal.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .isDemo(false)
                .isMarketerTransaction(true)
                .referenceId(withdrawal.getId())
                .referenceType("WITHDRAWAL_REQUEST")
                .description("Refund for failed marketer withdrawal")
                .build();
            walletTransactionRepository.save(tx);

            withdrawal.setStatus(WithdrawalStatus.FAILED);
            withdrawalRequestRepository.save(withdrawal);

            pushBalanceUpdate(userId, balanceAfter);
        } else {
            // Refund to liveBalance
            Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found"));

            BigDecimal balanceBefore = wallet.getLiveBalance();
            BigDecimal balanceAfter = balanceBefore.add(withdrawal.getAmount());
            wallet.setLiveBalance(balanceAfter);
            walletRepository.save(wallet);

            WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .tenant(withdrawal.getTenant())
                .type(WalletTransactionType.TRADE_REFUND)
                .amount(withdrawal.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .isDemo(false)
                .isMarketerTransaction(false)
                .referenceId(withdrawal.getId())
                .referenceType("WITHDRAWAL_REQUEST")
                .description("Refund for failed withdrawal")
                .build();
            walletTransactionRepository.save(tx);

            withdrawal.setStatus(WithdrawalStatus.FAILED);
            withdrawalRequestRepository.save(withdrawal);

            pushBalanceUpdate(userId, balanceAfter);
        }

        log.info("Withdrawal {} refunded to user {}", withdrawalId, userId);
    }

    private void pushBalanceUpdate(UUID userId, BigDecimal newBalance) {
        try {
            BalanceUpdateEvent event = BalanceUpdateEvent.builder()
                .userId(userId).newBalance(newBalance)
                .isDemo(false).timestamp(Instant.now()).build();
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/balance", event);
        } catch (Exception e) {
            log.warn("Could not push balance update to user {}: {}", userId, e.getMessage());
        }
    }
}