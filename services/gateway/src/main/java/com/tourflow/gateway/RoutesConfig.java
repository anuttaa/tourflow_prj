package com.tourflow.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Единая точка входа клиента (ТЗ 8.3): /internal/** наружу не маршрутизируется. */
@Configuration
public class RoutesConfig {
    @Bean
    RouteLocator routes(RouteLocatorBuilder b, @Value("${app.upstream.identity}") String identity, @Value("${app.upstream.catalog}") String catalog,
                        @Value("${app.upstream.planning}") String planning, @Value("${app.upstream.booking}") String booking) {
        return b.routes()
                .route("identity", r -> r.path("/api/v1/auth/**", "/api/v1/users/**", "/api/v1/profile/**", "/api/v1/audit/**").uri(identity))
                .route("catalog", r -> r.path("/api/v1/catalog/**").uri(catalog))
                .route("booking-reports", r -> r.path("/api/v1/reports/admin/bookings").uri(booking))
                .route("planning", r -> r.path("/api/v1/tours/**", "/api/v1/public/**", "/api/v1/reports/**", "/api/v1/rules/**").uri(planning))
                .route("booking", r -> r.path("/api/v1/bookings/**").uri(booking))
                .build();
    }
}
