package org.acme.foodpackaging.exception;

public class UploadDataException extends RuntimeException {
    public UploadDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
