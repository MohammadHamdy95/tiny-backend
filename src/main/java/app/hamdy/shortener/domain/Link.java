package app.hamdy.shortener.domain;

import java.time.Instant;

/** A short link. expiresAt is always set (default 5 years). */
public record Link(
        String slug,
        String url,
        Instant createdAt,
        Instant expiresAt,
        long clicks,
        Instant lastClickAt) {

    public boolean expired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
