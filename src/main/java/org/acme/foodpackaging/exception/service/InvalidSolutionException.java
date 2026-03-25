package org.acme.foodpackaging.exception.service;

public class InvalidSolutionException extends PackagingException {
    public InvalidSolutionException(String message) {
        super(message, 400);
    }
}
