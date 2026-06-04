package com.lezifx.trading.repository;

import com.lezifx.trading.domain.enums.UserRole;
import com.lezifx.trading.domain.user.User;
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
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);

    List<User> findByTenantIdAndRole(UUID tenantId, UserRole role);

    List<User> findByTenantIdAndIsMarketerTrue(UUID tenantId);

    long countByTenantIdAndCreatedAtAfter(UUID tenantId, Instant after);

    long countByTenantId(UUID tenantId);

    long countByTenantIdNot(UUID tenantId);

    @Query("""
        SELECT u FROM User u
        WHERE u.tenant.id = :tenantId
          AND (:role IS NULL OR CAST(u.role AS string) = :role)
          AND (:status IS NULL OR CAST(u.status AS string) = :status)
          AND (:search IS NULL
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY u.createdAt DESC
    """)
    Page<User> findByTenantIdWithFilters(
        @Param("tenantId") UUID tenantId,
        @Param("role") String role,
        @Param("status") String status,
        @Param("search") String search,
        Pageable pageable);
}