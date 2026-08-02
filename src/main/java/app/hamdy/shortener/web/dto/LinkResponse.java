package app.hamdy.shortener.web.dto;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import app.hamdy.shortener.domain.Link;

public record LinkResponse(
        String slug,
        String url,
        String shortUrl,
        Instant createdAt,
        Instant expiresAt,
        Stats stats) {

    public record Stats(long clicks, Instant lastClickAt) {
    }

    /** Builds responses with the configured public base URL. */
    @Component
    public static class Factory {

        private final String publicUrl;

        public Factory(@Value("${shortener.public-url}") String publicUrl) {
            this.publicUrl = publicUrl.endsWith("/")
                    ? publicUrl.substring(0, publicUrl.length() - 1)
                    : publicUrl;
        }

        public LinkResponse from(Link link) {
            return new LinkResponse(link.slug(), link.url(),
                    publicUrl + "/" + link.slug(), link.createdAt(), link.expiresAt(),
                    new Stats(link.clicks(), link.lastClickAt()));
        }
    }
}
