package org.acme.foodpackaging.exception.service;

public class SolutionParsingException extends PackagingException{
    public SolutionParsingException(String message, Throwable cause) {
        super(message, 500, cause);
    }
}
