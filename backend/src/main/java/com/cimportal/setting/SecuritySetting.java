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
    public Instant getUpdatedAt() { return updatedAt; }

    public void setSsoEnabled(boolean ssoEnabled) { this.ssoEnabled = ssoEnabled; }
    public void setSsoIssuerUri(String ssoIssuerUri) { this.ssoIssuerUri = ssoIssuerUri; }
    public void setSsoClientId(String ssoClientId) { this.ssoClientId = ssoClientId; }
    public void setSsoScopes(String ssoScopes) { this.ssoScopes = ssoScopes; }
    public void setSsoUsernameClaim(String ssoUsernameClaim) { this.ssoUsernameClaim = ssoUsernameClaim; }
    public void setInternalPasswordHash(String internalPasswordHash) { this.internalPasswordHash = internalPasswordHash; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
