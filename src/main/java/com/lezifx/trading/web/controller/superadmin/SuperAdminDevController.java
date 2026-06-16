package com.lezifx.trading.web.controller.superadmin;

import com.lezifx.trading.domain.mpesa.MpesaCallback;
import com.lezifx.trading.service.superadmin.SuperAdminDevService;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Dev & Support endpoints — SUPER_ADMIN only (enforced by SecurityConfig).
 * Prefix: /api/v1/superadmin/dev
 */
@RestController
@RequestMapping("/api/v1/superadmin/dev")
@RequiredArgsConstructor
public class SuperAdminDevController {

    private final SuperAdminDevService devService;

    // ── Stats ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(devService.getDevStats());
    }

    // ── Failed M-Pesa callbacks ───────────────────────────────────────────────

    @GetMapping("/failed-callbacks")
    public ResponseEntity<Page<MpesaCallback>> getFailedCallbacks(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(devService.getFailedCallbacks(page, size));
    }

    @PostMapping("/failed-callbacks/{callbackId}/retry")
    public ResponseEntity<Map<String, String>> retryCallback(
            @PathVariable UUID callbackId) {
        devService.retryCallback(callbackId);
        return ResponseEntity.ok(Map.of("message", "Callback queued for retry"));
    }

    // ── High-value transactions ───────────────────────────────────────────────

    @GetMapping("/high-value")
    public ResponseEntity<Page<Map<String, Object>>> getHighValueTransactions(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(devService.getHighValueTransactions(page, size));
    }

    // ── Suspicious users ──────────────────────────────────────────────────────

    @GetMapping("/suspicious")
    public ResponseEntity<Page<Map<String, Object>>> getSuspiciousUsers(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(devService.getSuspiciousUsers(page, size));
    }

    // ── Cross-tenant user suspension ──────────────────────────────────────────

    @PostMapping("/suspend-user")
    public ResponseEntity<Map<String, String>> suspendUser(
            @RequestBody Map<String, String> req) {
        String userId   = req.get("userId");
        String tenantId = req.get("tenantId");
        String reason   = req.get("reason");
        if (userId == null || tenantId == null) {
            throw new BusinessException("MISSING_FIELDS", "userId and tenantId are required");
        }
        devService.suspendUser(UUID.fromString(userId), UUID.fromString(tenantId),
                reason != null ? reason : "Suspended by SuperAdmin");
        return ResponseEntity.ok(Map.of("message", "User suspended"));
    }
}
