package com.housekeeping.management.service;

import java.util.Locale;
import java.util.function.Function;

public interface GlobalEmailUniquenessService {

    public <T> T executeWithUniqueEmail(String submittedEmail, Function<String, T> persistCanonicalEmail);
    static String canonicalize(String email) { if (email == null) throw new IllegalArgumentException("Email is required"); return email.trim().toLowerCase(Locale.ROOT); }
}
