package com.lezifx.trading.web.controller.admin;

import com.lezifx.trading.domain.enums.WalletTransactionType;
import com.lezifx.trading.domain.wallet.WalletTransaction;
import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.repository.WalletTransactionRepository;
import com.lezifx.trading.web.dto.response.WalletTransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/transactions")
@RequiredArgsConstructor
public class AdminTransactionController {

    private final WalletTransactionRepository walletTransactionRepository;

    /**
     * Paginated, filterable transaction list for the current tenant.
     *
     * Query params:
     *   type    — WalletTransactionType enum name (DEPOSIT, WITHDRAWAL, TRADE_WIN, …)
     *   isDemo  — true | false (omit = both)
     *   from    — ISO date (yyyy-MM-dd), inclusive
     *   to      — ISO date (yyyy-MM-dd), inclusive (end of day)
     *   userId  — filter to a specific user UUID
     *   page    — 0-based page number (default 0)
     *   size    — page size (default 20, max 100)
     */
    @GetMapping
    public ResponseEntity<Page<WalletTransactionResponse>> listTransactions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean isDemo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID tenantId = TenantContext.get();
        int safeSize  = Math.min(size, 100);

        WalletTransactionType txType = null;
        if (type != null && !type.isBlank()) {
            try { txType = WalletTransactionType.valueOf(type.toUpperCase()); }
            catch (IllegalArgumentException ignored) { /* unknown type → return empty */ }
        }

        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant   = to   != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        Page<WalletTransaction> page_ = walletTransactionRepository.findByTenantFiltered(
                tenantId, txType, isDemo, fromInstant, toInstant, userId,
                PageRequest.of(page, safeSize));

        return ResponseEntity.ok(page_.map(this::toResponse));
    }

    private WalletTransactionResponse toResponse(WalletTransaction wt) {
        var user = wt.getWallet() != null && wt.getWallet().getUser() != null
                ? wt.getWallet().getUser() : null;
        return WalletTransactionResponse.builder()
                .id(wt.getId())
                .type(wt.getType().name())
                .amount(wt.getAmount())
                .balanceBefore(wt.getBalanceBefore())
                .balanceAfter(wt.getBalanceAfter())
                .isDemo(wt.getIsDemo())
                .isMarketerTransaction(wt.getIsMarketerTransaction())
                .referenceId(wt.getReferenceId())
                .referenceType(wt.getReferenceType())
                .description(wt.getDescription())
                .userId(user != null ? user.getId() : null)
                .userEmail(user != null ? user.getEmail() : null)
                .userFullName(user != null ? user.getFullName() : null)
                .createdAt(wt.getCreatedAt())
                .build();
    }
}
