package com.lezifx.trading.repository;

import com.lezifx.trading.domain.enums.DepositStatus;
import com.lezifx.trading.domain.mpesa.DepositRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepositRequestRepository extends JpaRepository<DepositRequest, UUID> {

    Optional<DepositRequest> findByCheckoutRequestId(String checkoutRequestId);

    Optional<DepositRequest> findByMerchantRequestId(String merchantRequestId);

    Page<DepositRequest> findByTenantId(UUID tenantId, Pageable pageable);

    Page<DepositRequest> findByUserId(UUID userId, Pageable pageable);

    /** Deposits still PENDING and older than cutoff — used for expiry cleanup. */
    @Query("SELECT d FROM DepositRequest d WHERE d.status = :status AND d.createdAt < :cutoff")
    List<DepositRequest> findStaleDeposits(
        @Param("status") DepositStatus status,
        @Param("cutoff") Instant cutoff);
}