package com.lezifx.trading.repository;

import com.lezifx.trading.domain.commission.CommissionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CommissionPaymentRepository extends JpaRepository<CommissionPayment, UUID> {
}