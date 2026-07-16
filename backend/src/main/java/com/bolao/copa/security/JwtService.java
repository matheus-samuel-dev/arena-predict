package com.bolao.copa.security;

import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("app.jwt.secret deve possuir pelo menos 32 bytes.");
        }
        if (expirationMinutes <= 0) {
            throw new IllegalArgumentException("app.jwt.expiration-minutes deve ser maior que zero.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public String generate(User user) {
        return generate(user, Instant.now(), expiration);
    }

    String generate(User user, Instant issuedAt, Duration validity) {
        var canonicalRole = user.getRole().canonical();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(ROLE_CLAIM, canonicalRole.name())
                .claim("name", user.getName())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(validity)))
                .signWith(key)
                .compact();
    }

    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        var subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Token sem identificação de usuário.");
        }

        var tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
        // Tokens created by the previous version had no type claim and remain valid.
        if (tokenType != null && !ACCESS_TOKEN_TYPE.equals(tokenType)) {
            throw new IllegalArgumentException("Tipo de token inválido.");
        }
        if (claims.getExpiration() == null) {
            throw new IllegalArgumentException("Token sem expiração.");
        }

        var role = UserRole.fromExternalValue(claims.get(ROLE_CLAIM, String.class)).canonical();
        return new TokenClaims(
                subject,
                role,
                claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant(),
                claims.getExpiration() == null ? null : claims.getExpiration().toInstant()
        );
    }

    public String subject(String token) {
        return parse(token).subject();
    }

    public boolean isValidFor(TokenClaims token, UserDetails userDetails) {
        if (!token.subject().equalsIgnoreCase(userDetails.getUsername())) {
            return false;
        }
        var expectedAuthority = token.role().authority();
        return userDetails.getAuthorities().stream()
                .anyMatch(authority -> expectedAuthority.equals(authority.getAuthority()));
    }

    public record TokenClaims(String subject, UserRole role, Instant issuedAt, Instant expiresAt) {
    }
}
