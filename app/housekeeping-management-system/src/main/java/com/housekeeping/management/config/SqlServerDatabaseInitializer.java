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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Recreates the approved disposable local database before the DataSource is initialized. */
public final class SqlServerDatabaseInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(SqlServerDatabaseInitializer.class);
    private static final Pattern LOCAL_SERVER = Pattern.compile(
            "(?i)^jdbc:sqlserver://(?:localhost|127\\.0\\.0\\.1|\\[::1\\])(?=[:;\\\\]|$)");
    private static final Pattern DATABASE_NAME = Pattern.compile("(?i)(?:^|;)databaseName=([^;]+)");
    private static final Pattern DATABASE_NAME_PROPERTY = Pattern.compile("(?i);databaseName=[^;]*");
    private static final Set<String> RESET_DATABASES = Set.of("housekeeping_database", "home_cleaning_demo");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment environment = context.getEnvironment();
        if (!environment.getProperty("app.database.reset-on-startup", Boolean.class, false)) {
            return;
        }

        String jdbcUrl = environment.getProperty("spring.datasource.url");
        if (jdbcUrl == null || !LOCAL_SERVER.matcher(jdbcUrl).find()) {
            throw new IllegalStateException("Database reset is restricted to a local SQL Server. Set DB_RESET_ON_STARTUP=false to preserve data.");
        }

        Matcher databaseMatcher = DATABASE_NAME.matcher(jdbcUrl);
        if (!databaseMatcher.find()) {
            throw new IllegalStateException("SQL Server DB_URL must include databaseName=<database>.");
        }

        String databaseName = databaseMatcher.group(1).trim();
        if (!RESET_DATABASES.contains(databaseName.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Database reset is restricted to Housekeeping_Database or home_cleaning_demo.");
        }
        if (databaseMatcher.find()) {
            throw new IllegalStateException("DB_URL must contain exactly one databaseName property.");
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
            boolean exists;
            try (var result = databaseExists.executeQuery()) {
                exists = result.next() && result.getObject(1) != null;
            }
            log.warn("DESTRUCTIVE LOCAL RESET: recreating database {}. All existing data will be deleted.", databaseName);
            String quotedName = "[" + databaseName.replace("]", "]]") + "]";
            try (Statement statement = connection.createStatement()) {
                if (exists) {
                    statement.execute("ALTER DATABASE " + quotedName + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                    statement.execute("DROP DATABASE " + quotedName);
                }
                statement.execute("CREATE DATABASE " + quotedName);
            }
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Could not recreate the local SQL Server database. Check SQL Server availability and the login's ALTER/DROP/CREATE DATABASE permissions.",
                    failure);
        }
    }
}
