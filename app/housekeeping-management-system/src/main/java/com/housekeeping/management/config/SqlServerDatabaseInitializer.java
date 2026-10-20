package com.housekeeping.management.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Creates the configured local SQL Server database before the application DataSource is initialized. */
public final class SqlServerDatabaseInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Pattern DATABASE_NAME = Pattern.compile("(?i)(?:^|;)databaseName=([^;]+)");
    private static final Pattern DATABASE_NAME_PROPERTY = Pattern.compile("(?i);databaseName=[^;]*");
    private static final Set<String> SYSTEM_DATABASES = Set.of("master", "model", "msdb", "tempdb");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment environment = context.getEnvironment();
        if (!environment.getProperty("app.database.reset-on-startup", Boolean.class, false)) {
            return;
        }

        String jdbcUrl = environment.getProperty("spring.datasource.url");
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:sqlserver:")) {
            return;
        }

        Matcher databaseMatcher = DATABASE_NAME.matcher(jdbcUrl);
        if (!databaseMatcher.find()) {
            throw new IllegalStateException("SQL Server DB_URL must include databaseName=<database>.");
        }

        String databaseName = databaseMatcher.group(1).trim();
        if (databaseName.isEmpty() || SYSTEM_DATABASES.contains(databaseName.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Refusing to initialize or reset a SQL Server system database.");
        }

        String masterUrl = DATABASE_NAME_PROPERTY.matcher(jdbcUrl).replaceFirst(";databaseName=master");
        if (masterUrl.equals(jdbcUrl)) {
            masterUrl = jdbcUrl + ";databaseName=master";
        }

        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        try (var connection = username == null || username.isBlank()
                ? DriverManager.getConnection(masterUrl)
                : DriverManager.getConnection(masterUrl, username, password);
             PreparedStatement databaseExists = connection.prepareStatement("SELECT DB_ID(?)")) {
            databaseExists.setString(1, databaseName);
            try (var result = databaseExists.executeQuery()) {
                if (result.next() && result.getObject(1) == null) {
                    result.close();
                    try (Statement createDatabase = connection.createStatement()) {
                        createDatabase.execute("CREATE DATABASE [" + databaseName.replace("]", "]]") + "]");
                    }
                }
            }
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Could not ensure the configured SQL Server database exists. Check SQL Server availability and the login's CREATE DATABASE permission.",
                    failure);
        }
    }
}
