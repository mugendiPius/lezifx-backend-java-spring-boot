package com.lezifx.trading.repository;

import com.lezifx.trading.domain.commission.CommissionStatement;
import com.lezifx.trading.domain.enums.StatementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommissionStatementRepository extends JpaRepository<CommissionStatement, UUID> {

    @Query("SELECT cs FROM CommissionStatement cs WHERE cs.tenant.id = :tenantId ORDER BY cs.periodEnd DESC LIMIT 1")
    Optional<CommissionStatement> findTopByTenantIdOrderByPeriodEndDesc(@Param("tenantId") UUID tenantId);

    Page<CommissionStatement> findByTenantId(UUID tenantId, Pageable pageable);
    Page<CommissionStatement> findByStatus(StatementStatus status, Pageable pageable);
    Page<CommissionStatement> findByTenantIdAndStatus(UUID tenantId, StatementStatus status, Pageable pageable);
    long countByStatus(StatementStatus status);

    @Query("SELECT COALESCE(SUM(cs.platformCommission), 0) FROM CommissionStatement cs WHERE cs.status = :status")
    BigDecimal sumCommissionAmountByStatus(@Param("status") StatementStatus status);
}