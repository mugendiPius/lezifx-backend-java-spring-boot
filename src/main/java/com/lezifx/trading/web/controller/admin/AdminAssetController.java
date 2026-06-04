package com.lezifx.trading.web.controller.admin;

import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.service.admin.AdminAssetService;
import com.lezifx.trading.web.dto.response.TradingPairResponse;
import com.lezifx.trading.web.dto.response.TradingPairToggleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/assets/{pairId}/toggle")
    public ResponseEntity<TradingPairToggleResponse> toggleAsset(
            @PathVariable UUID pairId,
            @AuthenticationPrincipal String adminId) {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(adminAssetService.toggleAsset(pairId, tenantId, adminId));
    }
}