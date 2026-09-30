package org.acme.foodpackaging.exception.materials;

public class OneCSyncException extends RuntimeException {
    public OneCSyncException(String message) {
        super(message);
    }

    public OneCSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
