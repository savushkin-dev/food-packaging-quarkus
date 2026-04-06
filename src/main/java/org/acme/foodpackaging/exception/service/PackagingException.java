package org.acme.foodpackaging.exception.service;

import lombok.Getter;

@Getter
public abstract class PackagingException extends RuntimeException {

    private final int status;

    protected PackagingException(String message, int status) {
        super(message);
        this.status = status;
    }

    protected PackagingException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    protected PackagingException(int status, Throwable cause) {
        super(cause);
        this.status = status;
    }

}

