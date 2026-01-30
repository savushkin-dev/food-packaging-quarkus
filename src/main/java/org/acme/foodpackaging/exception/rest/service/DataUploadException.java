package org.acme.foodpackaging.exception.rest.service;

public class DataUploadException extends RuntimeException {
    public DataUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}

