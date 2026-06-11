package com.lezifx.trading.web.controller;

import com.lezifx.trading.domain.enums.TradeSessionStatus;
import com.lezifx.trading.domain.enums.WalletTransactionType;
import com.lezifx.trading.domain.wallet.WalletTransaction;
import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.TradeSessionRepository;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.repository.WalletRepository;
import com.lezifx.trading.repository.WalletTransactionRepository;
import com.lezifx.trading.service.wallet.DepositService;
import com.lezifx.trading.service.wallet.TransactionService;
import com.lezifx.trading.service.wallet.WithdrawalService;
import com.lezifx.trading.web.dto.request.DepositInitiateRequest;
import com.lezifx.trading.web.dto.request.MockDepositRequest;
import com.lezifx.trading.web.dto.request.WithdrawRequest;
import com.lezifx.trading.web.dto.response.DepositResponse;
import com.lezifx.trading.web.dto.response.WalletBalanceResponse;
import com.lezifx.trading.web.dto.response.WalletTransactionResponse;
import com.lezifx.trading.web.dto.response.WithdrawalResponse;
import com.lezifx.trading.web.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final DepositService depositService;
    private final WithdrawalService withdrawalService;
    private final TransactionService transactionService;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final TradeSessionRepository tradeSessionRepository;

    @GetMapping("/balance")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<WalletBalanceResponse> getBalance(
            @AuthenticationPrincipal String userId) {
        UUID userUuid = UUID.fromString(userId);
        UUID tenantId = TenantContext.get();

        var user = userRepository.findById(userUuid)
            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
        var wallet = walletRepository.findByUserId(userUuid)
            .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found"));

        // Demo refill: if demo < 50 and no active trade
        if (wallet.getDemoBalance().compareTo(BigDecimal.valueOf(50)) < 0) {
            long activeTrades = tradeSessionRepository
                .countByUserIdAndTenantIdAndStatus(userUuid, tenantId, TradeSessionStatus.ACTIVE);

            if (activeTrades == 0) {
                var tenant = tenantRepository.findById(tenantId).orElse(null);
                if (tenant != null) {
                    BigDecimal refillTo = tenant.getDemoBalance() != null
                        ? tenant.getDemoBalance() : BigDecimal.valueOf(10000);
                    BigDecimal refillAmount = refillTo.subtract(wallet.getDemoBalance());
                    BigDecimal before = wallet.getDemoBalance();
                    wallet.setDemoBalance(refillTo);
                    walletRepository.save(wallet);

                    WalletTransaction refillTx = WalletTransaction.builder()
                        .wallet(wallet)
                        .tenant(tenant)
                        .type(WalletTransactionType.DEMO_REFILL)
                        .amount(refillAmount)
                        .balanceBefore(before)
                        .balanceAfter(refillTo)
                        .isDemo(true)
                        .isMarketerTransaction(false)
                        .description("Demo balance auto-refill")
                        .build();
                    walletTransactionRepository.save(refillTx);
                }
            }
        }

        BigDecimal marketerBalance = Boolean.TRUE.equals(user.getIsMarketer())
            ? user.getMarketerBalance() : null;

        return ResponseEntity.ok(WalletBalanceResponse.builder()
            .liveBalance(wallet.getLiveBalance())
            .demoBalance(wallet.getDemoBalance())
            .marketerBalance(marketerBalance)
            .tenantId(tenantId)
            .build());
    }

    @PostMapping("/deposit")
    public ResponseEntity<DepositResponse> deposit(
            @Valid @RequestBody DepositInitiateRequest request,
            @AuthenticationPrincipal String userId) {
        UUID userUuid = UUID.fromString(userId);
        UUID tenantId = TenantContext.get();
        return ResponseEntity.status(HttpStatus.CREATED).body(
            depositService.initiateDeposit(userUuid, tenantId,
                request.getAmount(), request.getPhoneNumber()));
    }

    @PostMapping("/mock-deposit")
    public ResponseEntity<DepositResponse> mockDeposit(
            @Valid @RequestBody MockDepositRequest request,
            @AuthenticationPrincipal String userId) {
        UUID userUuid = UUID.fromString(userId);
        UUID tenantId = TenantContext.get();

        var user = userRepository.findById(userUuid)
            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
        if (!Boolean.TRUE.equals(user.getIsMarketer())) {
            throw new BusinessException("NOT_MARKETER",
                "Only marketer accounts can use mock deposits");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(
            depositService.initiateMockDeposit(userUuid, tenantId, request.getAmount()));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<WithdrawalResponse> withdraw(
            @Valid @RequestBody WithdrawRequest request,
            @AuthenticationPrincipal String userId) {
        UUID userUuid = UUID.fromString(userId);
        UUID tenantId = TenantContext.get();
        return ResponseEntity.status(HttpStatus.CREATED).body(
            withdrawalService.initiateWithdrawal(userUuid, tenantId,
                request.getAmount(), request.getPhoneNumber()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<WalletTransactionResponse>> getTransactions(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean isDemo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userUuid = UUID.fromString(userId);
        UUID tenantId = TenantContext.get();

        Page<WalletTransaction> txPage = transactionService
            .getTransactions(userUuid, tenantId, type, isDemo, page, size);

        return ResponseEntity.ok(txPage.map(tx -> WalletTransactionResponse.builder()
            .id(tx.getId())
            .type(tx.getType().name())
            .amount(tx.getAmount())
            .balanceBefore(tx.getBalanceBefore())
            .balanceAfter(tx.getBalanceAfter())
            .isDemo(Boolean.TRUE.equals(tx.getIsDemo()))
            .isMarketerTransaction(Boolean.TRUE.equals(tx.getIsMarketerTransaction()))
            .referenceId(tx.getReferenceId())
            .referenceType(tx.getReferenceType())
            .description(tx.getDescription())
            .createdAt(tx.getCreatedAt())
            .build()));
    }

    @GetMapping("/deposits")
    public ResponseEntity<?> getDeposits(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
            transactionService.getDeposits(UUID.fromString(userId), page, size));
    }

    @GetMapping("/withdrawals")
    public ResponseEntity<?> getWithdrawals(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
            transactionService.getWithdrawals(UUID.fromString(userId), page, size));
    }
}