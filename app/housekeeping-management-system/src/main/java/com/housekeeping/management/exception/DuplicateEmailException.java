package com.housekeeping.management.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String canonicalEmail) {
        super("Email is already registered: " + canonicalEmail);
    }
}
