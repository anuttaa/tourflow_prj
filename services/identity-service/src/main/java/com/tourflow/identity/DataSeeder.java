package com.tourflow.identity;

import com.tourflow.identity.model.*;
import com.tourflow.identity.repository.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Р”РµРјРѕРЅСЃС‚СЂР°С†РёРѕРЅРЅС‹Рµ РґР°РЅРЅС‹Рµ: СЂРѕР»Рё Рё РїРѕ РѕРґРЅРѕРјСѓ РїРѕР»СЊР·РѕРІР°С‚РµР»СЋ РєР°Р¶РґРѕР№ СЂРѕР»Рё (РїР°СЂРѕР»Рё Р·Р°РґР°СЋС‚СЃСЏ С‡РµСЂРµР· РѕРєСЂСѓР¶РµРЅРёРµ). */
@Component
public class DataSeeder implements CommandLineRunner {
    private final RoleRepository roles; private final UserRepository users; private final UserProfileRepository profiles; private final TravelerPreferencesRepository prefs;
    private final PasswordEncoder encoder;
    @Value("${app.seed.enabled}") boolean enabled;
    @Value("${app.seed.admin-password}") String adminPw;
    @Value("${app.seed.manager-password}") String managerPw;
    @Value("${app.seed.client-password}") String clientPw;

    public DataSeeder(RoleRepository roles, UserRepository users, UserProfileRepository profiles, TravelerPreferencesRepository prefs, PasswordEncoder encoder) {
        this.roles = roles; this.users = users; this.profiles = profiles; this.prefs = prefs; this.encoder = encoder;
    }

    @Override @Transactional
    public void run(String... args) {
        for (String c : new String[]{"CLIENT", "TOUR_MANAGER", "ADMIN"})
            if (roles.findByCode(c).isEmpty()) { Role r = new Role(); r.setCode(c); roles.save(r); }
        if (!enabled) return;
        seed("admin@tourflow.local", adminPw, "РђРґРјРёРЅ", "ADMIN");
        seed("manager@tourflow.local", managerPw, "РњР°СЂРёСЏ", "TOUR_MANAGER");
        seed("client@tourflow.local", clientPw, "РљРёСЂРёР»Р»", "CLIENT");
    }

    private void seed(String email, String pw, String name, String role) {
        if (users.existsByEmailIgnoreCase(email)) return;
        User u = new User();
        u.setId(UUID.randomUUID()); u.setEmail(email); u.setPasswordHash(encoder.encode(pw)); u.setStatus("ACTIVE");
        u.getRoles().add(roles.findByCode(role).orElseThrow());
        users.save(u);
        UserProfile p = new UserProfile(); p.setUserId(u.getId()); p.setFirstName(name); profiles.save(p);
        TravelerPreferences t = new TravelerPreferences(); t.setUserId(u.getId()); prefs.save(t);
    }
}

