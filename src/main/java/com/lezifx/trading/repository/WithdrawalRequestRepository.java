package com.lezifx.trading.repository;

import com.lezifx.trading.domain.enums.WithdrawalStatus;
import com.lezifx.trading.domain.mpesa.WithdrawalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {

    Page<WithdrawalRequest> findByTenantIdAndStatus(UUID tenantId, WithdrawalStatus status, Pageable pageable);

    Page<WithdrawalRequest> findByUserId(UUID userId, Pageable pageable);

    Page<WithdrawalRequest> findByTenantId(UUID tenantId, Pageable pageable);

    long countByTenantIdAndStatus(UUID tenantId, WithdrawalStatus status);
}