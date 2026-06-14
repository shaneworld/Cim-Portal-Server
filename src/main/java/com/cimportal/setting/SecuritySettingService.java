package com.cimportal.setting;

import com.cimportal.lark.LarkTokenCache;
import com.cimportal.setting.dto.AdminSettingView;
import com.cimportal.setting.dto.LarkSettingsUpdateRequest;
import com.cimportal.setting.dto.LarkSettingsView;
import com.cimportal.setting.dto.PublicConfig;
import com.cimportal.setting.dto.SecuritySettingUpdateRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SecuritySettingService {

    private static final long SINGLETON_ID = 1L;

    private final SecuritySettingRepository repo;
    private final PasswordEncoder encoder;
    private final LarkTokenCache larkTokenCache;

    /** In-memory cache of the single row. Refreshed on write. */
    private volatile SecuritySetting cached;

    public SecuritySettingService(SecuritySettingRepository repo, PasswordEncoder encoder,
                                  LarkTokenCache larkTokenCache) {
        this.repo = repo;
        this.encoder = encoder;
        this.larkTokenCache = larkTokenCache;
    }

    /** Load from cache or DB. */
    public SecuritySetting get() {
        if (cached == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = repo.findById(SINGLETON_ID)
                        .orElseThrow(() -> new IllegalStateException("security_setting row missing"));
                }
            }
        }
        return cached;
    }

    public PublicConfig publicView() {
        SecuritySetting s = get();
        boolean larkEnabled = nb(s.getLarkAppId()) && nb(s.getLarkAppSecret()) && nb(s.getLarkReceiverId());
        return new PublicConfig(
            s.isSsoEnabled(),
            s.getSsoIssuerUri(),
            s.getSsoClientId(),
            s.getSsoScopes(),
            s.getSsoUsernameClaim(),
            s.isInfoPanelEnabled(),
            s.isHeroEnabled(),
            larkEnabled
        );
    }

    /** Non-blank helper. */
    private static boolean nb(String x) {
        return x != null && !x.isBlank();
    }

    public AdminSettingView adminView() {
        SecuritySetting s = get();
        return new AdminSettingView(
            s.isSsoEnabled(),
            s.getSsoIssuerUri(),
            s.getSsoClientId(),
            s.getSsoScopes(),
            s.getSsoUsernameClaim(),
            s.isInfoPanelEnabled(),
            s.isHeroEnabled(),
            s.getDutyApiBaseUrl(),
            s.getDutyApiKey() != null && !s.getDutyApiKey().isBlank(),
            s.getLarkBaseUrl(),
            s.getLarkAppId(),
            s.getLarkReceiverId(),
            s.getLarkReceiverIdType(),
            s.getLarkAppSecret() != null && !s.getLarkAppSecret().isBlank(),
            s.getUpdatedAt()
        );
    }

    public LarkSettingsView larkSettingsView() {
        SecuritySetting s = get();
        return new LarkSettingsView(
            s.getLarkBaseUrl(),
            s.getLarkAppId(),
            s.getLarkReceiverId(),
            s.getLarkReceiverIdType(),
            nb(s.getLarkAppSecret())
        );
    }

    @Transactional
    public LarkSettingsView updateLarkSettings(LarkSettingsUpdateRequest req) {
        SecuritySetting s = repo.findById(SINGLETON_ID)
            .orElseThrow(() -> new IllegalStateException("security_setting row missing"));

        if (req.larkBaseUrl() != null) s.setLarkBaseUrl(req.larkBaseUrl().isBlank() ? null : req.larkBaseUrl());
        if (req.larkAppId() != null) s.setLarkAppId(req.larkAppId().isBlank() ? null : req.larkAppId());
        if (req.larkReceiverId() != null) s.setLarkReceiverId(req.larkReceiverId().isBlank() ? null : req.larkReceiverId());
        if (req.larkReceiverIdType() != null) s.setLarkReceiverIdType(req.larkReceiverIdType().isBlank() ? null : req.larkReceiverIdType());
        // null = keep existing secret; blank = clear
        if (req.larkAppSecret() != null) s.setLarkAppSecret(req.larkAppSecret().isBlank() ? null : req.larkAppSecret());
        s.setUpdatedAt(Instant.now());
        SecuritySetting saved = repo.save(s);

        synchronized (this) {
            cached = saved;
        }
        larkTokenCache.clear();

        return larkSettingsView();
    }

    @Transactional
    public AdminSettingView update(SecuritySettingUpdateRequest req) {
        SecuritySetting s = repo.findById(SINGLETON_ID)
            .orElseThrow(() -> new IllegalStateException("security_setting row missing"));

        if (req.ssoEnabled() != null)  s.setSsoEnabled(req.ssoEnabled());
        if (req.issuerUri() != null)   s.setSsoIssuerUri(req.issuerUri().isBlank() ? null : req.issuerUri());
        if (req.clientId() != null)    s.setSsoClientId(req.clientId().isBlank() ? null : req.clientId());
        if (req.scopes() != null && !req.scopes().isBlank()) s.setSsoScopes(req.scopes());
        if (req.usernameClaim() != null && !req.usernameClaim().isBlank()) s.setSsoUsernameClaim(req.usernameClaim());
        if (req.initialPassword() != null && !req.initialPassword().isBlank()) {
            s.setInternalPasswordHash(encoder.encode(req.initialPassword()));
        }
        if (req.infoPanelEnabled() != null) s.setInfoPanelEnabled(req.infoPanelEnabled());
        if (req.heroEnabled() != null) s.setHeroEnabled(req.heroEnabled());
        if (req.dutyApiBaseUrl() != null) s.setDutyApiBaseUrl(req.dutyApiBaseUrl().isBlank() ? null : req.dutyApiBaseUrl());
        // null = keep existing key; blank = clear
        if (req.dutyApiKey() != null) s.setDutyApiKey(req.dutyApiKey().isBlank() ? null : req.dutyApiKey());
        if (req.larkBaseUrl() != null) s.setLarkBaseUrl(req.larkBaseUrl().isBlank() ? null : req.larkBaseUrl());
        if (req.larkAppId() != null) s.setLarkAppId(req.larkAppId().isBlank() ? null : req.larkAppId());
        if (req.larkReceiverId() != null) s.setLarkReceiverId(req.larkReceiverId().isBlank() ? null : req.larkReceiverId());
        if (req.larkReceiverIdType() != null) s.setLarkReceiverIdType(req.larkReceiverIdType().isBlank() ? null : req.larkReceiverIdType());
        // null = keep existing secret; blank = clear
        if (req.larkAppSecret() != null) s.setLarkAppSecret(req.larkAppSecret().isBlank() ? null : req.larkAppSecret());
        s.setUpdatedAt(Instant.now());
        SecuritySetting saved = repo.save(s);

        // invalidate / refresh cache
        synchronized (this) {
            cached = saved;
        }
        // drop any cached Lark token in case credentials changed
        larkTokenCache.clear();

        return adminView();
    }

    /** Evicts the in-memory cache — useful in tests between scenarios. */
    public synchronized void invalidateCache() {
        cached = null;
    }

    /** Returns the current SSO issuer URI (may be null). */
    public String ssoIssuer() {
        return get().getSsoIssuerUri();
    }

    /** Checks raw password against the stored internal hash. */
    public boolean matchesInternalPassword(String raw) {
        String hash = get().getInternalPasswordHash();
        return hash != null && encoder.matches(raw, hash);
    }
}
