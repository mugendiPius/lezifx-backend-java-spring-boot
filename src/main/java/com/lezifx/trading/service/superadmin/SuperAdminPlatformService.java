package com.lezifx.trading.service.superadmin;

import com.lezifx.trading.domain.enums.StatementStatus;
import com.lezifx.trading.domain.enums.TenantStatus;

import com.lezifx.trading.repository.CommissionStatementRepository;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.TradeSessionRepository;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.web.dto.response.SuperAdminStatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminPlatformService {

    static final UUID MASTER_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TradeSessionRepository tradeSessionRepository;
    private final CommissionStatementRepository commissionStatementRepository;

    @Transactional(readOnly = true)
    public SuperAdminStatsResponse getPlatformStats() {
        long totalTenants = tenantRepository.countByIdNot(MASTER_TENANT_ID);
        long activeTenants = tenantRepository.countByStatusAndIdNot(TenantStatus.ACTIVE, MASTER_TENANT_ID);
        long totalUsers = userRepository.countByTenantIdNot(MASTER_TENANT_ID);
        long totalRealTrades = tradeSessionRepository
                .countByStatusAndIsDemoFalseAndIsMarketerTradeFalse(com.lezifx.trading.domain.enums.TradeSessionStatus.COMPLETED);

        BigDecimal totalGrossProfit = tradeSessionRepository
                .calculateTotalGrossProfitExcludingTenant(MASTER_TENANT_ID);
        if (totalGrossProfit == null) totalGrossProfit = BigDecimal.ZERO;

        long pendingCommissions = commissionStatementRepository.countByStatus(StatementStatus.PENDING);

        BigDecimal totalCommissionEarned = commissionStatementRepository
                .sumCommissionAmountByStatus(StatementStatus.PAID);
        if (totalCommissionEarned == null) totalCommissionEarned = BigDecimal.ZERO;

        return SuperAdminStatsResponse.builder()
                .totalTenants(totalTenants)
                .activeTenants(activeTenants)
                .totalUsers(totalUsers)
                .totalRealTrades(totalRealTrades)
                .totalGrossProfit(totalGrossProfit)
                .pendingCommissions(pendingCommissions)
                .totalCommissionEarned(totalCommissionEarned)
                .build();
    }
}