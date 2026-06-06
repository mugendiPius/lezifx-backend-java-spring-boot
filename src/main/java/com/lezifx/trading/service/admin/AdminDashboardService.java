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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

        // ── House balance ───────────────────────────────────────────────
        BigDecimal houseBalance = tenant.getHouseBalance() != null
                ? tenant.getHouseBalance() : BigDecimal.ZERO;
        BigDecimal floorBalance = tenant.getFloorBalance() != null
                ? tenant.getFloorBalance() : BigDecimal.ZERO;
        BigDecimal houseRatio = floorBalance.compareTo(BigDecimal.ZERO) > 0
                ? houseBalance.divide(floorBalance, 4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(999);

        // ── Time boundaries ─────────────────────────────────────────────
        Instant startOfDay  = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant startOfWeek = Instant.now().minus(7, ChronoUnit.DAYS);

        // ── User stats ──────────────────────────────────────────────────
        long totalUsers       = userRepository.countByTenantId(tenantId);
        long newSignupsToday  = userRepository.countByTenantIdAndCreatedAtAfter(tenantId, startOfDay);
        long newSignupsWeek   = userRepository.countByTenantIdAndCreatedAtAfter(tenantId, startOfWeek);

        // ── Trade stats ─────────────────────────────────────────────────
        long activeStakersNow = tradeSessionRepository
                .countByTenantIdAndStatusAndIsMarketerTradeFalse(tenantId, TradeSessionStatus.ACTIVE);

        // ── Financial stats ─────────────────────────────────────────────
        BigDecimal totalDeposited = walletTransactionRepository.sumDepositsForTenant(tenantId);
        if (totalDeposited == null) totalDeposited = BigDecimal.ZERO;

        BigDecimal totalWithdrawn = walletTransactionRepository.sumWithdrawalsForTenant(tenantId);
        if (totalWithdrawn == null) totalWithdrawn = BigDecimal.ZERO;
        totalWithdrawn = totalWithdrawn.abs();

        BigDecimal grossProfit = tradeSessionRepository.calculateGrossProfitForTenant(tenantId);
        if (grossProfit == null) grossProfit = BigDecimal.ZERO;

        long pendingWithdrawals = withdrawalRequestRepository
                .countByTenantIdAndStatus(tenantId, WithdrawalStatus.PENDING);

        return AdminDashboardResponse.builder()
                // Frontend-expected fields
                .totalUsers(totalUsers)
                .newSignupsToday(newSignupsToday)
                .newSignupsWeek(newSignupsWeek)
                .activeStakersNow(activeStakersNow)
                .activeStakersToday(0L)
                .activeStakersWeek(0L)
                .totalDepositsToday(BigDecimal.ZERO)
                .totalWithdrawsToday(BigDecimal.ZERO)
                .totalStakesLive(BigDecimal.ZERO)
                .totalVolume24h(totalDeposited)
                .houseBalance(houseBalance)
                .floorBalance(floorBalance)
                .platformMode(tenant.getPlatformMode().name())
                .killSwitchActive(Boolean.TRUE.equals(tenant.getKillSwitchActive()))
                .winModeRejected(false)
                // Internal fields
                .houseRatio(houseRatio)
                .activeTrades(activeStakersNow)
                .totalDeposited(totalDeposited)
                .totalWithdrawn(totalWithdrawn)
                .pendingWithdrawals(pendingWithdrawals)
                .grossProfit(grossProfit)
                .build();
    }
}