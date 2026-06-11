package com.lezifx.trading.service.wallet;

import com.lezifx.trading.domain.enums.DepositStatus;
import com.lezifx.trading.domain.enums.WalletTransactionType;
import com.lezifx.trading.domain.mpesa.DepositRequest;
import com.lezifx.trading.domain.wallet.Wallet;
import com.lezifx.trading.domain.wallet.WalletTransaction;
import com.lezifx.trading.repository.DepositRequestRepository;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.repository.WalletRepository;
import com.lezifx.trading.repository.WalletTransactionRepository;
import com.lezifx.trading.service.mpesa.C2BService;
import com.lezifx.trading.web.dto.event.BalanceUpdateEvent;
import com.lezifx.trading.web.dto.response.DepositResponse;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepositService {

    private final DepositRequestRepository depositRequestRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    @Lazy
    private final C2BService c2bService;

    @Transactional
    public DepositResponse initiateDeposit(UUID userId, UUID tenantId,
                                            BigDecimal amount, String phoneNumber) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

        if (Boolean.TRUE.equals(user.getIsMarketer())) {
            throw new BusinessException("MARKETER_USE_MOCK",
                "Marketer accounts must use /wallet/mock-deposit");
        }
        if (tenant.getMinDeposit() != null && amount.compareTo(tenant.getMinDeposit()) < 0) {
            throw new BusinessException("AMOUNT_TOO_LOW",
                "Minimum deposit is " + tenant.getMinDeposit());
        }
        if (tenant.getMaxDeposit() != null && amount.compareTo(tenant.getMaxDeposit()) > 0) {
            throw new BusinessException("AMOUNT_TOO_HIGH",
                "Maximum deposit is " + tenant.getMaxDeposit());
        }

        DepositRequest deposit = DepositRequest.builder()
            .tenant(tenant)
            .user(user)
            .amount(amount)
            .phoneNumber(phoneNumber)
            .status(DepositStatus.PENDING)
            .isMock(false)
            .build();
        deposit = depositRequestRepository.save(deposit);
        log.info("Deposit initiated: {} for user {}", deposit.getId(), userId);

        try {
            c2bService.initiateStk(deposit.getId());
        } catch (Exception e) {
            log.warn("STK push failed for deposit {}: {}", deposit.getId(), e.getMessage());
            // Don't fail the deposit initiation  STK is best-effort
        }

        return DepositResponse.builder()
            .depositId(deposit.getId())
            .amount(amount)
            .status(DepositStatus.PENDING.name())
            .message("Check your phone to complete the payment")
            .createdAt(deposit.getCreatedAt())
            .build();
    }

    @Transactional
    public DepositResponse initiateMockDeposit(UUID userId, UUID tenantId, BigDecimal amount) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        if (!Boolean.TRUE.equals(user.getIsMarketer())) {
            throw new BusinessException("NOT_MARKETER",
                "Only marketer accounts can use mock deposits");
        }

        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
            .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found"));

        BigDecimal balanceBefore = user.getMarketerBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);
        user.setMarketerBalance(balanceAfter);
        userRepository.save(user);

        WalletTransaction tx = WalletTransaction.builder()
            .wallet(wallet)
            .tenant(tenant)
            .type(WalletTransactionType.MOCK_DEPOSIT)
            .amount(amount)
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceAfter)
            .isDemo(false)
            .isMarketerTransaction(true)
            .description("Mock deposit for marketer")
            .build();
        walletTransactionRepository.save(tx);

        DepositRequest deposit = DepositRequest.builder()
            .tenant(tenant)
            .user(user)
            .amount(amount)
            .phoneNumber("N/A")
            .status(DepositStatus.COMPLETED)
            .isMock(true)
            .completedAt(Instant.now())
            .build();
        deposit = depositRequestRepository.save(deposit);

        pushBalanceUpdate(userId, balanceAfter);
        return DepositResponse.builder()
            .depositId(deposit.getId())
            .amount(amount)
            .status(DepositStatus.COMPLETED.name())
            .message("Mock deposit credited to marketer balance")
            .createdAt(deposit.getCreatedAt())
            .build();
    }

    @Transactional
    public void completeDeposit(UUID depositId, String mpesaReceiptNumber, Instant mpesaDate) {
        DepositRequest deposit = depositRequestRepository.findById(depositId)
            .orElseThrow(() -> new BusinessException("DEPOSIT_NOT_FOUND", "Deposit not found"));

        if (deposit.getStatus() != DepositStatus.PENDING) {
            throw new BusinessException("DEPOSIT_ALREADY_PROCESSED",
                "Deposit already processed: " + deposit.getStatus());
        }

        Wallet wallet = walletRepository.findByUserIdWithLock(deposit.getUser().getId())
            .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found"));

        BigDecimal balanceBefore = wallet.getLiveBalance();
        BigDecimal balanceAfter = balanceBefore.add(deposit.getAmount());
        wallet.setLiveBalance(balanceAfter);
        walletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
            .wallet(wallet)
            .tenant(deposit.getTenant())
            .type(WalletTransactionType.DEPOSIT)
            .amount(deposit.getAmount())
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceAfter)
            .isDemo(false)
            .isMarketerTransaction(false)
            .referenceId(deposit.getId())
            .referenceType("DEPOSIT_REQUEST")
            .description("M-Pesa deposit: " + mpesaReceiptNumber)
            .build();
        walletTransactionRepository.save(tx);

        deposit.setStatus(DepositStatus.COMPLETED);
        deposit.setMpesaReceiptNumber(mpesaReceiptNumber);
        deposit.setMpesaTransactionDate(mpesaDate);
        deposit.setCompletedAt(Instant.now());
        depositRequestRepository.save(deposit);

        pushBalanceUpdate(deposit.getUser().getId(), balanceAfter);
        log.info("Deposit completed: {} receipt={}", depositId, mpesaReceiptNumber);
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