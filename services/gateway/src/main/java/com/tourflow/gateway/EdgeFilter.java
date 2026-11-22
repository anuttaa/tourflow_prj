package com.tourflow.gateway;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Ограничение частоты запросов по IP (окно в памяти), сквозной correlation-id, защита от подделки сервисного токена. */
@Component
public class EdgeFilter implements GlobalFilter, Ordered {
    private record Window(long start, int count) {}
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int per10s; private final int authPerMin;

    public EdgeFilter(@Value("${app.rate-limit.per-10s}") int per10s, @Value("${app.rate-limit.auth-per-minute}") int authPerMin) {
        this.per10s = per10s; this.authPerMin = authPerMin;
    }

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    private boolean allowed(String key, long windowMs, int limit) {
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, old) -> old == null || now - old.start() >= windowMs ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        if (windows.size() > 50_000) windows.entrySet().removeIf(e -> now - e.getValue().start() > 120_000);
        return w.count() <= limit;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange ex, GatewayFilterChain chain) {
        String ip = ex.getRequest().getRemoteAddress() == null ? "unknown" : ex.getRequest().getRemoteAddress().getAddress().getHostAddress();
        String fwd = ex.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) ip = fwd.split(",")[0].trim();
        String path = ex.getRequest().getURI().getPath();
        boolean auth = path.startsWith("/api/v1/auth/login") || path.startsWith("/api/v1/auth/register") || path.startsWith("/api/v1/auth/refresh");
        if (!allowed("g:" + ip, 10_000, per10s) || (auth && !allowed("a:" + ip, 60_000, authPerMin))) {
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return ex.getResponse().setComplete();
        }
        String cid = ex.getRequest().getHeaders().getFirst("X-Correlation-Id");
        String correlation = cid == null || cid.isBlank() ? UUID.randomUUID().toString() : cid;
        ServerHttpRequest req = ex.getRequest().mutate().headers(h -> { h.remove("X-Service-Token"); h.set("X-Correlation-Id", correlation); }).build();
        ex.getResponse().getHeaders().set("X-Correlation-Id", correlation);
        return chain.filter(ex.mutate().request(req).build());
    }
}
