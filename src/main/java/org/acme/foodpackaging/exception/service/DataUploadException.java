package org.acme.foodpackaging.exception.service;

/**
 * Thrown when data upload or persistence operations fail (e.g. MS_LOG insert/update).
 */
public class DataUploadException extends RuntimeException {

    public DataUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
