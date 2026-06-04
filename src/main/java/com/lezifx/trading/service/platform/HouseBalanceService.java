package com.lezifx.trading.service.platform;

import com.lezifx.trading.domain.enums.PlatformMode;
import com.lezifx.trading.domain.tenant.Tenant;
import com.lezifx.trading.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class HouseBalanceService {

    private final TenantRepository tenantRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void adjustBalance(UUID tenantId, BigDecimal delta) {
        tenantRepository.adjustHouseBalance(tenantId, delta);
        checkAndApplyAutoModeSwitch(tenantId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getHouseRatio(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        BigDecimal floor = tenant.getFloorBalance();
        if (floor == null || floor.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(999);
        }

        BigDecimal house = tenant.getHouseBalance() != null
            ? tenant.getHouseBalance() : BigDecimal.ZERO;

        return house.divide(floor, 4, RoundingMode.HALF_UP);
    }

    private void checkAndApplyAutoModeSwitch(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) return;

        BigDecimal floor = tenant.getFloorBalance();
        if (floor == null || floor.compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal house = tenant.getHouseBalance() != null
            ? tenant.getHouseBalance() : BigDecimal.ZERO;

        double ratio = house.doubleValue() / floor.doubleValue();
        PlatformMode currentMode = tenant.getPlatformMode();
        PlatformMode newMode = currentMode;

        if (ratio < 0.50) {
            newMode = PlatformMode.LOSE;
        } else if (ratio < 1.20 && currentMode == PlatformMode.WIN) {
            newMode = PlatformMode.LOSE;
        } else if (ratio >= 1.20 && currentMode == PlatformMode.LOSE) {
            newMode = PlatformMode.NORMAL;
        }

        if (newMode != currentMode) {
            tenant.setPlatformMode(newMode);
            tenantRepository.save(tenant);
            log.info("Platform mode switched for tenant {}: {} -> {}", tenantId, currentMode, newMode);

            try {
                // Cast to Object explicitly to avoid ambiguity with the
                // convertAndSend(D, Map<String,Object>) headers overload
                Object payload = Map.of(
                    "event", "MODE_CHANGE",
                    "mode", newMode.name(),
                    "tenantId", tenantId.toString()
                );
                messagingTemplate.convertAndSend("/topic/" + tenantId + "/platform", payload);
            } catch (Exception e) {
                log.warn("Could not broadcast mode change for tenant {}: {}", tenantId, e.getMessage());
            }
        }
    }
}