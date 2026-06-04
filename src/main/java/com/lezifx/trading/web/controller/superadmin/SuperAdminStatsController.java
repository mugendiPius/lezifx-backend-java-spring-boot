package com.lezifx.trading.web.controller.superadmin;

import com.lezifx.trading.service.superadmin.SuperAdminPlatformService;
import com.lezifx.trading.web.dto.response.SuperAdminStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/superadmin")
@RequiredArgsConstructor
public class SuperAdminStatsController {

    private final SuperAdminPlatformService superAdminPlatformService;

    @GetMapping("/stats")
    public ResponseEntity<SuperAdminStatsResponse> getPlatformStats() {
        return ResponseEntity.ok(superAdminPlatformService.getPlatformStats());
    }
}