package com.lezifx.trading.service.superadmin;

import com.lezifx.trading.domain.commission.CommissionStatement;
import com.lezifx.trading.domain.enums.StatementStatus;
import com.lezifx.trading.domain.enums.TenantStatus;
import com.lezifx.trading.infrastructure.audit.AuditLogService;
import com.lezifx.trading.repository.CommissionPaymentRepository;
import com.lezifx.trading.repository.CommissionStatementRepository;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.TradeSessionRepository;
import com.lezifx.trading.web.dto.response.CommissionStatementResponse;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionService {

    static final BigDecimal COMMISSION_RATE = BigDecimal.valueOf(0.15);
    static final UUID MASTER_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final CommissionStatementRepository commissionStatementRepository;
    private final CommissionPaymentRepository commissionPaymentRepository;
    private final TradeSessionRepository tradeSessionRepository;
    private final TenantRepository tenantRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public void calculateAndSaveCommissions() {
        tenantRepository.findByStatus(TenantStatus.ACTIVE).forEach(tenant -> {
            if (MASTER_TENANT_ID.equals(tenant.getId())) return;
            try {
                Instant periodEnd = Instant.now();
                LocalDate periodEndDate = periodEnd.atZone(ZoneOffset.UTC).toLocalDate();
                LocalDate periodStartDate = commissionStatementRepository
                        .findTopByTenantIdOrderByPeriodEndDesc(tenant.getId())
                        .map(CommissionStatement::getPeriodEnd)
                        .orElse(tenant.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate());

                Instant periodStartInstant = periodStartDate.atStartOfDay(ZoneOffset.UTC).toInstant();

                BigDecimal grossProfit = tradeSessionRepository
                        .calculateGrossProfitForTenantBetween(tenant.getId(), periodStartInstant, periodEnd);
                if (grossProfit == null) grossProfit = BigDecimal.ZERO;
                if (grossProfit.compareTo(BigDecimal.ZERO) <= 0) return;

                BigDecimal commissionAmount = grossProfit
                        .multiply(COMMISSION_RATE)
                        .setScale(2, RoundingMode.HALF_UP);

                CommissionStatement stmt = CommissionStatement.builder()
                        .tenant(tenant)
                        .periodStart(periodStartDate)
                        .periodEnd(periodEndDate)
                        .totalTrades(0)
                        .totalStaked(BigDecimal.ZERO)
                        .totalPaidOut(BigDecimal.ZERO)
                        .grossProfit(grossProfit)
                        .platformCommission(commissionAmount)
                        .tenantNet(grossProfit.subtract(commissionAmount))
                        .status(StatementStatus.PENDING)
                        .dueAt(periodEnd.plusSeconds(30L * 24 * 60 * 60))
                        .build();
                commissionStatementRepository.save(stmt);
                log.info("Commission calculated for tenant {}: {}", tenant.getId(), commissionAmount);
            } catch (Exception e) {
                log.error("Commission calc failed for tenant {}: {}", tenant.getId(), e.getMessage(), e);
            }
        });
    }

    @Transactional
    public void markPaid(UUID statementId, String paidBy, String notes) {
        CommissionStatement statement = commissionStatementRepository.findById(statementId)
                .orElseThrow(() -> new BusinessException("STATEMENT_NOT_FOUND", "Commission statement not found"));

        if (statement.getStatus() != StatementStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS", "Only PENDING statements can be marked paid");
        }

        statement.setStatus(StatementStatus.PAID);
        commissionStatementRepository.save(statement);

        auditLogService.record(MASTER_TENANT_ID, paidBy, "SUPER_ADMIN",
                "MARK_COMMISSION_PAID", "CommissionStatement", statementId,
                Map.of("status", "PENDING"),
                Map.of("status", "PAID", "notes", notes != null ? notes : ""),
                null);
    }

    @Transactional(readOnly = true)
    public Page<CommissionStatementResponse> listStatements(UUID tenantId, String statusFilter,
                                                            int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("generatedAt").descending());
        StatementStatus status = parseStatus(statusFilter);

        Page<CommissionStatement> statements;
        if (tenantId != null && status != null) {
            statements = commissionStatementRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else if (tenantId != null) {
            statements = commissionStatementRepository.findByTenantId(tenantId, pageable);
        } else if (status != null) {
            statements = commissionStatementRepository.findByStatus(status, pageable);
        } else {
            statements = commissionStatementRepository.findAll(pageable);
        }

        return statements.map(s -> CommissionStatementResponse.builder()
                .id(s.getId())
                .tenantId(s.getTenant().getId())
                .tenantBrandName(s.getTenant().getBrandName())
                .periodStart(s.getPeriodStart().atStartOfDay(ZoneOffset.UTC).toInstant())
                .periodEnd(s.getPeriodEnd().atStartOfDay(ZoneOffset.UTC).toInstant())
                .grossProfit(s.getGrossProfit())
                .commissionRate(COMMISSION_RATE)
                .commissionAmount(s.getPlatformCommission())
                .status(s.getStatus().name())
                .createdAt(s.getGeneratedAt())
                .build());
    }

    private StatementStatus parseStatus(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) return null;
        try { return StatementStatus.valueOf(statusFilter.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}