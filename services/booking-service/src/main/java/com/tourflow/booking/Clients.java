package com.tourflow.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourflow.common.dto.Contracts.*;
import com.tourflow.common.web.ApiException;
import com.tourflow.common.web.InternalTokenFilter;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

public final class Clients {
    private Clients() {}

    static RestClient build(String url, String token) {
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        f.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().baseUrl(url).requestFactory(f).defaultHeader(InternalTokenFilter.HEADER, token).build();
    }

    /** Перевод ответов 4xx и сетевых сбоев в понятные ошибки; 409 каталога — «места закончились». */
    static <T> T call(Supplier<T> s, ObjectMapper mapper) {
        try { return s.get(); }
        catch (HttpClientErrorException e) {
            String detail = null;
            try { JsonNode n = mapper.readTree(e.getResponseBodyAsString()); detail = n.hasNonNull("detail") ? n.get("detail").asText() : null; } catch (Exception ignored) { }
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) throw ApiException.notFound("Объект");
            if (e.getStatusCode() == HttpStatus.CONFLICT) throw ApiException.conflict(detail != null ? detail : "Конфликт при обращении к каталогу");
            throw ApiException.invalid(detail != null ? detail : "Запрос отклонён: " + e.getStatusCode());
        } catch (RuntimeException e) {
            throw ApiException.unavailable("Сервис временно недоступен, повторите позже");
        }
    }

    @Component
    public static class CatalogApi {
        private final RestClient rest;
        public CatalogApi(@Value("${app.catalog-url}") String url, @Value("${app.internal-token}") String token) { rest = build(url, token); }

        @CircuitBreaker(name = "catalog")
        public void hold(HoldRequest r) { rest.post().uri("/internal/holds").body(r).retrieve().toBodilessEntity(); }
        @CircuitBreaker(name = "catalog")
        public void release(String ref) { rest.delete().uri("/internal/holds/{ref}", ref).retrieve().toBodilessEntity(); }
        @CircuitBreaker(name = "catalog")
        public void convert(String ref) { rest.post().uri("/internal/holds/{ref}/convert", ref).retrieve().toBodilessEntity(); }
    }

    @Component
    public static class PlanningApi {
        private final RestClient rest;
        public PlanningApi(@Value("${app.planning-url}") String url, @Value("${app.internal-token}") String token) { rest = build(url, token); }

        @CircuitBreaker(name = "planning")
        public PublishedSnapshot snapshot(long tourId) { return rest.get().uri("/internal/tours/{id}/published-snapshot", tourId).retrieve().body(PublishedSnapshot.class); }
    }

    @Component
    public static class CatalogClient {
        private final CatalogApi api; private final ObjectMapper mapper;
        public CatalogClient(CatalogApi api, ObjectMapper mapper) { this.api = api; this.mapper = mapper; }
        public void hold(HoldRequest r) { call(() -> { api.hold(r); return null; }, mapper); }
        public void release(String ref) { call(() -> { api.release(ref); return null; }, mapper); }
        public void convert(String ref) { call(() -> { api.convert(ref); return null; }, mapper); }
    }

    @Component
    public static class PlanningClient {
        private final PlanningApi api; private final ObjectMapper mapper;
        public PlanningClient(PlanningApi api, ObjectMapper mapper) { this.api = api; this.mapper = mapper; }
        public PublishedSnapshot snapshot(long tourId) { return call(() -> api.snapshot(tourId), mapper); }
    }
}
