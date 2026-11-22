package com.tourflow.common.web;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Доступ к данным пользователя из JWT. */
public record CurrentUser(UUID id, String email, boolean manager, boolean admin) {
    public static CurrentUser get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof Jwt jwt)) throw ApiException.forbidden("Требуется вход в систему");
        boolean admin = a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        boolean mgr = a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_TOUR_MANAGER"));
        return new CurrentUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"), mgr, admin);
    }
    public boolean staff() { return manager || admin; }
}
