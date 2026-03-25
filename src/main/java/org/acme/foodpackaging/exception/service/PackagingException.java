package org.acme.foodpackaging.exception.service;

public abstract class PackagingException extends RuntimeException {

    private final int status;

    protected PackagingException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
