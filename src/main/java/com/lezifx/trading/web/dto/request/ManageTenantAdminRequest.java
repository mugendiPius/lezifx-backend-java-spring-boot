package com.lezifx.trading.web.dto.request;

import lombok.Data;

/**
 * Single request DTO for all three admin management actions
 * performed by SUPER_ADMIN on a tenant's admin users.
 *
 * action = CREATE          creates a new ADMIN user in the tenant
 * action = ELEVATE         promotes an existing PLAYER to ADMIN
 * action = RESET_PASSWORD  changes an existing ADMIN user's password
 *
 * email is required for all three actions.
 * password + fullName required for CREATE.
 * newPassword required for RESET_PASSWORD.
 */
@Data
public class ManageTenantAdminRequest {

    private String action;       // CREATE | ELEVATE | RESET_PASSWORD
    private String email;
    private String password;     // CREATE only
    private String fullName;     // CREATE only
    private String phoneNumber;  // CREATE only, optional
    private String newPassword;  // RESET_PASSWORD only
}