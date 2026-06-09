
package org.acme.foodpackaging.exception.service;

public class CameraDataReadException extends RuntimeException {

    public CameraDataReadException(String message, Throwable cause) {
        super(message, cause);
    }
}