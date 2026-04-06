package org.acme.foodpackaging.exception.service;

public class SolutionNotFoundException extends PackagingException {

    private static final int STATUS = 404;

    public SolutionNotFoundException(String message) {
        super(message, STATUS);
    }

    public SolutionNotFoundException(String message, Throwable cause) {
        super(message, STATUS, cause);
    }

    public SolutionNotFoundException(Throwable cause) {
        super(STATUS, cause);
    }
}

