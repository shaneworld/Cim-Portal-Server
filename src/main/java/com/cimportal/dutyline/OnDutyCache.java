package com.cimportal.dutyline;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Process-wide TTL cache of on-duty lookups, keyed by scheduleName. */
@Component
public class OnDutyCache {

    private record Entry(Optional<OnDutyPerson> value, Instant expiresAt) { }

    private static final Duration TTL = Duration.ofMinutes(10);

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final Clock clock;
    private final OnDutyClient client;

    public OnDutyCache(Clock clock, OnDutyClient client) {
        this.clock = clock;
        this.client = client;
    }

    public Optional<OnDutyPerson> get(String baseUrl, String apiKey, String scheduleName) {
        Instant now = clock.instant();
        Entry e = cache.get(scheduleName);
        if (e != null && e.expiresAt().isAfter(now)) {
            return e.value();
        }
        Optional<OnDutyPerson> fresh = client.primaryByScheduleName(baseUrl, apiKey, scheduleName);
        cache.put(scheduleName, new Entry(fresh, now.plus(TTL)));
        return fresh;
    }
}
