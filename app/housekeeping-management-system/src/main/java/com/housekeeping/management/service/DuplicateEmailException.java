package com.housekeeping.management.service;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String canonicalEmail) {
        super("Email is already registered: " + canonicalEmail);
    }
}
