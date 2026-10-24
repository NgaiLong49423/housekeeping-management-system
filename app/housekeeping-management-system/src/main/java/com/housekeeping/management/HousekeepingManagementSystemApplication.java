package com.housekeeping.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
@EntityScan("com.housekeeping.management.entity")
public class HousekeepingManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(HousekeepingManagementSystemApplication.class, args);
    }

}
