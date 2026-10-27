package com.housekeeping.management.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SqlServerDatabaseInitializerTest {
    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=Housekeeping_Database";

    @Test
    void disabledResetDoesNotConnect() {
        try (var drivers = mockStatic(DriverManager.class)) {
            initialize("jdbc:sqlserver://remote;databaseName=master", false);
            drivers.verifyNoInteractions();
        }
    }

    @Test
    void rejectsRemoteSystemAndUnapprovedDatabasesBeforeConnecting() {
        try (var drivers = mockStatic(DriverManager.class)) {
            for (String url : new String[]{
                    "jdbc:sqlserver://remote;databaseName=Housekeeping_Database",
                    "jdbc:sqlserver://localhost.evil;databaseName=Housekeeping_Database",
                    "jdbc:sqlserver://localhost;databaseName=master",
                    "jdbc:sqlserver://localhost;databaseName=another_project",
                    URL + ";databaseName=master",
                    "jdbc:sqlserver://localhost"}) {
                assertThatThrownBy(() -> initialize(url, true)).isInstanceOf(IllegalStateException.class);
            }
            drivers.verifyNoInteractions();
        }
    }

    @Test
    void dropsExistingDatabaseBeforeCreatingFreshDatabase() throws Exception {
        verifyReset(true);
    }

    @Test
    void createsMissingDatabaseWithoutTryingToDropIt() throws Exception {
        verifyReset(false);
    }

    private void verifyReset(boolean exists) throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement query = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        Statement statement = mock(Statement.class);
        when(connection.prepareStatement("SELECT DB_ID(?)")).thenReturn(query);
        when(query.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getObject(1)).thenReturn(exists ? 5 : null);
        when(connection.createStatement()).thenReturn(statement);
        try (var drivers = mockStatic(DriverManager.class)) {
            drivers.when(() -> DriverManager.getConnection(
                    "jdbc:sqlserver://localhost:1433;databaseName=master", "test-user", "test-password"))
                    .thenReturn(connection);
            initialize(URL, true);
        }
        var order = inOrder(statement);
        if (exists) {
            order.verify(statement).execute("ALTER DATABASE [Housekeeping_Database] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
            order.verify(statement).execute("DROP DATABASE [Housekeeping_Database]");
        }
        order.verify(statement).execute("CREATE DATABASE [Housekeeping_Database]");
        verify(statement, times(exists ? 3 : 1)).execute(anyString());
    }

    private void initialize(String url, boolean reset) {
        try (var context = new GenericApplicationContext()) {
            context.setEnvironment(new MockEnvironment()
                    .withProperty("app.database.reset-on-startup", Boolean.toString(reset))
                    .withProperty("spring.datasource.url", url)
                    .withProperty("spring.datasource.username", "test-user")
                    .withProperty("spring.datasource.password", "test-password"));
            new SqlServerDatabaseInitializer().initialize(context);
        }
    }
}
