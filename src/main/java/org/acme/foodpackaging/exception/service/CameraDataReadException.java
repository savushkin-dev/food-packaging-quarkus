
package org.acme.foodpackaging.exception.service;

import java.sql.SQLException;

public class CameraDataReadException extends SQLException {

    public CameraDataReadException(String message, Throwable cause) {
        super(message, cause);
    }
}