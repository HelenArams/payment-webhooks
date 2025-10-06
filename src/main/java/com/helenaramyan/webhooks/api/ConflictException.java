package com.helenaramyan.webhooks.api;

/** The request is well-formed but the resource is not in a state that allows it (409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
