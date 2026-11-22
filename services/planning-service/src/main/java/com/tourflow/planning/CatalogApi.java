package com.tourflow.planning;

import com.tourflow.common.dto.Contracts.*;
import com.tourflow.common.web.InternalTokenFilter;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Низкоуровневые вызовы catalog (REST, сервисный токен, таймауты); circuit breaker Resilience4j — внутри этого бина. */
@Component
public class CatalogApi {
    private final RestClient rest;

    public CatalogApi(@Value("${app.catalog-url}") String url, @Value("${app.internal-token}") String token) {
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        f.setReadTimeout(Duration.ofSeconds(5));
        this.rest = RestClient.builder().baseUrl(url).requestFactory(f).defaultHeader(InternalTokenFilter.HEADER, token).build();
    }

    @CircuitBreaker(name = "catalog")
    public List<CheckResult> check(List<CheckItem> items) {
        return rest.post().uri("/internal/offers/check").body(items).retrieve().body(new ParameterizedTypeReference<>() {});
    }

    @CircuitBreaker(name = "catalog")
    public List<CheckResult> candidates(CandidateRequest r) {
        return rest.post().uri("/internal/offers/candidates").body(r).retrieve().body(new ParameterizedTypeReference<>() {});
    }

    @CircuitBreaker(name = "catalog")
    public DestinationDto destination(long id) {
        return rest.get().uri("/internal/destinations/{id}", id).retrieve().body(DestinationDto.class);
    }

    @CircuitBreaker(name = "catalog")
    public List<DestinationDto> destinations() {
        return rest.get().uri("/internal/destinations").retrieve().body(new ParameterizedTypeReference<>() {});
    }
}
