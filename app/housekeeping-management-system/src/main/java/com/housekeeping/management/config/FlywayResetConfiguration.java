package com.housekeeping.management.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FlywayResetConfiguration {

    @Bean
    FlywayMigrationStrategy migrateOnStartup() {
        return Flyway::migrate;
    }
}
