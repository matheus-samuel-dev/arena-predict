package com.bolao.copa.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bolao.copa.entity.UserRole;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.security.JwtService;
import com.bolao.copa.support.RegularTestUsers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Exercises the real JWT filter, database identities and HTTP authorization, without security mocks. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoSecurityIntegrationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;

    @Test
    void quickAccessProducesRegularValidatedJwtAndPersistedDemoIdentity() throws Exception {
        for (String profile : List.of("ADMIN", "PARTICIPANT")) {
            String token = quickAccess(profile);
            var claims = jwt.parse(token);
            var account = users.findByEmailIgnoreCase(claims.subject()).orElseThrow();
            assertThat(claims.role()).isEqualTo(account.getRole().canonical());
            assertThat(claims.expiresAt()).isAfter(claims.issuedAt());
            http.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(account.getId()))
                    .andExpect(jsonPath("$.demoProfile").value(profile))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PARTICIPANT"})
    void demoAdminCanExploreWhileParticipantAndGenericAdministrativeCommandsStayRestricted(String profile) throws Exception {
        String auth = "Bearer " + quickAccess(profile);
        for (String path : List.of("/api/admin/dashboard", "/api/admin/users", "/api/admin/audit",
                "/api/admin/settings", "/api/admin/sports-sync/status"))
            http.perform(get(path).header("Authorization", auth)).andExpect(status().is("ADMIN".equals(profile) ? 200 : 403));
        for (String path : List.of("/api/admin/sports", "/api/admin/championships", "/api/admin/events",
                "/api/admin/markets"))
            http.perform(post(path).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        http.perform(put("/api/admin/events/1/result").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"homeScore\":2,\"awayScore\":1,\"finishEvent\":true}"))
                .andExpect(status().isForbidden());
        http.perform(post("/api/community/posts").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Unauthorized post\"}"))
                .andExpect(status().is("PARTICIPANT".equals(profile) ? 201 : 403));
    }

    @Test
    void demoParticipantCannotStartFinishOrResetEvenWithArbitraryEventIds() throws Exception {
        String auth = "Bearer " + quickAccess("PARTICIPANT");
        for (String path : List.of("/api/demo/reset", "/api/demo/events/1/start", "/api/demo/events/1/result",
                "/api/demo/events/999999/result"))
            http.perform(post(path).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
    }

    @Test
    void demoAdministratorCannotPlaceOrCancelPredictions() throws Exception {
        String auth = "Bearer " + quickAccess("ADMIN");
        http.perform(post("/api/predictions").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        http.perform(post("/api/predictions/1/cancel").header("Authorization", auth))
                .andExpect(status().isForbidden());
    }

    @Test
    void normalAdministratorKeepsRealAdministrativeAccessButCannotUseDemoCommands() throws Exception {
        var administrator = RegularTestUsers.admin(users);
        String auth = "Bearer " + jwt.generate(administrator);
        http.perform(get("/api/admin/dashboard").header("Authorization", auth)).andExpect(status().isOk());
        http.perform(get("/api/admin/sports-sync/status").header("Authorization", auth)).andExpect(status().isOk());
        http.perform(post("/api/demo/reset").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        http.perform(get("/api/auth/me").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.demoProfile").doesNotExist());
    }

    @Test
    void publicDemoBackofficeCannotEnumerateRegisteredVisitorWalletsOrProfiles() throws Exception {
        var visitor=RegularTestUsers.freshParticipant(users);
        visitor.setName("Private visitor audit " + visitor.getId());users.saveAndFlush(visitor);
        String demo="Bearer " + quickAccess("ADMIN");
        http.perform(get("/api/admin/users").param("search",visitor.getName()).header("Authorization",demo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        String admin="Bearer " + jwt.generate(RegularTestUsers.admin(users));
        http.perform(get("/api/admin/users").param("search",visitor.getName()).header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void normalParticipantKeepsCommunityCommandsWithoutAdministrativeOrDemoPrivileges() throws Exception {
        var participant = RegularTestUsers.participant(users);
        String auth = "Bearer " + jwt.generate(participant);
        http.perform(post("/api/community/posts").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Publicação regular de integração.\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.content").value("Publicação regular de integração."));
        http.perform(get("/api/admin/users").header("Authorization", auth)).andExpect(status().isForbidden());
        http.perform(post("/api/demo/reset").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        http.perform(get("/api/auth/me").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.demoProfile").doesNotExist());
    }

    @Test
    void demoCommandsDoNotBypassSignatureValidationOrDatabaseRoleValidation() throws Exception {
        var participant = users.findByEmailIgnoreCase("jogador@arenapredict.com").orElseThrow();
        var foreignSigner = new JwtService("a-different-signing-secret-with-more-than-32-bytes", 60);
        http.perform(post("/api/demo/reset").header("Authorization", "Bearer " + foreignSigner.generate(participant))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        var admin = users.findByEmailIgnoreCase("admin@arenapredict.com").orElseThrow();
        String formerAdminToken = jwt.generate(admin);
        admin.setRole(UserRole.PARTICIPANTE);
        users.saveAndFlush(admin);
        http.perform(post("/api/demo/reset").header("Authorization", "Bearer " + formerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        http.perform(post("/api/demo/reset").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin@arenapredict.com", "JOGADOR@ARENAPREDICT.COM"})
    void registrationNeverCapturesReservedDemoIdentities(String email) throws Exception {
        long count = users.count();
        http.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("name", "Visitante", "email", email,
                                "password", "Registration-test-123", "role", "ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Não foi possível concluir o cadastro com os dados informados."));
        assertThat(users.count()).isEqualTo(count);
    }

    private String quickAccess(String profile) throws Exception {
        var response = http.perform(post("/api/auth/demo").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("profile", profile))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoProfile").value(profile))
                .andReturn().getResponse().getContentAsByteArray();
        return json.readTree(response).get("token").asText();
    }
}
