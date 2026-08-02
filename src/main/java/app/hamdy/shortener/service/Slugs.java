package app.hamdy.shortener.service;

import java.security.SecureRandom;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Slug rules. Generated slugs are 7-char base62 (~3.5 x 10^12 space);
 * custom slugs must match the shared pattern and avoid the reserved
 * words that collide with real paths on s.hamdy.app (UI + API share
 * the host — see docs/API.md).
 */
public final class Slugs {

    public static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    static final int GENERATED_LENGTH = 7;
    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Set<String> RESERVED = Set.of(
            "api", "assets", "static", "index.html", "favicon.ico",
            "robots.txt", "sitemap.xml", "health", "admin", "login");

    private Slugs() {
    }

    public static String generate() {
        char[] out = new char[GENERATED_LENGTH];
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            out[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }

    public static boolean reserved(String slug) {
        return RESERVED.contains(slug.toLowerCase());
    }
}
