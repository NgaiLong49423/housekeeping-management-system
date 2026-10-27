package com.housekeeping.management.service;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serializes the unique email check and the caller's insert in one SQL Server transaction.
 * Callers must persist the canonical email supplied to the callback.
 */
@Service
public class GlobalEmailUniquenessService {
    private static final String USER_EMAIL_LOCK = """
            SELECT TOP (1) user_id FROM dbo.Users
            WITH (UPDLOCK, HOLDLOCK, INDEX(UQ_Users_Email))
            WHERE email = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public GlobalEmailUniquenessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public <T> T executeWithUniqueEmail(String submittedEmail, Function<String, T> persistCanonicalEmail) {
        String canonicalEmail = canonicalize(submittedEmail);

        // Lock the unique index key range so concurrent role registrations cannot duplicate the email.
        if (exists(USER_EMAIL_LOCK, canonicalEmail)) {
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
