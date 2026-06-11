package com.lezifx.trading.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned by GET /superadmin/tenants/{id}/admins
 * and POST /superadmin/tenants/{id}/admins.
 * Contains only the fields SUPER_ADMIN needs to see  no wallet data.
 */
@Data
@Builder
public class TenantAdminResponse {

    private UUID    id;
    private String  email;
    private String  fullName;
    private String  phoneNumber;
    private String  role;
    private String  status;
    private Instant createdAt;
}