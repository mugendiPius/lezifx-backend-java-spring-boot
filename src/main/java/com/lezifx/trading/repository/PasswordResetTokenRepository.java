package com.lezifx.trading.repository;

import com.lezifx.trading.domain.user.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** Latest unused, non-expired token for this tenant+email */
    @Query("""
        SELECT t FROM PasswordResetToken t
        WHERE t.tenant.id = :tenantId
          AND t.email = :email
          AND t.used = false
          AND t.expiresAt > :now
        ORDER BY t.createdAt DESC
        """)
    Optional<PasswordResetToken> findValidToken(
        @Param("tenantId") UUID tenantId,
        @Param("email") String email,
        @Param("now") Instant now
    );

    /** Invalidate all pending tokens for this tenant+email before issuing a new one */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.tenant.id = :tenantId AND t.email = :email AND t.used = false")
    void invalidateAll(@Param("tenantId") UUID tenantId, @Param("email") String email);

    /** Purge expired tokens — called on a schedule */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :cutoff")
    void deleteExpired(@Param("cutoff") Instant cutoff);
}
