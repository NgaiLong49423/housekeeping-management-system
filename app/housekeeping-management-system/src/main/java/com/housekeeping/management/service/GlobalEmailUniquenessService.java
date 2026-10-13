package com.housekeeping.management.service;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serializes cross-table email checks and the caller's insert in one SQL Server transaction.
 * Callers must persist the canonical email supplied to the callback.
 */
@Service
public class GlobalEmailUniquenessService {
    private static final String CUSTOMER_EMAIL_LOCK = """
            SELECT TOP (1) customer_id FROM Customer
            WITH (UPDLOCK, HOLDLOCK, INDEX(UQ_Customer_Email))
            WHERE email = ?
            """;
    private static final String STAFF_EMAIL_LOCK = """
            SELECT TOP (1) staff_id FROM Staff
            WITH (UPDLOCK, HOLDLOCK, INDEX(UQ_Staff_Email))
            WHERE email = ?
            """;
    private static final String ADMIN_EMAIL_LOCK = """
            SELECT TOP (1) admin_id FROM Admin
            WITH (UPDLOCK, HOLDLOCK, INDEX(UQ_Admin_Email))
            WHERE email = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public GlobalEmailUniquenessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public <T> T executeWithUniqueEmail(String submittedEmail, Function<String, T> persistCanonicalEmail) {
        String canonicalEmail = canonicalize(submittedEmail);

        // Always lock each table in the same order, including empty key ranges, before inserting.
        boolean customerExists = exists(CUSTOMER_EMAIL_LOCK, canonicalEmail);
        boolean staffExists = exists(STAFF_EMAIL_LOCK, canonicalEmail);
        boolean adminExists = exists(ADMIN_EMAIL_LOCK, canonicalEmail);
        if (customerExists || staffExists || adminExists) {
            throw new DuplicateEmailException(canonicalEmail);
        }

        return persistCanonicalEmail.apply(canonicalEmail);
    }

    public static String canonicalize(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean exists(String sql, String canonicalEmail) {
        List<Long> matches = jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> resultSet.getLong(1),
                canonicalEmail);
        return !matches.isEmpty();
    }
}
