package com.lezifx.trading.web.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class UpdateTenantDomainsRequest {
    private List<String> domains;
}