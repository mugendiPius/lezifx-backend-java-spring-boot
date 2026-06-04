package com.lezifx.trading.repository;

import com.lezifx.trading.domain.mpesa.MpesaCallback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MpesaCallbackRepository extends JpaRepository<MpesaCallback, UUID> {

    List<MpesaCallback> findByProcessedFalseOrderByCreatedAtAsc();
}