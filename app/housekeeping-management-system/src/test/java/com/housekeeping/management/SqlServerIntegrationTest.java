package com.housekeeping.management;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Tag("sqlserver")
public abstract class SqlServerIntegrationTest {
    private static final String TEST_DATABASE = "home_cleaning_acceptance";
    static final MSSQLServerContainer SQL_SERVER = new MSSQLServerContainer(
            "mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04")
            .acceptLicense();

    static {
        SQL_SERVER.start();
        createEmptyTestDatabase();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> SQL_SERVER.getJdbcUrl() + ";databaseName=" + TEST_DATABASE);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("app.evidence.storage-root", () ->
                Path.of(System.getProperty("java.io.tmpdir"), "hsf302-test-evidence").toString());
    }

    private static void createEmptyTestDatabase() {
        try (var connection = DriverManager.getConnection(
                SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + TEST_DATABASE);
        } catch (SQLException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
