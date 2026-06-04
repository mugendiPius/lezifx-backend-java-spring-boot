package com.lezifx.trading.repository;

import com.lezifx.trading.domain.enums.TradeOutcome;
import com.lezifx.trading.domain.enums.TradeSessionStatus;
import com.lezifx.trading.domain.trading.TradeSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TradeSessionRepository extends JpaRepository<TradeSession, UUID> {

    List<TradeSession> findAllByStatus(TradeSessionStatus status);

    List<TradeSession> findByStatusAndExpiresAtBefore(TradeSessionStatus status, Instant now);

    List<TradeSession> findByUserIdAndTenantId(UUID userId, UUID tenantId);

    @Query("""
            SELECT ts FROM TradeSession ts
            WHERE ts.user.id = :userId AND ts.tenant.id = :tenantId
              AND ts.status = 'COMPLETED'
            ORDER BY ts.settledAt DESC
            """)
    Page<TradeSession> findHistoryByUserIdAndTenantId(
            @Param("userId") UUID userId,
            @Param("tenantId") UUID tenantId,
            Pageable pageable);

    long countByUserIdAndTenantIdAndStatus(UUID userId, UUID tenantId, TradeSessionStatus status);

    long countByUserIdAndTenantIdAndStatusAndOutcomeAndIsDemo(
            UUID userId, UUID tenantId, TradeSessionStatus status,
            TradeOutcome outcome, boolean isDemo);

    long countByTenantIdAndStatusAndIsMarketerTradeFalse(UUID tenantId, TradeSessionStatus status);

    long countByStatusAndIsDemoFalseAndIsMarketerTradeFalse(TradeSessionStatus status);

    @Query("""
            SELECT COALESCE(SUM(ts.stakeAmount), 0)
            FROM TradeSession ts
            WHERE ts.tenant.id = :tenantId
              AND ts.status = 'COMPLETED'
              AND ts.isMarketerTrade = false AND ts.isDemo = false
            """)
    BigDecimal calculateGrossProfitForTenant(@Param("tenantId") UUID tenantId);

    @Query("""
            SELECT COALESCE(SUM(ts.stakeAmount), 0)
                 - COALESCE(SUM(CASE WHEN ts.outcome = 'WIN' THEN ts.profitAmount ELSE 0 END), 0)
            FROM TradeSession ts
            WHERE ts.tenant.id = :tenantId
              AND ts.status = 'COMPLETED'
              AND ts.isMarketerTrade = false AND ts.isDemo = false
              AND ts.settledAt BETWEEN :start AND :end
            """)
    BigDecimal calculateGrossProfitForTenantBetween(
            @Param("tenantId") UUID tenantId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
            SELECT COUNT(ts) FROM TradeSession ts
            WHERE ts.tenant.id = :tenantId
              AND ts.status = 'COMPLETED'
              AND ts.isMarketerTrade = false AND ts.isDemo = false
              AND ts.settledAt BETWEEN :start AND :end
            """)
    long countSettledRealTradesBetween(
            @Param("tenantId") UUID tenantId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
            SELECT COALESCE(SUM(ts.stakeAmount), 0)
                 - COALESCE(SUM(CASE WHEN ts.outcome = 'WIN' THEN ts.profitAmount ELSE 0 END), 0)
            FROM TradeSession ts
            WHERE ts.status = 'COMPLETED'
              AND ts.isMarketerTrade = false AND ts.isDemo = false
              AND ts.tenant.id != :excludeTenantId
            """)
    BigDecimal calculateTotalGrossProfitExcludingTenant(
            @Param("excludeTenantId") UUID excludeTenantId);

    @Query("""
            SELECT COALESCE(SUM(ts.stakeAmount), 0)
            FROM TradeSession ts
            WHERE ts.user.id = :userId AND ts.tenant.id = :tenantId
              AND ts.status = 'COMPLETED' AND ts.isDemo = false
            """)
    BigDecimal sumStakedByUser(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);

    @Query("""
            SELECT COALESCE(SUM(ts.profitAmount), 0)
            FROM TradeSession ts
            WHERE ts.user.id = :userId AND ts.tenant.id = :tenantId
              AND ts.status = 'COMPLETED' AND ts.outcome = 'WIN' AND ts.isDemo = false
            """)
    BigDecimal sumProfitByUser(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
}