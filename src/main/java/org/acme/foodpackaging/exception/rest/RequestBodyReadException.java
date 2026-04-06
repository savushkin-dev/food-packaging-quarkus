package org.acme.foodpackaging.exception.rest;

public class RequestBodyReadException extends RuntimeException {
    public RequestBodyReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
