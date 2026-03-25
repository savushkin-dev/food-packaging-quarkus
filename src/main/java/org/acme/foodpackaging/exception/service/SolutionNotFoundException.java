package org.acme.foodpackaging.exception.service;

public class SolutionNotFoundException extends PackagingException {
    public SolutionNotFoundException(String message) {
        super(message, 404);
    }
}
