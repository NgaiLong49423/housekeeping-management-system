package com.housekeeping.management;

import com.housekeeping.management.config.SqlServerDatabaseInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HousekeepingManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(HousekeepingManagementSystemApplication.class);
        application.addInitializers(new SqlServerDatabaseInitializer());
        application.run(args);
    }

}
