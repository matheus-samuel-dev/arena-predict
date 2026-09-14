package com.bolao.copa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bolao.copa.config.DemoProperties;
import com.bolao.copa.arena.domain.PlayerProfile;
import com.bolao.copa.arena.repository.PlayerProfileRepository;
import com.bolao.copa.dto.AuthDtos.DemoAccessRequest;
import com.bolao.copa.dto.AuthDtos.DemoProfile;
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

@ExtendWith(MockitoExtension.class)
class DemoAuthServiceTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Mock UserRepository users;
    @Mock JwtService jwtService;
    @Mock PlayerProfileRepository playerProfiles;

    private DemoAuthService service;

    @BeforeEach
    void setUp() {
        service = new DemoAuthService(
                users,
                jwtService,
                new DemoProperties(
                        true,
                        "portfolio-admin@example.test",
                        "",
                        "portfolio-player@example.test",
                        ""
                ),
                playerProfiles
        );
    }

    @Test
    void participantAccessIssuesARegularTokenForConfiguredIdentity() {
        var participant = user("portfolio-player@example.test", UserRole.PARTICIPANTE);
        when(users.findByEmailIgnoreCase("portfolio-player@example.test"))
                .thenReturn(Optional.of(participant));
        when(jwtService.generate(participant)).thenReturn("regular-signed-jwt");
        var profile = new PlayerProfile();
        profile.setUser(participant);
        profile.setAvatarUrl("/assets/avatars/jogador-demo.webp");
        when(playerProfiles.findByUser(participant)).thenReturn(Optional.of(profile));

        var response = service.access(new DemoAccessRequest(DemoProfile.PARTICIPANT));

        assertThat(response.token()).isEqualTo("regular-signed-jwt");
        assertThat(response.email()).isEqualTo("portfolio-player@example.test");
        assertThat(response.role()).isEqualTo(UserRole.PARTICIPANTE);
        assertThat(response.avatarUrl()).isEqualTo("/assets/avatars/jogador-demo.webp");
    }

    @Test
    void adminAccessCannotIssueTokenForAnAccountWithAnotherRole() {
        var wronglyConfigured = user("portfolio-admin@example.test", UserRole.PARTICIPANTE);
        when(users.findByEmailIgnoreCase("portfolio-admin@example.test"))
                .thenReturn(Optional.of(wronglyConfigured));

        assertThatThrownBy(() -> service.access(new DemoAccessRequest(DemoProfile.ADMIN)))
                .isInstanceOf(DemoAuthService.DemoAccessUnavailableException.class)
                .hasMessage("O acesso demonstrativo está temporariamente indisponível.");
        verify(jwtService, never()).generate(wronglyConfigured);
    }

    @Test
    void missingConfiguredAccountDoesNotFallBackToAnotherIdentity() {
        when(users.findByEmailIgnoreCase("portfolio-admin@example.test"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.access(new DemoAccessRequest(DemoProfile.ADMIN)))
                .isInstanceOf(DemoAuthService.DemoAccessUnavailableException.class);
        verify(jwtService, never()).generate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void jsonProfileParserSupportsOnlyTheTwoDemoProfiles() {
        assertThat(DemoProfile.fromJson("participant")).isEqualTo(DemoProfile.PARTICIPANT);
        assertThat(DemoProfile.fromJson("PARTICIPANTE")).isEqualTo(DemoProfile.PARTICIPANT);
        assertThat(DemoProfile.fromJson("admin")).isEqualTo(DemoProfile.ADMIN);
        assertThatThrownBy(() -> DemoProfile.fromJson("moderator"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private User user(String email, UserRole role) {
        var user = new User();
        user.setName("Conta de portfólio");
        user.setEmail(email);
        user.setPasswordHash("irrelevant-hash");
        user.setRole(role);
        return user;
    }
}
