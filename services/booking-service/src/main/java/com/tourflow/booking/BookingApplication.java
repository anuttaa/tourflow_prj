package com.tourflow.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.tourflow.common", "com.tourflow.booking"})
@EntityScan({"com.tourflow.common", "com.tourflow.booking"})
@EnableJpaRepositories(basePackages = {"com.tourflow.common", "com.tourflow.booking"})
public class BookingApplication {
    public static void main(String[] args) { SpringApplication.run(BookingApplication.class, args); }
}