package com.lezifx.trading.service.superadmin;

import com.lezifx.trading.domain.enums.UserStatus;
import com.lezifx.trading.domain.enums.WalletTransactionType;
import com.lezifx.trading.domain.mpesa.MpesaCallback;
import com.lezifx.trading.domain.user.User;
import com.lezifx.trading.domain.wallet.WalletTransaction;
import com.lezifx.trading.repository.MpesaCallbackRepository;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.repository.WalletTransactionRepository;
import com.lezifx.trading.service.mpesa.MpesaCallbackService;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminDevService {

    private static final BigDecimal HIGH_VALUE_THRESHOLD = BigDecimal.valueOf(10_000);

    private final MpesaCallbackRepository  mpesaCallbackRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository           userRepository;
    private final TenantRepository         tenantRepository;
    private final MpesaCallbackService     mpesaCallbackService;

    // ── Stats ─────────────────────────────────────────────────────────────────

    public Map<String, Object> getDevStats() {
        List<MpesaCallback> unprocessed = mpesaCallbackRepository.findByProcessedFalseOrderByCreatedAtAsc();

        Instant todayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
        List<WalletTransaction> todayTxns = walletTransactionRepository
                .findAll()
                .stream()
                .filter(t -> !t.getCreatedAt().isBefore(todayStart))
                .toList();

        BigDecimal depositsToday = todayTxns.stream()
                .filter(t -> t.getType() == WalletTransactionType.DEPOSIT)
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal withdrawalsToday = todayTxns.stream()
                .filter(t -> t.getType() == WalletTransactionType.WITHDRAWAL)
                .map(WalletTransaction::getAmount)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long suspicious = countSuspiciousUsers();

        return Map.of(
                "failedCallbacksCount",    unprocessed.size(),
                "unprocessedCallbacks",    unprocessed.size(),
                "highValueThresholdKes",   HIGH_VALUE_THRESHOLD.longValue(),
                "todayDepositsTotal",      depositsToday.longValue(),
                "todayWithdrawalsTotal",   withdrawalsToday.longValue(),
                "suspiciousUsersCount",    suspicious
        );
    }

    // ── Failed callbacks ──────────────────────────────────────────────────────

    public Page<MpesaCallback> getFailedCallbacks(int page, int size) {
        List<MpesaCallback> all = mpesaCallbackRepository.findByProcessedFalseOrderByCreatedAtAsc();
        return toPage(all, page, size);
    }

    @Transactional
    public void retryCallback(UUID callbackId) {
        MpesaCallback cb = mpesaCallbackRepository.findById(callbackId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Callback not found"));
        if (Boolean.TRUE.equals(cb.getProcessed())) {
            throw new BusinessException("ALREADY_PROCESSED", "Callback is already processed");
        }
        try {
            String typeName = cb.getCallbackType().name();
            if (typeName.startsWith("C2B")) {
                mpesaCallbackService.handleC2bCallback(cb.getRawPayload());
            } else if (typeName.equals("B2C_RESULT")) {
                mpesaCallbackService.handleB2cResult(cb.getRawPayload());
            } else if (typeName.equals("B2C_TIMEOUT")) {
                mpesaCallbackService.handleB2cTimeout(cb.getRawPayload());
            } else {
                throw new BusinessException("UNSUPPORTED_TYPE", "Cannot retry callback type: " + typeName);
            }
            cb.setProcessed(true);
            cb.setProcessingError(null);
            mpesaCallbackRepository.save(cb);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            cb.setProcessingError("Retry failed: " + e.getMessage());
            mpesaCallbackRepository.save(cb);
            throw new BusinessException("RETRY_FAILED", "Callback retry failed: " + e.getMessage());
        }
    }

    // ── High-value transactions ───────────────────────────────────────────────

    public Page<Map<String, Object>> getHighValueTransactions(int page, int size) {
        Instant todayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);

        List<WalletTransaction> highValue = walletTransactionRepository.findAll()
                .stream()
                .filter(t -> !t.getCreatedAt().isBefore(todayStart))
                .filter(t -> t.getType() == WalletTransactionType.DEPOSIT
                          || t.getType() == WalletTransactionType.WITHDRAWAL)
                .filter(t -> t.getAmount().abs().compareTo(HIGH_VALUE_THRESHOLD) >= 0)
                .sorted(Comparator.comparing(WalletTransaction::getAmount,
                        Comparator.comparingDouble(bd -> -bd.abs().doubleValue())))
                .toList();

        List<Map<String, Object>> mapped = highValue.stream().map(tx -> {
            User user = tx.getWallet() != null ? tx.getWallet().getUser() : null;
            String tenantName = tx.getTenant() != null ? tx.getTenant().getBrandName() : "Unknown";
            return Map.<String, Object>of(
                    "id",         tx.getId().toString(),
                    "tenantId",   tx.getTenant() != null ? tx.getTenant().getId().toString() : "",
                    "tenantName", tenantName,
                    "userId",     user != null ? user.getId().toString() : "",
                    "userName",   user != null ? user.getFullName() : "Unknown",
                    "userEmail",  user != null ? user.getEmail() : "",
                    "type",       tx.getType().name(),
                    "amount",     tx.getAmount().abs().longValue(),
                    "createdAt",  tx.getCreatedAt().toString()
            );
        }).collect(Collectors.toList());

        return toPage(mapped, page, size);
    }

    // ── Suspicious users ──────────────────────────────────────────────────────

    public Page<Map<String, Object>> getSuspiciousUsers(int page, int size) {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);

        // Heuristic: users with >5 deposits in 24h, or any single deposit >= 50k KES
        List<WalletTransaction> recentTxns = walletTransactionRepository.findAll()
                .stream()
                .filter(t -> !t.getCreatedAt().isBefore(cutoff))
                .filter(t -> t.getType() == WalletTransactionType.DEPOSIT
                          || t.getType() == WalletTransactionType.WITHDRAWAL)
                .toList();

        Map<UUID, List<WalletTransaction>> byUser = recentTxns.stream()
                .filter(t -> t.getWallet() != null)
                .collect(Collectors.groupingBy(t -> t.getWallet().getUser().getId()));

        List<Map<String, Object>> flagged = byUser.entrySet().stream()
                .flatMap(entry -> {
                    List<WalletTransaction> userTxns = entry.getValue();
                    User user = userTxns.get(0).getWallet().getUser();
                    String tenantName = userTxns.get(0).getTenant() != null
                            ? userTxns.get(0).getTenant().getBrandName() : "Unknown";
                    UUID tenantId = userTxns.get(0).getTenant() != null
                            ? userTxns.get(0).getTenant().getId() : null;

                    // Check rapid deposits
                    long depositCount = userTxns.stream()
                            .filter(t -> t.getType() == WalletTransactionType.DEPOSIT).count();
                    BigDecimal maxSingle = userTxns.stream()
                            .map(WalletTransaction::getAmount).map(BigDecimal::abs)
                            .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

                    String reason = null;
                    int riskScore = 0;

                    if (depositCount >= 5) {
                        reason = depositCount + " deposits in last 24 hours";
                        riskScore = Math.min(90, 40 + (int)(depositCount * 10));
                    }
                    if (maxSingle.compareTo(BigDecimal.valueOf(50_000)) >= 0) {
                        reason = (reason != null ? reason + "; " : "") + "single transaction >= KES 50,000";
                        riskScore = Math.max(riskScore, 75);
                    }

                    if (reason == null) return java.util.stream.Stream.empty();

                    final String finalReason = reason;
                    final int finalScore = riskScore;
                    return java.util.stream.Stream.of(Map.<String, Object>of(
                            "userId",     user.getId().toString(),
                            "userName",   user.getFullName(),
                            "userEmail",  user.getEmail(),
                            "tenantId",   tenantId != null ? tenantId.toString() : "",
                            "tenantName", tenantName,
                            "reason",     finalReason,
                            "riskScore",  finalScore,
                            "details",    "Total " + depositCount + " deposits, largest KES " + maxSingle.longValue() + " in 24h"
                    ));
                })
                .sorted(Comparator.comparingInt(m -> -((int) m.get("riskScore"))))
                .collect(Collectors.toList());

        return toPage(flagged, page, size);
    }

    @Transactional
    public void suspendUser(UUID userId, UUID tenantId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "User not found"));
        if (!user.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("TENANT_MISMATCH", "User not in specified tenant");
        }
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);
        log.warn("[SuperAdmin] Suspended user {} (tenant {}) reason: {}", userId, tenantId, reason);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long countSuspiciousUsers() {
        try {
            return getSuspiciousUsers(0, Integer.MAX_VALUE).getTotalElements();
        } catch (Exception e) { return 0; }
    }

    private <T> Page<T> toPage(List<T> all, int page, int size) {
        int total  = all.size();
        int from   = Math.min(page * size, total);
        int to     = Math.min(from + size, total);
        return new PageImpl<>(all.subList(from, to), PageRequest.of(page, size), total);
    }
}
