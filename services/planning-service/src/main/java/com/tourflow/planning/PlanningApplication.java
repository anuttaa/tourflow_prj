package com.tourflow.planning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.tourflow.common", "com.tourflow.planning"})
@EntityScan({"com.tourflow.common", "com.tourflow.planning"})
@EnableJpaRepositories(basePackages = {"com.tourflow.common", "com.tourflow.planning"})
public class PlanningApplication {
    public static void main(String[] args) { SpringApplication.run(PlanningApplication.class, args); }
}