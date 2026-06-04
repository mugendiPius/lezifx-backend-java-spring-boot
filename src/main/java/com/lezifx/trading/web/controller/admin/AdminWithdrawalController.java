package com.lezifx.trading.web.controller.admin;

import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.service.admin.AdminWithdrawalService;
import com.lezifx.trading.web.dto.request.RejectWithdrawalRequest;
import com.lezifx.trading.web.dto.response.AdminWithdrawalResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminWithdrawalController {

    private final AdminWithdrawalService adminWithdrawalService;

    @GetMapping("/withdrawals")
    public ResponseEntity<Page<AdminWithdrawalResponse>> listWithdrawals(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(
            adminWithdrawalService.listWithdrawals(tenantId, status, page, size));
    }

    @PutMapping("/withdrawals/{withdrawalId}/approve")
    public ResponseEntity<Map<String, String>> approve(
            @PathVariable UUID withdrawalId,
            @AuthenticationPrincipal String adminId) {
        UUID tenantId = TenantContext.get();
        adminWithdrawalService.approve(withdrawalId, tenantId, adminId);
        return ResponseEntity.ok(Map.of("message", "Withdrawal approved"));
    }

    @PutMapping("/withdrawals/{withdrawalId}/reject")
    public ResponseEntity<Map<String, String>> reject(
            @PathVariable UUID withdrawalId,
            @Valid @RequestBody RejectWithdrawalRequest request,
            @AuthenticationPrincipal String adminId) {
        UUID tenantId = TenantContext.get();
        adminWithdrawalService.reject(withdrawalId, tenantId, request.getReason(), adminId);
        return ResponseEntity.ok(Map.of("message", "Withdrawal rejected and funds returned"));
    }
}