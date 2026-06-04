package com.lezifx.trading.repository;

import com.lezifx.trading.domain.trading.SocialFeedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SocialFeedEventRepository extends JpaRepository<SocialFeedEvent, UUID> {

    List<SocialFeedEvent> findTop20ByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    long countByTenantIdAndIsSimulatedFalseAndCreatedAtAfter(UUID tenantId, Instant after);
}