package com.tourflow.identity;

import com.tourflow.common.outbox.OutboxService;
import com.tourflow.common.web.ApiException;
import com.tourflow.identity.model.*;
import com.tourflow.identity.repository.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users; private final RoleRepository roles; private final RefreshTokenRepository refresh; private final EmailConfirmationRepository confirms;
    private final UserProfileRepository profiles; private final TravelerPreferencesRepository prefs; private final PasswordEncoder encoder;
    private final TokenService tokens; private final OutboxService outbox; private final AuditService audit;
    private final String frontendUrl; private final long refreshDays;

    public AuthService(UserRepository users, RoleRepository roles, RefreshTokenRepository refresh, EmailConfirmationRepository confirms, UserProfileRepository profiles, TravelerPreferencesRepository prefs,
                       PasswordEncoder encoder, TokenService tokens, OutboxService outbox, AuditService audit,
                       @Value("${app.frontend-url}") String frontendUrl, @Value("${app.jwt.refresh-ttl-days}") long refreshDays) {
        this.users = users; this.roles = roles; this.refresh = refresh; this.confirms = confirms; this.profiles = profiles; this.prefs = prefs;
        this.encoder = encoder; this.tokens = tokens; this.outbox = outbox; this.audit = audit; this.frontendUrl = frontendUrl; this.refreshDays = refreshDays;
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn, Map<String, Object> user) {}

    /** UC-01: СЂРµРіРёСЃС‚СЂР°С†РёСЏ С‚СѓСЂРёСЃС‚Р°, РїРёСЃСЊРјРѕ СЃ РїРѕРґС‚РІРµСЂР¶РґРµРЅРёРµРј СѓС…РѕРґРёС‚ С‡РµСЂРµР· outbox. */
    @Transactional
    public void register(String email, String password, String firstName, String lastName) {
        String mail = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(mail)) throw ApiException.conflict("РџРѕР»СЊР·РѕРІР°С‚РµР»СЊ СЃ С‚Р°РєРёРј email СѓР¶Рµ Р·Р°СЂРµРіРёСЃС‚СЂРёСЂРѕРІР°РЅ");
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(mail);
        u.setPasswordHash(encoder.encode(password));
        u.setStatus("PENDING");
        u.getRoles().add(roles.findByCode("CLIENT").orElseThrow());
        users.save(u);
        UserProfile p = new UserProfile();
        p.setUserId(u.getId()); p.setFirstName(firstName); p.setLastName(lastName);
        profiles.save(p);
        TravelerPreferences tp = new TravelerPreferences();
        tp.setUserId(u.getId());
        prefs.save(tp);

        String token = TokenService.randomToken();
        EmailConfirmation c = new EmailConfirmation();
        c.setId(UUID.randomUUID()); c.setUserId(u.getId()); c.setTokenHash(TokenService.sha256(token));
        c.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        confirms.save(c);
        outbox.publish("user.registered", u.getId().toString(), Map.of("userId", u.getId().toString(), "email", mail,
                "firstName", firstName == null ? "" : firstName, "confirmUrl", frontendUrl + "/confirm?token=" + token));
        audit.log(u.getId(), "REGISTER", mail, null);
    }

    @Transactional
    public void confirm(String token) {
        EmailConfirmation c = confirms.findByTokenHash(TokenService.sha256(token))
                .orElseThrow(() -> ApiException.invalid("РЎСЃС‹Р»РєР° РїРѕРґС‚РІРµСЂР¶РґРµРЅРёСЏ РЅРµРґРµР№СЃС‚РІРёС‚РµР»СЊРЅР°"));
        if (c.getConfirmedAt() != null) return;
        if (c.getExpiresAt().isBefore(Instant.now())) throw ApiException.invalid("РЎСЂРѕРє РґРµР№СЃС‚РІРёСЏ СЃСЃС‹Р»РєРё РёСЃС‚С‘Рє, Р·Р°СЂРµРіРёСЃС‚СЂРёСЂСѓР№С‚РµСЃСЊ РїРѕРІС‚РѕСЂРЅРѕ");
        User u = users.findById(c.getUserId()).orElseThrow();
        if (u.getStatus().equals("PENDING")) u.setStatus("ACTIVE");
        c.setConfirmedAt(Instant.now());
        audit.log(u.getId(), "EMAIL_CONFIRMED", u.getEmail(), null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair login(String email, String password) {
        User u = users.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (u == null) {
            audit.log(null, "LOGIN_FAILED", email, "РЅРµРёР·РІРµСЃС‚РЅС‹Р№ РїРѕР»СЊР·РѕРІР°С‚РµР»СЊ");
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "РќРµРІРµСЂРЅС‹Р№ email РёР»Рё РїР°СЂРѕР»СЊ");
        }
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(Instant.now()))
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "LOCKED", "РЎР»РёС€РєРѕРј РјРЅРѕРіРѕ РЅРµСѓРґР°С‡РЅС‹С… РїРѕРїС‹С‚РѕРє, РїРѕРІС‚РѕСЂРёС‚Рµ РїРѕР·Р¶Рµ");
        if (!encoder.matches(password, u.getPasswordHash())) {
            u.setFailedAttempts(u.getFailedAttempts() + 1);
            if (u.getFailedAttempts() >= 5) { u.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES)); u.setFailedAttempts(0); }
            audit.log(u.getId(), "LOGIN_FAILED", u.getEmail(), "РЅРµРІРµСЂРЅС‹Р№ РїР°СЂРѕР»СЊ");
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "РќРµРІРµСЂРЅС‹Р№ email РёР»Рё РїР°СЂРѕР»СЊ");
        }
        if (u.getStatus().equals("PENDING")) throw ApiException.forbidden("РџРѕРґС‚РІРµСЂРґРёС‚Рµ email РїРѕ СЃСЃС‹Р»РєРµ РёР· РїРёСЃСЊРјР°");
        if (u.getStatus().equals("BLOCKED")) throw ApiException.forbidden("РЈС‡С‘С‚РЅР°СЏ Р·Р°РїРёСЃСЊ Р·Р°Р±Р»РѕРєРёСЂРѕРІР°РЅР° Р°РґРјРёРЅРёСЃС‚СЂР°С‚РѕСЂРѕРј");
        u.setFailedAttempts(0); u.setLockedUntil(null);
        audit.log(u.getId(), "LOGIN", u.getEmail(), null);
        return issue(u);
    }

    @Transactional
    public TokenPair refresh(String token) {
        RefreshToken t = refresh.findByTokenHash(TokenService.sha256(token))
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "РЎРµСЃСЃРёСЏ РЅРµРґРµР№СЃС‚РІРёС‚РµР»СЊРЅР°"));
        if (t.isRevoked() || t.getExpiresAt().isBefore(Instant.now()))
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "РЎРµСЃСЃРёСЏ РёСЃС‚РµРєР»Р°, РІРѕР№РґРёС‚Рµ Р·Р°РЅРѕРІРѕ");
        User u = users.findById(t.getUserId()).orElseThrow();
        if (!u.getStatus().equals("ACTIVE")) throw ApiException.forbidden("РЈС‡С‘С‚РЅР°СЏ Р·Р°РїРёСЃСЊ РЅРµРґРѕСЃС‚СѓРїРЅР°");
        t.setRevoked(true); // СЂРѕС‚Р°С†РёСЏ refresh-С‚РѕРєРµРЅР°
        return issue(u);
    }

    @Transactional
    public void logout(String token) {
        refresh.findByTokenHash(TokenService.sha256(token)).ifPresent(t -> t.setRevoked(true));
    }

    private TokenPair issue(User u) {
        String raw = TokenService.randomToken();
        RefreshToken t = new RefreshToken();
        t.setId(UUID.randomUUID()); t.setUserId(u.getId()); t.setTokenHash(TokenService.sha256(raw));
        t.setExpiresAt(Instant.now().plus(refreshDays, ChronoUnit.DAYS));
        refresh.save(t);
        UserProfile p = profiles.findById(u.getId()).orElse(null);
        List<String> rs = u.getRoles().stream().map(Role::getCode).sorted().toList();
        Map<String, Object> user = Map.of("id", u.getId().toString(), "email", u.getEmail(), "roles", rs,
                "firstName", p == null || p.getFirstName() == null ? "" : p.getFirstName());
        return new TokenPair(tokens.accessToken(u), raw, tokens.accessTtlSeconds(), user);
    }
}

