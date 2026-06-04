package com.lezifx.trading.repository;

import com.lezifx.trading.domain.trading.PriceTick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceTickRepository extends JpaRepository<PriceTick, Long> {

    List<PriceTick> findTop100ByTenantIdAndSymbolOrderByTickTimeDesc(UUID tenantId, String symbol);

    @Query("""
        SELECT pt FROM PriceTick pt
        WHERE pt.tenantId = :tenantId AND pt.symbol = :symbol
        ORDER BY pt.tickTime DESC
        LIMIT 1
    """)
    Optional<PriceTick> findLatestByTenantIdAndSymbol(
        @Param("tenantId") UUID tenantId,
        @Param("symbol") String symbol
    );

    @Modifying
    @Transactional
    @Query("DELETE FROM PriceTick pt WHERE pt.tickTime < :cutoff")
    int deleteByTickTimeBefore(@Param("cutoff") Instant cutoff);
}