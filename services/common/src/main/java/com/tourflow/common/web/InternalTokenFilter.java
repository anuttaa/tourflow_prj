package com.tourflow.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Внутренние эндпоинты /internal/** доступны только с сервисным токеном (ТЗ 10.2). */
public class InternalTokenFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Service-Token";
    private final byte[] token;

    public InternalTokenFilter(String token) { this.token = token.getBytes(StandardCharsets.UTF_8); }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (req.getRequestURI().startsWith("/internal/")) {
            String h = req.getHeader(HEADER);
            if (h != null && MessageDigest.isEqual(token, h.getBytes(StandardCharsets.UTF_8))) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        "service", null, List.of(new SimpleGrantedAuthority("SERVICE"))));
            }
        }
        chain.doFilter(req, res);
    }
}
