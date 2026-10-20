package com.housekeeping.management.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class FlywayResetConfiguration {

    @Bean
    FlywayMigrationStrategy cleanAndMigrateOnStartup(Environment environment) {
        return (Flyway flyway) -> {
            if (environment.getProperty("app.database.reset-on-startup", Boolean.class, false)) {
                flyway.clean();
            }
            flyway.migrate();
        };
    }
}
