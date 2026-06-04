package com.lezifx.trading.service.admin;

import com.lezifx.trading.domain.enums.KycStatus;
import com.lezifx.trading.domain.enums.TradeOutcome;
import com.lezifx.trading.domain.enums.TradeSessionStatus;
import com.lezifx.trading.domain.enums.UserStatus;
import com.lezifx.trading.domain.enums.WalletTransactionType;
import com.lezifx.trading.domain.wallet.WalletTransaction;
import com.lezifx.trading.infrastructure.audit.AuditLogService;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.TradeSessionRepository;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.repository.WalletRepository;
import com.lezifx.trading.repository.WalletTransactionRepository;
import com.lezifx.trading.web.dto.response.AdminUserDetailResponse;
import com.lezifx.trading.web.dto.response.AdminUserResponse;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final TradeSessionRepository tradeSessionRepository;
    private final TenantRepository tenantRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(UUID tenantId, String role, String status,
                                              String search, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return userRepository.findByTenantIdWithFilters(tenantId, role, status, search, pageable)
            .map(u -> {
                var wallet = walletRepository.findByUserId(u.getId()).orElse(null);
                return AdminUserResponse.builder()
                    .id(u.getId())
                    .tenantId(u.getTenant().getId())
                    .email(u.getEmail())
                    .fullName(u.getFullName())
                    .phoneNumber(u.getPhoneNumber())
                    .role(u.getRole().name())
                    .status(u.getStatus().name())
                    .kycStatus(u.getKycStatus() != null ? u.getKycStatus().name() : null)
                    .isMarketer(Boolean.TRUE.equals(u.getIsMarketer()))
                    .liveBalance(wallet != null ? wallet.getLiveBalance() : BigDecimal.ZERO)
                    .demoBalance(wallet != null ? wallet.getDemoBalance() : BigDecimal.ZERO)
                    .createdAt(u.getCreatedAt())
                    .lastLoginAt(u.getLastLoginAt())
                    .build();
            });
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(UUID userId, UUID tenantId) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
        if (!user.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("ACCESS_DENIED", "Resource not in your tenant");
        }

        var wallet = walletRepository.findByUserId(userId).orElse(null);

        long totalTrades = tradeSessionRepository
            .countByUserIdAndTenantIdAndStatus(userId, tenantId, TradeSessionStatus.COMPLETED);
        long wins = tradeSessionRepository
            .countByUserIdAndTenantIdAndStatusAndOutcomeAndIsDemo(
                userId, tenantId, TradeSessionStatus.COMPLETED, TradeOutcome.WIN, false);
        long losses = totalTrades - wins;
        BigDecimal winRate = totalTrades > 0
            ? BigDecimal.valueOf((double) wins / totalTrades).setScale(4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal totalStaked = tradeSessionRepository.sumStakedByUser(userId, tenantId);
        if (totalStaked == null) totalStaked = BigDecimal.ZERO;

        BigDecimal totalProfit = tradeSessionRepository.sumProfitByUser(userId, tenantId);
        if (totalProfit == null) totalProfit = BigDecimal.ZERO;

        BigDecimal totalDeposited = walletTransactionRepository.sumDepositsByUser(userId);
        if (totalDeposited == null) totalDeposited = BigDecimal.ZERO;

        BigDecimal totalWithdrawn = walletTransactionRepository.sumWithdrawalsByUser(userId);
        if (totalWithdrawn == null) totalWithdrawn = BigDecimal.ZERO;

        return AdminUserDetailResponse.builder()
            .id(user.getId())
            .tenantId(user.getTenant().getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .phoneNumber(user.getPhoneNumber())
            .role(user.getRole().name())
            .status(user.getStatus().name())
            .kycStatus(user.getKycStatus() != null ? user.getKycStatus().name() : null)
            .isMarketer(Boolean.TRUE.equals(user.getIsMarketer()))
            .liveBalance(wallet != null ? wallet.getLiveBalance() : BigDecimal.ZERO)
            .demoBalance(wallet != null ? wallet.getDemoBalance() : BigDecimal.ZERO)
            .createdAt(user.getCreatedAt())
            .lastLoginAt(user.getLastLoginAt())
            .totalTrades(totalTrades)
            .wins(wins)
            .losses(losses)
            .winRate(winRate)
            .totalStaked(totalStaked)
            .totalProfit(totalProfit)
            .totalDeposited(totalDeposited)
            .totalWithdrawn(totalWithdrawn.abs())
            .build();
    }

    @Transactional
    public void updateUserStatus(UUID userId, UUID tenantId, UserStatus newStatus, String adminId) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
        if (!user.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("ACCESS_DENIED", "Resource not in your tenant");
        }
        UserStatus oldStatus = user.getStatus();
        user.setStatus(newStatus);
        userRepository.save(user);
        auditLogService.record(tenantId, adminId, "ADMIN", "UPDATE_USER_STATUS",
            "User", userId,
            Map.of("status", oldStatus.name()),
            Map.of("status", newStatus.name()), null);
    }

    @Transactional
    public void updateKycStatus(UUID userId, UUID tenantId, KycStatus newKycStatus, String adminId) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
        if (!user.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("ACCESS_DENIED", "Resource not in your tenant");
        }
        KycStatus oldKyc = user.getKycStatus();
        user.setKycStatus(newKycStatus);
        userRepository.save(user);
        auditLogService.record(tenantId, adminId, "ADMIN", "UPDATE_KYC_STATUS",
            "User", userId,
            Map.of("kycStatus", oldKyc != null ? oldKyc.name() : "NONE"),
            Map.of("kycStatus", newKycStatus.name()), null);
    }

    @Transactional
    public void resetDemoBalance(UUID userId, UUID tenantId, String adminId) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));
        var wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found"));
        if (!wallet.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("ACCESS_DENIED", "Resource not in your tenant");
        }

        BigDecimal refillTo = tenant.getDemoBalance() != null
            ? tenant.getDemoBalance() : BigDecimal.valueOf(10000);
        BigDecimal before = wallet.getDemoBalance();
        BigDecimal refillAmount = refillTo.subtract(before);

        wallet.setDemoBalance(refillTo);
        walletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
            .wallet(wallet)
            .tenant(tenant)
            .type(WalletTransactionType.DEMO_REFILL)
            .amount(refillAmount)
            .balanceBefore(before)
            .balanceAfter(refillTo)
            .isDemo(true)
            .isMarketerTransaction(false)
            .description("Admin demo balance reset")
            .build();
        walletTransactionRepository.save(tx);

        auditLogService.record(tenantId, adminId, "ADMIN", "RESET_DEMO_BALANCE",
            "Wallet", wallet.getId(),
            Map.of("demoBalance", before),
            Map.of("demoBalance", refillTo), null);
    }
}