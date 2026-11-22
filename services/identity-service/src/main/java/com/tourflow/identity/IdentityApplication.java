package com.tourflow.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.tourflow.common", "com.tourflow.identity"})
@EntityScan({"com.tourflow.common", "com.tourflow.identity"})
@EnableJpaRepositories(basePackages = {"com.tourflow.common", "com.tourflow.identity"})
public class IdentityApplication {
    public static void main(String[] args) { SpringApplication.run(IdentityApplication.class, args); }
}