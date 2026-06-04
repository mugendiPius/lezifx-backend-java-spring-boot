package com.lezifx.trading.service.platform;

import com.lezifx.trading.domain.enums.PlatformMode;
import com.lezifx.trading.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformModeService {

    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public PlatformMode getMode(UUID tenantId) {
        return tenantRepository.findById(tenantId)
            .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId))
            .getPlatformMode();
    }
}