package com.lezifx.trading.service.admin;

import com.lezifx.trading.domain.enums.WithdrawalStatus;
import com.lezifx.trading.domain.mpesa.WithdrawalRequest;
import com.lezifx.trading.infrastructure.audit.AuditLogService;
import com.lezifx.trading.repository.WithdrawalRequestRepository;
import com.lezifx.trading.service.mpesa.B2CService;
import com.lezifx.trading.service.wallet.WithdrawalService;
import com.lezifx.trading.web.dto.response.AdminWithdrawalResponse;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminWithdrawalService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WithdrawalService withdrawalService;
    private final B2CService b2cService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<AdminWithdrawalResponse> listWithdrawals(UUID tenantId,
                                                          String statusFilter,
                                                          int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<WithdrawalRequest> results;

        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                WithdrawalStatus status = WithdrawalStatus.valueOf(statusFilter.toUpperCase());
                results = withdrawalRequestRepository.findByTenantIdAndStatus(tenantId, status, pageable);
            } catch (IllegalArgumentException e) {
                results = withdrawalRequestRepository.findByTenantId(tenantId, pageable);
            }
        } else {
            results = withdrawalRequestRepository.findByTenantId(tenantId, pageable);
        }

        return results.map(w -> AdminWithdrawalResponse.builder()
            .id(w.getId())
            .userId(w.getUser().getId())
            .userEmail(w.getUser().getEmail())
            .userFullName(w.getUser().getFullName())
            .amount(w.getAmount())
            .status(w.getStatus().name())
            .phoneNumber(w.getPhoneNumber())
            .failureReason(w.getFailureReason())
            .createdAt(w.getCreatedAt())
            .approvedAt(w.getApprovedAt())
            .completedAt(w.getCompletedAt())
            .build());
    }

    @Transactional
    public void approve(UUID withdrawalId, UUID tenantId, String adminId) {
        WithdrawalRequest withdrawal = withdrawalRequestRepository.findById(withdrawalId)
            .orElseThrow(() -> new BusinessException("WITHDRAWAL_NOT_FOUND", "Withdrawal not found"));

        if (!withdrawal.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("ACCESS_DENIED", "Resource not in your tenant");
        }
        if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
            throw new BusinessException("NOT_PENDING",
                "Withdrawal is not in PENDING status: " + withdrawal.getStatus());
        }

        withdrawal.setStatus(WithdrawalStatus.APPROVED);
        withdrawal.setApprovedAt(Instant.now());
        withdrawalRequestRepository.save(withdrawal);

        auditLogService.record(tenantId, adminId, "ADMIN", "APPROVE_WITHDRAWAL",
            "WithdrawalRequest", withdrawalId,
            Map.of("status", "PENDING"),
            Map.of("status", "APPROVED"), null);

        try {
            b2cService.initiateB2c(withdrawalId);
        } catch (Exception e) {
            log.error("B2C initiation failed for withdrawal {}: {}", withdrawalId, e.getMessage());
        }
    }

    @Transactional
    public void reject(UUID withdrawalId, UUID tenantId, String reason, String adminId) {
        WithdrawalRequest withdrawal = withdrawalRequestRepository.findById(withdrawalId)
            .orElseThrow(() -> new BusinessException("WITHDRAWAL_NOT_FOUND", "Withdrawal not found"));

        if (!withdrawal.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("ACCESS_DENIED", "Resource not in your tenant");
        }
        if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
            throw new BusinessException("NOT_PENDING",
                "Withdrawal is not in PENDING status: " + withdrawal.getStatus());
        }

        withdrawal.setStatus(WithdrawalStatus.REJECTED);
        withdrawal.setFailureReason(reason);
        withdrawalRequestRepository.save(withdrawal);

        withdrawalService.refundFailedWithdrawal(withdrawalId);

        auditLogService.record(tenantId, adminId, "ADMIN", "REJECT_WITHDRAWAL",
            "WithdrawalRequest", withdrawalId,
            Map.of("status", "PENDING"),
            Map.of("status", "REJECTED", "reason", reason), null);
    }
}