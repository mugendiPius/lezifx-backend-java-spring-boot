package com.lezifx.trading.web.dto.request;

import lombok.Data;

import java.util.List;

/**
 * Request body for PUT /admin/platform/domains
 * and PUT /superadmin/tenants/{id}/domains.
 * Contains the full replacement list of allowed origins for a tenant.
 */
@Data
public class UpdateTenantDomainsRequest {
    private List<String> domains;
}