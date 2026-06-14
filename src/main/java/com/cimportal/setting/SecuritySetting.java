package com.cimportal.setting;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "security_setting")
public class SecuritySetting {

    @Id
    private Long id;

    @Column(name = "sso_enabled", nullable = false)
    private boolean ssoEnabled;

    @Column(name = "sso_issuer_uri", length = 512)
    private String ssoIssuerUri;

    @Column(name = "sso_client_id", length = 255)
    private String ssoClientId;

    @Column(name = "sso_scopes", nullable = false, length = 255)
    private String ssoScopes;

    @Column(name = "sso_username_claim", nullable = false, length = 64)
    private String ssoUsernameClaim;

    @Column(name = "internal_password_hash", length = 100)
    private String internalPasswordHash;

    @Column(name = "info_panel_enabled", nullable = false)
    private boolean infoPanelEnabled = true;

    @Column(name = "hero_enabled", nullable = false)
    private boolean heroEnabled = true;

    @Column(name = "duty_api_base_url", length = 512)
    private String dutyApiBaseUrl;

    @Column(name = "duty_api_key", length = 512)
    private String dutyApiKey;

    @Column(name = "lark_base_url", length = 512)
    private String larkBaseUrl;

    @Column(name = "lark_app_id", length = 255)
    private String larkAppId;

    @Column(name = "lark_app_secret", length = 512)
    private String larkAppSecret;

    @Column(name = "lark_receiver_id", length = 255)
    private String larkReceiverId;

    @Column(name = "lark_receiver_id_type", length = 16)
    private String larkReceiverIdType;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected SecuritySetting() { }

    public Long getId() { return id; }
    public boolean isSsoEnabled() { return ssoEnabled; }
    public String getSsoIssuerUri() { return ssoIssuerUri; }
    public String getSsoClientId() { return ssoClientId; }
    public String getSsoScopes() { return ssoScopes; }
    public String getSsoUsernameClaim() { return ssoUsernameClaim; }
    public String getInternalPasswordHash() { return internalPasswordHash; }
    public boolean isInfoPanelEnabled() { return infoPanelEnabled; }
    public boolean isHeroEnabled() { return heroEnabled; }
    public String getDutyApiBaseUrl() { return dutyApiBaseUrl; }
    public String getDutyApiKey() { return dutyApiKey; }
    public String getLarkBaseUrl() { return larkBaseUrl; }
    public String getLarkAppId() { return larkAppId; }
    public String getLarkAppSecret() { return larkAppSecret; }
    public String getLarkReceiverId() { return larkReceiverId; }
    public String getLarkReceiverIdType() { return larkReceiverIdType; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setSsoEnabled(boolean ssoEnabled) { this.ssoEnabled = ssoEnabled; }
    public void setSsoIssuerUri(String ssoIssuerUri) { this.ssoIssuerUri = ssoIssuerUri; }
    public void setSsoClientId(String ssoClientId) { this.ssoClientId = ssoClientId; }
    public void setSsoScopes(String ssoScopes) { this.ssoScopes = ssoScopes; }
    public void setSsoUsernameClaim(String ssoUsernameClaim) { this.ssoUsernameClaim = ssoUsernameClaim; }
    public void setInternalPasswordHash(String internalPasswordHash) { this.internalPasswordHash = internalPasswordHash; }
    public void setInfoPanelEnabled(boolean infoPanelEnabled) { this.infoPanelEnabled = infoPanelEnabled; }
    public void setHeroEnabled(boolean heroEnabled) { this.heroEnabled = heroEnabled; }
    public void setDutyApiBaseUrl(String dutyApiBaseUrl) { this.dutyApiBaseUrl = dutyApiBaseUrl; }
    public void setDutyApiKey(String dutyApiKey) { this.dutyApiKey = dutyApiKey; }
    public void setLarkBaseUrl(String larkBaseUrl) { this.larkBaseUrl = larkBaseUrl; }
    public void setLarkAppId(String larkAppId) { this.larkAppId = larkAppId; }
    public void setLarkAppSecret(String larkAppSecret) { this.larkAppSecret = larkAppSecret; }
    public void setLarkReceiverId(String larkReceiverId) { this.larkReceiverId = larkReceiverId; }
    public void setLarkReceiverIdType(String larkReceiverIdType) { this.larkReceiverIdType = larkReceiverIdType; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
