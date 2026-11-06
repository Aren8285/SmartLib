package com.smartlib.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory fixed-window rate limiter for login/register attempts.
 * Per-process only, which is enough for the single-instance demo deployment;
 * a distributed deployment would move the buckets to Redis.
 */
@Component
public class LoginRateLimiter {

    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final boolean enabled;
    private final int maxAttempts;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.attempts-per-minute:10}") int maxAttempts) {
        this.enabled = enabled;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /**
     * @return 0 when the request is allowed, otherwise the number of seconds
     *         until the window resets (used for Retry-After).
     */
    public long retryAfterSeconds(String key) {
        if (!enabled) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (buckets.size() > MAX_TRACKED_KEYS) {
            buckets.entrySet().removeIf(entry -> now - entry.getValue().windowStart > 5 * WINDOW_MS);
        }
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(now));
        synchronized (bucket) {
            if (now - bucket.windowStart >= WINDOW_MS) {
                bucket.windowStart = now;
                bucket.count = 0;
            }
            bucket.count++;
            if (bucket.count > maxAttempts) {
                return Math.max(1, (WINDOW_MS - (now - bucket.windowStart)) / 1000);
            }
            return 0;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static class Bucket {
        private long windowStart;
        private int count;

        Bucket(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
