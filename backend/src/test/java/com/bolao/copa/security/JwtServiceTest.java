package com.bolao.copa.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

class JwtServiceTest {
    private static final String SECRET = "test-secret-with-at-least-thirty-two-bytes-123456";
    private final JwtService jwtService = new JwtService(SECRET, 60);

    @Test
    void refusesMissingOrWeakSigningSecret() {
        assertThatThrownBy(() -> new JwtService("", 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new JwtService("shared-default", 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void generatesAndValidatesParticipantTokenWithCanonicalRole() {
        var user = user("jogador@arenapredict.com", UserRole.USER);
        var token = jwtService.parse(jwtService.generate(user));
        UserDetails details = org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password("ignored")
                .authorities("ROLE_PARTICIPANTE", "ROLE_USER")
                .build();

        assertThat(token.subject()).isEqualTo(user.getEmail());
        assertThat(token.role()).isEqualTo(UserRole.PARTICIPANTE);
        assertThat(jwtService.isValidFor(token, details)).isTrue();
    }

    @Test
    void rejectsExpiredToken() {
        var expired = jwtService.generate(
                user("admin@arenapredict.com", UserRole.ADMIN),
                Instant.now().minus(Duration.ofHours(2)),
                Duration.ofMinutes(1)
        );

        assertThatThrownBy(() -> jwtService.parse(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTokenWithInvalidSignature() {
        var token = jwtService.generate(user("admin@arenapredict.com", UserRole.ADMIN));
        var replacement = token.endsWith("a") ? "b" : "a";
        var tampered = token.substring(0, token.length() - 1) + replacement;

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void roleAliasesAreNormalized() {
        assertThat(UserRole.fromExternalValue("ROLE_USER")).isEqualTo(UserRole.PARTICIPANTE);
        assertThat(UserRole.fromExternalValue("player")).isEqualTo(UserRole.PARTICIPANTE);
        assertThat(UserRole.fromExternalValue("ROLE_ADMIN")).isEqualTo(UserRole.ADMIN);
    }

    private User user(String email, UserRole role) {
        var user = new User();
        user.setName("Demo");
        user.setEmail(email);
        user.setPasswordHash("ignored");
        user.setRole(role);
        return user;
    }
}
