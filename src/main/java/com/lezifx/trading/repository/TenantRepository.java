package com.lezifx.trading.repository;

import com.lezifx.trading.domain.enums.TenantStatus;
import com.lezifx.trading.domain.tenant.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /**
     * Find a tenant whose allowed_origins array contains the given domain.
     * Uses PostgreSQL native array operator @> for GIN-indexed lookup.
     * Called by PublicConfigController on every /public/config request.
     */
    @Query(value = "SELECT * FROM tenants WHERE allowed_origins @> ARRAY[:domain]::text[] LIMIT 1",
           nativeQuery = true)
    Optional<Tenant> findByAllowedOriginsContaining(@Param("domain") String domain);

    /**
     * Legacy method kept for any existing callers  delegates to array lookup.
     * Will find a tenant only if the domain exists in their allowed_origins.
     */
    default Optional<Tenant> findByCustomDomain(String domain) {
        return findByAllowedOriginsContaining(domain);
    }

    List<Tenant> findByStatus(TenantStatus status);

    Page<Tenant> findByIdNot(UUID excludeId, Pageable pageable);
    Page<Tenant> findByIdNotAndStatus(UUID excludeId, TenantStatus status, Pageable pageable);
    Page<Tenant> findByIdNotAndNameContainingIgnoreCase(UUID excludeId, String name, Pageable pageable);
    Page<Tenant> findByIdNotAndStatusAndNameContainingIgnoreCase(
            UUID excludeId, TenantStatus status, String name, Pageable pageable);

    long countByIdNot(UUID excludeId);
    long countByStatusAndIdNot(TenantStatus status, UUID excludeId);

    @Modifying
    @Transactional
    @Query("UPDATE Tenant t SET t.houseBalance = t.houseBalance + :delta WHERE t.id = :tenantId")
    void adjustHouseBalance(@Param("tenantId") UUID tenantId, @Param("delta") BigDecimal delta);
}