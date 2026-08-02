package app.hamdy.shortener.web.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateLinkRequest(
        @NotNull @Size(max = 2048) String url,
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String slug,
        Instant expiresAt) {
}
