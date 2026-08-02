package app.hamdy.shortener.service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import app.hamdy.shortener.domain.Link;
import app.hamdy.shortener.domain.LinkRepository;

@Service
public class LinkService {

    private static final int MAX_SLUG_ATTEMPTS = 3;

    private final LinkRepository repository;
    private final Duration defaultTtl;

    public LinkService(LinkRepository repository,
            @Value("${shortener.default-ttl-days}") long defaultTtlDays) {
        this.repository = repository;
        this.defaultTtl = Duration.ofDays(defaultTtlDays);
    }

    public Link create(String url, String customSlug, Instant expiresAt) {
        validateUrl(url);
        Instant now = Instant.now();
        Instant effectiveExpiry = effectiveExpiry(now, expiresAt);

        if (customSlug != null) {
            if (Slugs.reserved(customSlug)) {
                throw new SlugTakenException(customSlug);
            }
            Link link = new Link(customSlug, url, now, effectiveExpiry, 0, null);
            if (!repository.putIfAbsent(link)) {
                throw new SlugTakenException(customSlug);
            }
            return link;
        }

        for (int attempt = 0; attempt < MAX_SLUG_ATTEMPTS; attempt++) {
            Link link = new Link(Slugs.generate(), url, now, effectiveExpiry, 0, null);
            if (repository.putIfAbsent(link)) {
                return link;
            }
        }
        throw new IllegalStateException("Could not allocate a slug after "
                + MAX_SLUG_ATTEMPTS + " attempts");
    }

    /**
     * Resolve a slug for redirecting: expired counts as gone (exact 404
     * even while DynamoDB TTL lags), and a successful hit records the
     * click atomically.
     */
    public Link resolve(String slug) {
        Link link = lookupLive(slug);
        repository.recordClick(slug, Instant.now());
        return link;
    }

    /** Details without counting a click. */
    public Link get(String slug) {
        return lookupLive(slug);
    }

    private Link lookupLive(String slug) {
        Link link = repository.find(slug).orElseThrow(() -> new LinkNotFoundException(slug));
        if (link.expired(Instant.now())) {
            throw new LinkNotFoundException(slug);
        }
        return link;
    }

    private Instant effectiveExpiry(Instant now, Instant requested) {
        Instant defaultExpiry = now.plus(defaultTtl);
        if (requested == null) {
            return defaultExpiry;
        }
        if (!requested.isAfter(now)) {
            throw new InvalidLinkException("expiresAt must be in the future");
        }
        if (requested.isAfter(defaultExpiry)) {
            throw new InvalidLinkException(
                    "expiresAt may not exceed the default TTL (" + defaultTtl.toDays() + " days)");
        }
        return requested;
    }

    private static void validateUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new InvalidLinkException("url is not a valid URI");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))
                || uri.getHost() == null) {
            throw new InvalidLinkException("url must be an absolute http(s) URL");
        }
    }
}
