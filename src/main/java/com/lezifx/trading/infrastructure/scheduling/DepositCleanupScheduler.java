package com.lezifx.trading.infrastructure.scheduling;

import com.lezifx.trading.domain.enums.DepositStatus;
import com.lezifx.trading.domain.mpesa.DepositRequest;
import com.lezifx.trading.repository.DepositRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Expires PENDING deposits that never received a Safaricom callback.
 *
 * A deposit is considered stale when it has been PENDING for more than
 * EXPIRY_MINUTES without a CheckoutRequestID (meaning Safaricom never
 * acknowledged the STK push) OR without a callback after EXPIRY_MINUTES.
 *
 * Runs every 15 minutes. Safe to run repeatedly — idempotent status check.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DepositCleanupScheduler {

    private static final int EXPIRY_MINUTES = 15;

    private final DepositRequestRepository depositRequestRepository;

    @Scheduled(fixedDelay = 15 * 60 * 1000)
    @Transactional
    public void expireStaleDeposits() {
        Instant cutoff = Instant.now().minus(EXPIRY_MINUTES, ChronoUnit.MINUTES);
        List<DepositRequest> stale = depositRequestRepository
            .findStaleDeposits(DepositStatus.PENDING, cutoff);

        if (stale.isEmpty()) return;

        log.info("Expiring {} stale PENDING deposits older than {} minutes", stale.size(), EXPIRY_MINUTES);

        for (DepositRequest deposit : stale) {
            deposit.setStatus(DepositStatus.FAILED);
            deposit.setFailureReason("Expired — no payment confirmation received within "
                + EXPIRY_MINUTES + " minutes. Please try again.");
            depositRequestRepository.save(deposit);
            log.info("Expired deposit {} for user {} (tenant {})",
                deposit.getId(), deposit.getUser().getId(), deposit.getTenant().getId());
        }
    }
}
