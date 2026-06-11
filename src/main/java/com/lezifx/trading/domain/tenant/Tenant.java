package com.lezifx.trading.domain.tenant;

import com.lezifx.trading.domain.enums.PlatformMode;
import com.lezifx.trading.domain.enums.TenantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * All domains and deployment URLs that resolve to this tenant.
     * Replaces the old single custom_domain column.
     * Examples: ["poa-trade.com", "www.poa-trade.com", "poa-trade.onrender.com"]
     * PublicConfigController matches incoming Origin/X-Domain against this array.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_origins", columnDefinition = "text[]")
    private String[] allowedOrigins;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TenantStatus status;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "house_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal houseBalance;

    @Column(name = "floor_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal floorBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_mode", nullable = false, length = 10)
    private PlatformMode platformMode;

    @Column(name = "kill_switch_active", nullable = false)
    private Boolean killSwitchActive;

    @Column(name = "daraja_consumer_key")
    private String darajaConsumerKey;

    @Column(name = "daraja_consumer_secret")
    private String darajaConsumerSecret;

    @Column(name = "daraja_passkey")
    private String darajaPasskey;

    @Column(name = "daraja_shortcode", length = 20)
    private String darajaShortcode;

    @Column(name = "daraja_b2c_initiator_name")
    private String darajaB2cInitiatorName;

    @Column(name = "daraja_b2c_security_cred")
    private String darajaB2cSecurityCred;

    @Column(name = "daraja_environment", length = 10)
    private String darajaEnvironment;

    @Column(name = "daraja_c2b_registered")
    private Boolean darajaC2bRegistered;

    @Column(name = "min_deposit", precision = 10, scale = 2)
    private BigDecimal minDeposit;

    @Column(name = "max_deposit", precision = 10, scale = 2)
    private BigDecimal maxDeposit;

    @Column(name = "min_withdrawal", precision = 10, scale = 2)
    private BigDecimal minWithdrawal;

    @Column(name = "max_withdrawal", precision = 10, scale = 2)
    private BigDecimal maxWithdrawal;

    @Column(name = "auto_withdrawal_limit", precision = 10, scale = 2)
    private BigDecimal autoWithdrawalLimit;

    @Column(name = "demo_balance", precision = 10, scale = 2)
    private BigDecimal demoBalance;

    @Column(name = "default_marketer_balance", precision = 10, scale = 2)
    private BigDecimal defaultMarketerBalance;

    @Column(name = "registration_open")
    private Boolean registrationOpen;

    @Column(name = "kyc_required")
    private Boolean kycRequired;

    @Column(name = "max_concurrent_trades")
    private Integer maxConcurrentTrades;

    @Column(name = "brand_name", length = 100)
    private String brandName;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "favicon_url")
    private String faviconUrl;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "accent_color", length = 7)
    private String accentColor;

    @Column(name = "support_email", length = 255)
    private String supportEmail;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "marketer_withdrawal_enabled")
    private Boolean marketerWithdrawalEnabled;

    @Column(name = "marketer_max_withdrawal", precision = 10, scale = 2)
    private BigDecimal marketerMaxWithdrawal;

    private String tagline;
}