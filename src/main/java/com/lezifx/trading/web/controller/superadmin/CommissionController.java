package com.lezifx.trading.web.controller.superadmin;

import com.lezifx.trading.service.superadmin.CommissionService;
import com.lezifx.trading.web.dto.request.MarkCommissionPaidRequest;
import com.lezifx.trading.web.dto.response.CommissionStatementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/superadmin/commissions")
@RequiredArgsConstructor
public class CommissionController {

    private final CommissionService commissionService;

    @GetMapping
    public ResponseEntity<Page<CommissionStatementResponse>> listStatements(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                commissionService.listStatements(tenantId, status, page, size));
    }

    @PutMapping("/{statementId}/mark-paid")
    public ResponseEntity<Void> markPaid(
            @PathVariable UUID statementId,
            @RequestBody MarkCommissionPaidRequest req,
            @AuthenticationPrincipal String adminId) {
        commissionService.markPaid(statementId, adminId, req.getNotes());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> runCommissionCalculation(
            @AuthenticationPrincipal String adminId) {
        commissionService.calculateAndSaveCommissions();
        return ResponseEntity.ok(Map.of("message", "Commission calculation triggered"));
    }
}