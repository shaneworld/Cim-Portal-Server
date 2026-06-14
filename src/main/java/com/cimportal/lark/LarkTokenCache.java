package com.cimportal.lark;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide TTL cache of Lark tenant_access_tokens, keyed by baseUrl + appId.
 * Mirrors {@code OnDutyCache}. Failures are not cached. TTL is the token's own
 * expiry minus a 60s safety margin, clamped to [60s, 7200s].
 */
@Component
public class LarkTokenCache {

    private record Entry(Optional<String> token, Instant expiresAt) { }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final Clock clock;
    private final LarkClient client;

    public LarkTokenCache(Clock clock, LarkClient client) {
        this.clock = clock;
        this.client = client;
    }

    public Optional<String> get(String baseUrl, String appId, String appSecret) {
        Instant now = clock.instant();
        String key = baseUrl + "|" + appId;
        Entry e = cache.get(key);
        if (e != null && e.expiresAt().isAfter(now)) {
            return e.token();
        }
        Optional<LarkClient.TokenInfo> info = client.tenantAccessToken(baseUrl, appId, appSecret);
        if (info.isEmpty()) {
            return Optional.empty(); // do not cache failures
        }
        long ttl = Math.min(Math.max(60, info.get().expireSeconds() - 60), 7200);
        cache.put(key, new Entry(Optional.of(info.get().token()), now.plusSeconds(ttl)));
        return Optional.of(info.get().token());
    }

    public void clear() {
        cache.clear();
    }
}
