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

    Optional<Tenant> findByCustomDomain(String customDomain);

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