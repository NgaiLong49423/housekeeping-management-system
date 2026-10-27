package com.housekeeping.management;

import com.housekeeping.management.config.SqlServerDatabaseInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
@EntityScan("com.housekeeping.management.entity")
public class HousekeepingManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(HousekeepingManagementSystemApplication.class);
        application.addInitializers(new SqlServerDatabaseInitializer());
        application.run(args);
    }

}
