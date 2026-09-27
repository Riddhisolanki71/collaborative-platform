package com.collabplatform.exception;

/** Thrown for state conflicts, e.g. duplicate email or a stale optimistic-lock version (409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
