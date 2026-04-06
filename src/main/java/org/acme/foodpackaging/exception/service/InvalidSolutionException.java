package org.acme.foodpackaging.exception.service;

public class InvalidSolutionException extends PackagingException {

    private static final int STATUS = 400;

    public InvalidSolutionException(String message) {
        super(message, STATUS);
    }

    public InvalidSolutionException(String message, Throwable cause) {
        super(message, STATUS, cause);
    }

    public InvalidSolutionException(Throwable cause) {
        super(STATUS, cause);
    }
}

