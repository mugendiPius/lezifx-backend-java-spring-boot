package com.lezifx.trading.repository;

import com.lezifx.trading.domain.enums.WalletTransactionType;
import com.lezifx.trading.domain.wallet.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    Page<WalletTransaction> findByTenantId(UUID tenantId, Pageable pageable);

    // ── Tenant-level aggregates ──────────────────────────────────────────────

    @Query("""
        SELECT COALESCE(SUM(wt.amount), 0)
        FROM WalletTransaction wt
        WHERE wt.tenant.id = :tenantId
          AND wt.type = 'DEPOSIT'
          AND wt.isDemo = false
          AND wt.isMarketerTransaction = false
    """)
    BigDecimal sumDepositsForTenant(@Param("tenantId") UUID tenantId);

    @Query("""
        SELECT COALESCE(SUM(wt.amount), 0)
        FROM WalletTransaction wt
        WHERE wt.tenant.id = :tenantId
          AND wt.type = 'WITHDRAWAL'
          AND wt.isDemo = false
          AND wt.isMarketerTransaction = false
    """)
    BigDecimal sumWithdrawalsForTenant(@Param("tenantId") UUID tenantId);

    @Query("""
        SELECT COALESCE(SUM(ABS(wt.amount)), 0)
        FROM WalletTransaction wt
        WHERE wt.tenant.id = :tenantId
          AND wt.type = 'TRADE_STAKE'
          AND wt.isDemo = false
          AND wt.isMarketerTransaction = false
    """)
    BigDecimal sumStakesForTenant(@Param("tenantId") UUID tenantId);

    // ── User-level paginated queries ─────────────────────────────────────────

    @Query("""
        SELECT wt FROM WalletTransaction wt
        WHERE wt.wallet.user.id = :userId
        ORDER BY wt.createdAt DESC
    """)
    Page<WalletTransaction> findByUserIdOrderByCreatedAtDesc(
        @Param("userId") UUID userId, Pageable pageable);

    @Query("""
        SELECT wt FROM WalletTransaction wt
        WHERE wt.wallet.user.id = :userId
          AND wt.type = :type
        ORDER BY wt.createdAt DESC
    """)
    Page<WalletTransaction> findByUserIdAndTypeOrderByCreatedAtDesc(
        @Param("userId") UUID userId,
        @Param("type") WalletTransactionType type,
        Pageable pageable);

    @Query("""
        SELECT wt FROM WalletTransaction wt
        WHERE wt.wallet.user.id = :userId
          AND wt.isDemo = :isDemo
        ORDER BY wt.createdAt DESC
    """)
    Page<WalletTransaction> findByUserIdAndIsDemoOrderByCreatedAtDesc(
        @Param("userId") UUID userId,
        @Param("isDemo") boolean isDemo,
        Pageable pageable);

    // ── User-level aggregates ────────────────────────────────────────────────

    @Query("""
        SELECT COALESCE(SUM(ABS(wt.amount)), 0)
        FROM WalletTransaction wt
        WHERE wt.wallet.user.id = :userId
          AND wt.type = 'TRADE_STAKE'
          AND wt.isDemo = false
    """)
    BigDecimal sumStakedByUser(@Param("userId") UUID userId);

    @Query("""
        SELECT COALESCE(SUM(wt.amount), 0)
        FROM WalletTransaction wt
        WHERE wt.wallet.user.id = :userId
          AND wt.type = 'DEPOSIT'
          AND wt.isDemo = false
    """)
    BigDecimal sumDepositsByUser(@Param("userId") UUID userId);

    @Query("""
        SELECT COALESCE(SUM(ABS(wt.amount)), 0)
        FROM WalletTransaction wt
        WHERE wt.wallet.user.id = :userId
          AND wt.type = 'WITHDRAWAL'
          AND wt.isDemo = false
    """)
    BigDecimal sumWithdrawalsByUser(@Param("userId") UUID userId);
}