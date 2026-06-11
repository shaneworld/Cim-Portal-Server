package com.cimportal.setting;

import com.cimportal.setting.dto.AdminSettingView;
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

    /** In-memory cache of the single row. Refreshed on write. */
    private volatile SecuritySetting cached;

    public SecuritySettingService(SecuritySettingRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
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
        return new PublicConfig(
            s.isSsoEnabled(),
            s.getSsoIssuerUri(),
            s.getSsoClientId(),
            s.getSsoScopes(),
            s.getSsoUsernameClaim(),
            s.isAnnouncementsEnabled(),
            s.isDutyLinesEnabled()
        );
    }

    public AdminSettingView adminView() {
        SecuritySetting s = get();
        return new AdminSettingView(
            s.isSsoEnabled(),
            s.getSsoIssuerUri(),
            s.getSsoClientId(),
            s.getSsoScopes(),
            s.getSsoUsernameClaim(),
            s.isAnnouncementsEnabled(),
            s.isDutyLinesEnabled(),
            s.getUpdatedAt()
        );
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
        if (req.announcementsEnabled() != null) s.setAnnouncementsEnabled(req.announcementsEnabled());
        if (req.dutyLinesEnabled() != null) s.setDutyLinesEnabled(req.dutyLinesEnabled());
        s.setUpdatedAt(Instant.now());
        SecuritySetting saved = repo.save(s);

        // invalidate / refresh cache
        synchronized (this) {
            cached = saved;
        }

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
