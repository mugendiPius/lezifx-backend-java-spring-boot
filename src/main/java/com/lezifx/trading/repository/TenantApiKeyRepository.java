package com.lezifx.trading.repository;

import com.lezifx.trading.domain.tenant.TenantApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantApiKeyRepository extends JpaRepository<TenantApiKey, UUID> {

    Optional<TenantApiKey> findByApiKeyAndIsActiveTrue(String apiKey);

    List<TenantApiKey> findByTenantId(UUID tenantId);

    long countByTenantIdAndIsActiveTrue(UUID tenantId);
}