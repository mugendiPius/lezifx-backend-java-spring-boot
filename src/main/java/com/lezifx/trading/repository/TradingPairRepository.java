package com.lezifx.trading.repository;

import com.lezifx.trading.domain.trading.TradingPair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TradingPairRepository extends JpaRepository<TradingPair, UUID> {

    List<TradingPair> findByTenantIdIsNullAndIsEnabledTrue();

    List<TradingPair> findByTenantIdAndIsEnabledTrue(UUID tenantId);

    List<TradingPair> findByTenantIdIsNullOrTenantId(UUID tenantId);

    List<TradingPair> findByTenantId(UUID tenantId);
}