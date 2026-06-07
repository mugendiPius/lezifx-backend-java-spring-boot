package com.lezifx.trading.web.dto.request;

import lombok.Data;

@Data
public class SetDarajaCredentialsRequest {

    /** Frontend field: consumerKey */
    private String consumerKey;

    /** Frontend field: consumerSecret */
    private String consumerSecret;

    /** Frontend field: shortCode (was `shortcode` — case fixed) */
    private String shortCode;

    /** Frontend field: passkey */
    private String passkey;

    /**
     * Frontend field: callbackUrl.
     * Stored to tenant; used as the C2B / STK callback base URL.
     */
    private String callbackUrl;

    /** Extra fields retained for SUPER_ADMIN extended config (not sent by basic frontend form). */
    private String b2cInitiatorName;
    private String b2cSecurityCred;
    private String environment;

    /**
     * Backwards-compat getter: AdminPlatformService calls getShortcode().
     * Delegates to shortCode so the service does not need to change.
     */
    public String getShortcode() {
        return shortCode;
    }
}