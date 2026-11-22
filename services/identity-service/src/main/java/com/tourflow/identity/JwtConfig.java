package com.tourflow.identity;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** RS256: приватный ключ только в identity (хранится в томе), остальные сервисы проверяют по JWKS (ТЗ 11). */
@Configuration
public class JwtConfig {

    @Bean
    RSAKey rsaKey(@Value("${app.jwt.key-file}") String file) throws Exception {
        Path p = Path.of(file);
        if (Files.exists(p)) return RSAKey.parse(Files.readString(p));
        RSAKey key = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, key.toJSONString());
        return key;
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey key) { return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key))); }

    @Bean
    JwtDecoder jwtDecoder(RSAKey key) throws Exception { return NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build(); }

    @Bean
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(11);
    }
}
