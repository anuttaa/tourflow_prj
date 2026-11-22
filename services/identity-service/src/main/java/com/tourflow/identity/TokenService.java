package com.tourflow.identity;

import com.nimbusds.jose.jwk.RSAKey;
import com.tourflow.identity.model.Role;
import com.tourflow.identity.model.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private static final SecureRandom RNG = new SecureRandom();
    private final JwtEncoder encoder;
    private final RSAKey key;
    private final long accessTtlMin;

    public TokenService(JwtEncoder encoder, RSAKey key, @Value("${app.jwt.access-ttl-min}") long accessTtlMin) {
        this.encoder = encoder; this.key = key; this.accessTtlMin = accessTtlMin;
    }

    public String accessToken(User u) {
        Instant now = Instant.now();
        List<String> roles = u.getRoles().stream().map(Role::getCode).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("tourflow-identity").subject(u.getId().toString())
                .issuedAt(now).expiresAt(now.plus(accessTtlMin, ChronoUnit.MINUTES))
                .claim("email", u.getEmail()).claim("roles", roles).build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTtlSeconds() { return accessTtlMin * 60; }

    public static String randomToken() {
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String sha256(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
