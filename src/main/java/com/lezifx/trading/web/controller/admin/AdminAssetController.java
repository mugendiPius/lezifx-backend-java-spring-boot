package com.lezifx.trading.web.controller.admin;

import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.service.admin.AdminAssetService;
import com.lezifx.trading.web.dto.request.SetAssetEnabledRequest;
import com.lezifx.trading.web.dto.response.TradingPairResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAssetController {

    private final AdminAssetService adminAssetService;

    @GetMapping("/assets")
    public ResponseEntity<List<TradingPairResponse>> listAssets() {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(adminAssetService.listAssets(tenantId));
    }

    /**
     * BUG 1 FIX: was POST /assets/{pairId}/toggle with no body.
     * Now PUT /assets/{pairId} with { isEnabled: boolean } body.
     * Matches frontend PUT /admin/assets/{id} { isEnabled }.
     */
    @PutMapping("/assets/{pairId}")
    public ResponseEntity<TradingPairResponse> setAssetEnabled(
            @PathVariable UUID pairId,
            @RequestBody SetAssetEnabledRequest request,
            @AuthenticationPrincipal String adminId) {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(
            adminAssetService.setAssetEnabled(pairId, tenantId, request.isEnabled(), adminId));
    }
}