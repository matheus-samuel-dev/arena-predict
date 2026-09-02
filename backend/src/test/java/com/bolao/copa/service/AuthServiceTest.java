package com.bolao.copa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bolao.copa.dto.AuthDtos.LoginRequest;
import com.bolao.copa.dto.AuthDtos.RegisterRequest;
import com.bolao.copa.arena.domain.PlayerProfile;
import com.bolao.copa.arena.repository.PlayerProfileRepository;
import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    static {
        // The desktop runtime uses a newer JDK than the project's Java 21 target.
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtService jwtService;
    @Mock CurrentUserService currentUserService;
    @Mock PlayerProfileRepository playerProfileRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                authenticationManager,
                jwtService,
                currentUserService,
                playerProfileRepository
        );
    }

    @Test
    void participantLoginNormalizesEmailAndLegacyRole() {
        var user = user("jogador@arenapredict.com", UserRole.USER);
        when(userRepository.findByEmailIgnoreCase("jogador@arenapredict.com"))
                .thenReturn(Optional.of(user));
        when(jwtService.generate(user)).thenReturn("signed-token");

        var response = authService.login(new LoginRequest("  JOGADOR@ArenaPredict.com ", "unit-test-password"));

        assertThat(response.token()).isEqualTo("signed-token");
        assertThat(response.role()).isEqualTo(UserRole.PARTICIPANTE);
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void invalidCredentialsNeverQueryOrRevealAccount() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("inexistente@arenapredict.com", "Senha@123")
        )).isInstanceOf(BadCredentialsException.class);
        verify(userRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void publicRegistrationCannotSelfAssignAdmin() {
        when(userRepository.existsByEmailIgnoreCase("novo@arenapredict.com")).thenReturn(false);
        when(passwordEncoder.encode("Seguro@123")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generate(any(User.class))).thenReturn("signed-token");

        var response = authService.register(new RegisterRequest(
                "Novo Jogador",
                "NOVO@ArenaPredict.com",
                "Seguro@123",
                UserRole.ADMIN
        ));

        assertThat(response.email()).isEqualTo("novo@arenapredict.com");
        assertThat(response.role()).isEqualTo(UserRole.PARTICIPANTE);
    }

    @Test
    void currentSessionIncludesPersistedAvatar() {
        var user = user("jogador@arenapredict.com", UserRole.PARTICIPANTE);
        var details = org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail()).password("ignored").authorities("ROLE_PARTICIPANTE").build();
        var profile = new PlayerProfile();
        profile.setUser(user);
        profile.setAvatarUrl("https://example.test/avatar.png");
        when(currentUserService.from(details)).thenReturn(user);
        when(playerProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));

        assertThat(authService.me(details).avatarUrl()).isEqualTo("https://example.test/avatar.png");
    }

    private User user(String email, UserRole role) {
        var user = new User();
        user.setName("Jogador Demo");
        user.setEmail(email);
        user.setPasswordHash("bcrypt-hash");
        user.setRole(role);
        return user;
    }
}
