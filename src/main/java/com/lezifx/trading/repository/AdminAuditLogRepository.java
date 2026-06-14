package com.lezifx.trading.repository;

import com.lezifx.trading.domain.audit.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    Page<AdminAuditLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<AdminAuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);

    @Query("""
        SELECT a FROM AdminAuditLog a
        WHERE a.tenantId = :tenantId
          AND (:action   IS NULL OR a.action     = :action)
          AND (:actorId  IS NULL OR a.actorId    = :actorId)
          AND (:from     IS NULL OR a.createdAt >= :from)
          AND (:to       IS NULL OR a.createdAt <= :to)
        ORDER BY a.createdAt DESC
        """)
    Page<AdminAuditLog> findFiltered(
        @Param("tenantId") UUID tenantId,
        @Param("action")   String action,
        @Param("actorId")  UUID actorId,
        @Param("from")     Instant from,
        @Param("to")       Instant to,
        Pageable pageable
    );

    /** Distinct action strings for a tenant — drives the filter dropdown. */
    @Query("SELECT DISTINCT a.action FROM AdminAuditLog a WHERE a.tenantId = :tenantId ORDER BY a.action")
    List<String> findDistinctActionsByTenant(@Param("tenantId") UUID tenantId);
}