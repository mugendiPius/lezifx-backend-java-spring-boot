package com.lezifx.trading.service.admin;

import com.lezifx.trading.domain.enums.TradeSessionStatus;
import com.lezifx.trading.domain.enums.WithdrawalStatus;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.TradeSessionRepository;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.repository.WalletTransactionRepository;
import com.lezifx.trading.repository.WithdrawalRequestRepository;
import com.lezifx.trading.web.dto.response.AdminDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final TradeSessionRepository tradeSessionRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard(UUID tenantId) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        BigDecimal houseBalance = tenant.getHouseBalance() != null
            ? tenant.getHouseBalance() : BigDecimal.ZERO;
        BigDecimal floorBalance = tenant.getFloorBalance() != null
            ? tenant.getFloorBalance() : BigDecimal.ZERO;

        BigDecimal houseRatio = floorBalance.compareTo(BigDecimal.ZERO) > 0
            ? houseBalance.divide(floorBalance, 4, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(999);

        long activeTrades = tradeSessionRepository
            .countByTenantIdAndStatusAndIsMarketerTradeFalse(tenantId, TradeSessionStatus.ACTIVE);

        long totalUsers = userRepository.countByTenantId(tenantId);

        BigDecimal totalDeposited = walletTransactionRepository.sumDepositsForTenant(tenantId);
        if (totalDeposited == null) totalDeposited = BigDecimal.ZERO;

        BigDecimal totalWithdrawn = walletTransactionRepository.sumWithdrawalsForTenant(tenantId);
        if (totalWithdrawn == null) totalWithdrawn = BigDecimal.ZERO;
        totalWithdrawn = totalWithdrawn.abs();

        long pendingWithdrawals = withdrawalRequestRepository
            .countByTenantIdAndStatus(tenantId, WithdrawalStatus.PENDING);

        BigDecimal grossProfit = tradeSessionRepository.calculateGrossProfitForTenant(tenantId);
        if (grossProfit == null) grossProfit = BigDecimal.ZERO;

        return AdminDashboardResponse.builder()
            .houseBalance(houseBalance)
            .floorBalance(floorBalance)
            .houseRatio(houseRatio)
            .platformMode(tenant.getPlatformMode().name())
            .activeTrades(activeTrades)
            .totalUsers(totalUsers)
            .totalDeposited(totalDeposited)
            .totalWithdrawn(totalWithdrawn)
            .pendingWithdrawals(pendingWithdrawals)
            .grossProfit(grossProfit)
            .build();
    }
}