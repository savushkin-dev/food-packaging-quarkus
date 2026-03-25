package org.acme.foodpackaging.exception.service;

public class SolutionParsingException extends PackagingException {

    private static final int STATUS = 500;

    public SolutionParsingException(String message) {
        super(message, STATUS);
    }

    public SolutionParsingException(String message, Throwable cause) {
        super(message, STATUS, cause);
    }

    public SolutionParsingException(Throwable cause) {
        super(STATUS, cause);
    }
}

