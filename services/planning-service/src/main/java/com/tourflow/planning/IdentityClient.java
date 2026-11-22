package com.tourflow.planning;

import com.tourflow.common.web.InternalTokenFilter;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Предпочтения туриста/менеджера для профиля подбора по умолчанию (необязательная интеграция: при сбое — BALANCE). */
@Component
public class IdentityClient {
    private final RestClient rest;

    public IdentityClient(@Value("${app.identity-url}") String url, @Value("${app.internal-token}") String token) {
        this.rest = RestClient.builder().baseUrl(url).defaultHeader(InternalTokenFilter.HEADER, token).build();
    }

    public String defaultProfile(UUID userId) {
        try {
            Map<String, Object> m = rest.get().uri("/internal/users/{id}/profile", userId).retrieve().body(new ParameterizedTypeReference<>() {});
            Object p = m == null ? null : m.get("profile");
            return p == null ? "BALANCE" : p.toString();
        } catch (Exception e) { return "BALANCE"; }
    }
}
