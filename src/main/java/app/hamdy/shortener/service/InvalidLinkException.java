package app.hamdy.shortener.service;

public class InvalidLinkException extends RuntimeException {

    public InvalidLinkException(String message) {
        super(message);
    }
}
