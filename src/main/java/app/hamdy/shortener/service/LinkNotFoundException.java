package app.hamdy.shortener.service;

public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException(String slug) {
        super("No such link: " + slug);
    }
}
