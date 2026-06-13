package com.lezifx.trading.web.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SetAssetEnabledRequest {
    @JsonProperty("isEnabled")
    private boolean isEnabled;
}