package com.lezifx.trading.repository;

import com.lezifx.trading.domain.mpesa.DepositRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepositRequestRepository extends JpaRepository<DepositRequest, UUID> {

    Optional<DepositRequest> findByCheckoutRequestId(String checkoutRequestId);

    Optional<DepositRequest> findByMerchantRequestId(String merchantRequestId);

    Page<DepositRequest> findByTenantId(UUID tenantId, Pageable pageable);

    Page<DepositRequest> findByUserId(UUID userId, Pageable pageable);
}