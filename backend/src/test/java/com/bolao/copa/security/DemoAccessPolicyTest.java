package com.bolao.copa.security;

import static org.assertj.core.api.Assertions.*;

import com.bolao.copa.arena.domain.ArenaEvent;
import com.bolao.copa.arena.domain.Championship;
import com.bolao.copa.config.DemoProperties;
import com.bolao.copa.dto.AuthDtos.DemoProfile;
import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;

class DemoAccessPolicyTest {
    private static final String ADMIN = "demo-admin@example.test";
    private static final String PLAYER = "demo-player@example.test";
    private final DemoAccessPolicy policy = policy(true);

    @Test
    void demoIdentityRequiresPersistedConfiguredAccountAndItsExpectedRole() {
        assertThat(policy.demoProfile(user(ADMIN, UserRole.ADMIN))).isEqualTo(DemoProfile.ADMIN);
        assertThat(policy.demoProfile(user(PLAYER, UserRole.USER))).isEqualTo(DemoProfile.PARTICIPANT);
        assertThat(policy.demoProfile(user(ADMIN, UserRole.PARTICIPANTE))).isNull();
        assertThat(policy.demoProfile(user("normal@example.test", UserRole.ADMIN))).isNull();
        var unsaved = user(ADMIN, UserRole.ADMIN);
        ReflectionTestUtils.setField(unsaved, "id", null);
        assertThat(policy.demoProfile(unsaved)).isNull();
    }

    @Test
    void onlyDemoParticipantCanPredictInControlledScenario() {
        var event = scenario();
        assertThatCode(() -> policy.requirePredictionAccess(user(PLAYER, UserRole.PARTICIPANTE), event))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requirePredictionAccess(user(ADMIN, UserRole.ADMIN), event))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> policy.requirePredictionAccess(user("regular@example.test", UserRole.PARTICIPANTE), event))
                .isInstanceOf(AccessDeniedException.class);
        event.setDemoArchived(true);
        assertThatThrownBy(() -> policy.requirePredictionAccess(user(PLAYER, UserRole.PARTICIPANTE), event))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void externalIdentityNeverBecomesEligibleForDemoPredictionEvenWithContradictoryFlags() {
        var event = scenario();
        event.setExternalId("123");
        assertThatThrownBy(() -> policy.requirePredictionAccess(user(PLAYER, UserRole.PARTICIPANTE), event))
                .isInstanceOf(AccessDeniedException.class);
        event.setExternalId(null);
        event.setExternalProvider("PANDASCORE");
        assertThatThrownBy(() -> policy.requirePredictionAccess(user(PLAYER, UserRole.PARTICIPANTE), event))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void demoAccountsCannotPredictOnUnmanagedEventsWhileNormalParticipantCan() {
        var event = new ArenaEvent();
        event.setChampionship(new Championship());
        event.setDemo(false);
        assertThatThrownBy(() -> policy.requirePredictionAccess(user(PLAYER, UserRole.PARTICIPANTE), event))
                .isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> policy.requirePredictionAccess(user("regular@example.test", UserRole.PARTICIPANTE), event))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/dashboard", "/api/admin/users", "/api/admin/sports-sync/status"})
    void demoAdminCanReadButCannotWriteAdministrativeResources(String path) {
        var auth = auth(ADMIN, "ROLE_ADMIN");
        for (String verb : List.of("GET", "HEAD"))
            assertThat(policy.allowsHttpRequest(auth, verb, path)).as("%s %s", verb, path).isTrue();
        for (String verb : List.of("POST", "PUT", "PATCH", "DELETE"))
            assertThat(policy.allowsHttpRequest(auth, verb, path)).as("%s %s", verb, path).isFalse();
    }

    @Test
    void demoAdminCanOnlyWriteTheExplicitScenarioCommands() {
        var auth = auth(ADMIN, "ROLE_ADMIN");
        for (String path : List.of("/api/demo/reset", "/api/demo/events/15/start", "/api/demo/events/15/result"))
            assertThat(policy.allowsHttpRequest(auth, "POST", path)).isTrue();
        for (String path : List.of("/api/demo/events/15/delete", "/api/demo/events/15", "/api/predictions",
                "/api/profile", "/api/community/posts", "/api/pools", "/api/demo/events/../reset"))
            assertThat(policy.allowsHttpRequest(auth, "POST", path)).as(path).isFalse();
        assertThat(policy.allowsHttpRequest(auth, "GET", "/api/demo/scenario")).isTrue();
        assertThat(policy.allowsHttpRequest(auth, "GET", "/api/demo/reset")).isFalse();
    }

    @Test
    void participantCannotStartFinishOrResetDemoButCanUseNormalPredictionCommands() {
        var auth = auth(PLAYER, "ROLE_PARTICIPANTE");
        for (String path : List.of("/api/demo/reset", "/api/demo/events/15/start", "/api/demo/events/15/result"))
            assertThat(policy.allowsHttpRequest(auth, "POST", path)).isFalse();
        assertThat(policy.allowsHttpRequest(auth, "GET", "/api/demo/scenario")).isTrue();
        assertThat(policy.allowsHttpRequest(auth, "POST", "/api/predictions")).isTrue();
        assertThat(policy.allowsHttpRequest(auth, "POST", "/api/predictions/15/cancel")).isTrue();
    }

    @Test
    void disablingQuickAccessDoesNotPromotePreviouslyIssuedDemoTokens() {
        var disabled = policy(false);
        assertThat(disabled.isReservedDemoEmail(" DEMO-ADMIN@EXAMPLE.TEST ")).isTrue();
        assertThatThrownBy(() -> disabled.requireDemoAdmin(user(ADMIN, UserRole.ADMIN)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(disabled.allowsHttpRequest(auth(ADMIN, "ROLE_ADMIN"), "GET", "/api/admin/users")).isFalse();
        assertThat(disabled.allowsHttpRequest(auth(ADMIN, "ROLE_ADMIN"), "POST", "/api/demo/reset")).isFalse();
        assertThat(disabled.allowsHttpRequest(auth(PLAYER, "ROLE_PARTICIPANTE"), "POST", "/api/predictions")).isFalse();
        assertThat(disabled.allowsHttpRequest(auth(ADMIN, "ROLE_ADMIN"), "POST", "/api/auth/logout")).isTrue();
    }

    private static DemoAccessPolicy policy(boolean enabled) {
        return new DemoAccessPolicy(new DemoProperties(enabled, ADMIN, "", PLAYER, ""));
    }

    private static User user(String email, UserRole role) {
        var user = new User();
        ReflectionTestUtils.setField(user, "id", 10L);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }

    private static UsernamePasswordAuthenticationToken auth(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role)));
    }

    private static ArenaEvent scenario() {
        var championship = new Championship();
        championship.setDemoManaged(true);
        var event = new ArenaEvent();
        event.setChampionship(championship);
        event.setDemo(true);
        return event;
    }
}
