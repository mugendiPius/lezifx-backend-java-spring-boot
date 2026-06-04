package com.lezifx.trading.web.dto.request;

import lombok.Data;

@Data
public class SetDarajaCredentialsRequest {

    private String consumerKey;
    private String consumerSecret;
    private String passkey;
    private String shortcode;
    private String b2cInitiatorName;
    private String b2cSecurityCred;
    private String environment;
}