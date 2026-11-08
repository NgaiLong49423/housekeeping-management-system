package com.housekeeping.management;

import com.housekeeping.management.config.SqlServerDatabaseInitializer;
import com.housekeeping.management.config.VietmapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
@EntityScan("com.housekeeping.management.entity")
@EnableConfigurationProperties(VietmapProperties.class)
public class HousekeepingManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(HousekeepingManagementSystemApplication.class);
        application.addInitializers(new SqlServerDatabaseInitializer());
        application.run(args);
    }

}
