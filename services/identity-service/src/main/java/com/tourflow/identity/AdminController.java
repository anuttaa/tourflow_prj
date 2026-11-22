package com.tourflow.identity;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.tourflow.common.outbox.OutboxService;
import com.tourflow.common.web.ApiException;
import com.tourflow.common.web.CurrentUser;
import com.tourflow.identity.model.*;
import com.tourflow.identity.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdminController {
    private final UserRepository users; private final RoleRepository roles; private final RefreshTokenRepository refresh; private final UserProfileRepository profiles;
    private final TravelerPreferencesRepository prefs; private final AuditLogRepository AuditLogRepository; private final AuditService audit; private final OutboxService outbox; private final RSAKey key;

    public AdminController(UserRepository users, RoleRepository roles, RefreshTokenRepository refresh, UserProfileRepository profiles, TravelerPreferencesRepository prefs, AuditLogRepository AuditLogRepository,
                           AuditService audit, OutboxService outbox, RSAKey key) {
        this.users = users; this.roles = roles; this.refresh = refresh; this.profiles = profiles; this.prefs = prefs;
        this.AuditLogRepository = AuditLogRepository; this.audit = audit; this.outbox = outbox; this.key = key;
    }

    public record UserView(UUID id, String email, String status, List<String> roles, String firstName, String lastName, Instant createdAt) {}
    public record BlockRequest(boolean blocked) {}
    public record RolesRequest(@NotEmpty List<@Pattern(regexp = "CLIENT|TOUR_MANAGER|ADMIN") String> roles) {}
    public record ProfileRequest(@Size(max = 100) String firstName, @Size(max = 100) String lastName,
                                 @Pattern(regexp = "^[0-9+()\\- ]{0,30}$", message = "РќРµРєРѕСЂСЂРµРєС‚РЅС‹Р№ С‚РµР»РµС„РѕРЅ") String phone,
                                 @Min(0) BigDecimal budget, @Min(1) @Max(5) Integer classLevel, @Size(max = 500) String interests,
                                 @Pattern(regexp = "ECONOMY|BALANCE|COMFORT") String profile) {}

    private UserView view(User u) {
        UserProfile p = profiles.findById(u.getId()).orElse(null);
        return new UserView(u.getId(), u.getEmail(), u.getStatus(), u.getRoles().stream().map(Role::getCode).sorted().toList(),
                p == null ? null : p.getFirstName(), p == null ? null : p.getLastName(), u.getCreatedAt());
    }

    // FR-03: СѓРїСЂР°РІР»РµРЅРёРµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏРјРё
    @GetMapping("/api/v1/users") @PreAuthorize("hasRole('ADMIN')")
    public Page<UserView> list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "createdAt,desc") String sort) {
        String[] s = sort.split(",");
        String field = List.of("email", "status", "createdAt").contains(s[0]) ? s[0] : "createdAt";
        Sort.Direction dir = s.length > 1 && s[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return users.search(q, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(dir, field))).map(this::view);
    }

    @PatchMapping("/api/v1/users/{id}/block") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public UserView block(@PathVariable UUID id, @Valid @RequestBody BlockRequest r) {
        CurrentUser me = CurrentUser.get();
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("РџРѕР»СЊР·РѕРІР°С‚РµР»СЊ"));
        if (u.getId().equals(me.id())) throw ApiException.conflict("РќРµР»СЊР·СЏ Р·Р°Р±Р»РѕРєРёСЂРѕРІР°С‚СЊ СЃР°РјРѕРіРѕ СЃРµР±СЏ");
        u.setStatus(r.blocked() ? "BLOCKED" : "ACTIVE");
        u.setUpdatedAt(java.time.Instant.now());
        if (r.blocked()) { refresh.revokeAll(id); outbox.publish("user.blocked", id.toString(), Map.of("userId", id.toString(), "email", u.getEmail())); }
        audit.log(me.id(), r.blocked() ? "USER_BLOCKED" : "USER_UNBLOCKED", u.getEmail(), null);
        return view(u);
    }

    @PutMapping("/api/v1/users/{id}/roles") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public UserView setRoles(@PathVariable UUID id, @Valid @RequestBody RolesRequest r) {
        CurrentUser me = CurrentUser.get();
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("РџРѕР»СЊР·РѕРІР°С‚РµР»СЊ"));
        if (u.getId().equals(me.id()) && !r.roles().contains("ADMIN")) throw ApiException.conflict("РќРµР»СЊР·СЏ СЃРЅСЏС‚СЊ СЂРѕР»СЊ Р°РґРјРёРЅРёСЃС‚СЂР°С‚РѕСЂР° СЃ СЃР°РјРѕРіРѕ СЃРµР±СЏ");
        Set<Role> set = new HashSet<>();
        for (String code : r.roles()) set.add(roles.findByCode(code).orElseThrow(() -> ApiException.invalid("РќРµРёР·РІРµСЃС‚РЅР°СЏ СЂРѕР»СЊ " + code)));
        u.setRoles(set);
        refresh.revokeAll(id);
        audit.log(me.id(), "ROLES_CHANGED", u.getEmail(), String.join(",", r.roles()));
        return view(u);
    }

    // FR-04: РїСЂРѕС„РёР»СЊ Рё РїСЂРµРґРїРѕС‡С‚РµРЅРёСЏ
    @GetMapping("/api/v1/profile")
    public Map<String, Object> profile() {
        UUID id = CurrentUser.get().id();
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("РџРѕР»СЊР·РѕРІР°С‚РµР»СЊ"));
        UserProfile p = profiles.findById(id).orElseGet(UserProfile::new);
        TravelerPreferences t = prefs.findById(id).orElseGet(TravelerPreferences::new);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", u.getEmail()); m.put("firstName", p.getFirstName()); m.put("lastName", p.getLastName()); m.put("phone", p.getPhone());
        m.put("budget", t.getBudget()); m.put("classLevel", t.getClassLevel()); m.put("interests", t.getInterests()); m.put("profile", t.getProfile());
        return m;
    }

    @PutMapping("/api/v1/profile") @Transactional
    public Map<String, Object> updateProfile(@Valid @RequestBody ProfileRequest r) {
        UUID id = CurrentUser.get().id();
        UserProfile p = profiles.findById(id).orElseGet(() -> { UserProfile n = new UserProfile(); n.setUserId(id); return n; });
        p.setFirstName(r.firstName()); p.setLastName(r.lastName()); p.setPhone(r.phone());
        profiles.save(p);
        TravelerPreferences t = prefs.findById(id).orElseGet(() -> { TravelerPreferences n = new TravelerPreferences(); n.setUserId(id); return n; });
        t.setBudget(r.budget()); t.setClassLevel(r.classLevel()); t.setInterests(r.interests());
        t.setProfile(r.profile() == null ? "BALANCE" : r.profile());
        prefs.save(t);
        return profile();
    }

    // FR-27: Р¶СѓСЂРЅР°Р» Р°СѓРґРёС‚Р°
    @GetMapping("/api/v1/audit") @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditLog> audit(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return AuditLogRepository.findAll(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    // Р’РЅСѓС‚СЂРµРЅРЅРёР№ РІС‹Р·РѕРІ planning в†’ identity: РїСЂРµРґРїРѕС‡С‚РµРЅРёСЏ РґР»СЏ РїСЂРѕС„РёР»СЏ РїРѕРґР±РѕСЂР°
    @GetMapping("/internal/users/{id}/profile")
    public Map<String, Object> internalProfile(@PathVariable UUID id) {
        TravelerPreferences t = prefs.findById(id).orElseGet(TravelerPreferences::new);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profile", t.getProfile()); m.put("budget", t.getBudget()); m.put("classLevel", t.getClassLevel());
        return m;
    }

    // РџСѓР±Р»РёС‡РЅС‹Рµ РєР»СЋС‡Рё РїСЂРѕРІРµСЂРєРё JWT (РўР— 10.2)
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() { return new JWKSet(key.toPublicJWK()).toJSONObject(); }
}

