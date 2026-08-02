package app.hamdy.shortener.service;

public class SlugTakenException extends RuntimeException {

    public SlugTakenException(String slug) {
        super("Slug already in use or reserved: " + slug);
    }
}
