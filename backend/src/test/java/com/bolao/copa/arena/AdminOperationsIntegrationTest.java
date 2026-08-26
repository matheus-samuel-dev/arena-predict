package com.bolao.copa.arena;

import static com.bolao.copa.arena.api.ExperienceDtos.PostRequest;
import static com.bolao.copa.arena.api.ExperienceDtos.ReportRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bolao.copa.arena.service.CommunityService;
import com.bolao.copa.arena.repository.AdminAuditRepository;
import com.bolao.copa.arena.repository.ArenaEventRepository;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminOperationsIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository users;
    @Autowired CommunityService community;
    @Autowired ArenaEventRepository events;
    @Autowired AdminAuditRepository audits;

    @Test
    void administratorReadsEveryOperationalResourceWithSafeShapes() throws Exception {
        User admin = users.findByEmailIgnoreCase("admin@arenapredict.com").orElseThrow();
        User player = users.findByEmailIgnoreCase("jogador@arenapredict.com").orElseThrow();
        var post = community.create(new PostRequest(
                "Publicação criada para validar a fila administrativa.", "Validação"), player);
        community.report(post.id(), new ReportRequest("Conteúdo enviado para revisão administrativa."), admin);
        String authorization = bearer(admin);

        String userBody = mockMvc.perform(get("/api/admin/users").param("size", "100")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].email").isString())
                .andExpect(jsonPath("$.content[0].role").isString())
                .andReturn().getResponse().getContentAsString();
        assertSafe(userBody);

        String poolBody = mockMvc.perform(get("/api/admin/pools")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ownerName").isString())
                .andExpect(jsonPath("$.content[0].participantCount").isNumber())
                .andReturn().getResponse().getContentAsString();
        assertSafe(poolBody);

        String championshipBody = mockMvc.perform(get("/api/admin/championships")
                        .param("search", "Brasileirão")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Brasileirão Série A"))
                .andExpect(jsonPath("$.content[0].sportName").value("Futebol"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();
        assertSafe(championshipBody);

        String eventBody = mockMvc.perform(get("/api/admin/events")
                        .param("search", "demo-football-live")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].externalKey").value("demo-football-live"))
                .andExpect(jsonPath("$.content[0].championship").value("Brasileirão Série A"))
                .andExpect(jsonPath("$.content[0].sport").value("Futebol"))
                .andExpect(jsonPath("$.content[0].homeCompetitor.id").isNumber())
                .andExpect(jsonPath("$.content[0].awayCompetitor.id").isNumber())
                .andExpect(jsonPath("$.content[0].format").value("STANDARD"))
                .andExpect(jsonPath("$.content[0].bestOf").value(1))
                .andReturn().getResponse().getContentAsString();
        assertSafe(eventBody);

        String marketBody = mockMvc.perform(get("/api/admin/markets")
                        .param("search", "demo-nba-open")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventTitle").value("Boston Celtics x Dallas Mavericks"))
                .andExpect(jsonPath("$.content[0].eventStatus").value("OPEN_FOR_PREDICTIONS"))
                .andExpect(jsonPath("$.content[0].optionCount").value(2))
                .andExpect(jsonPath("$.content[0].options[0].key").isString())
                .andExpect(jsonPath("$.content[0].options[0].multiplier").isNumber())
                .andReturn().getResponse().getContentAsString();
        assertSafe(marketBody);

        mockMvc.perform(get("/api/admin/scoring-rules").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].calculation").isString())
                .andExpect(jsonPath("$[0].unit").value("pontos virtuais"));

        mockMvc.perform(get("/api/admin/reports").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").isNumber())
                .andExpect(jsonPath("$[0].updatedAt").isString());

        String auditBody = mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").isString())
                .andExpect(jsonPath("$.content[0].actor").isString())
                .andReturn().getResponse().getContentAsString();
        assertSafe(auditBody);

        String settingsBody = mockMvc.perform(get("/api/admin/settings")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'virtual-points-policy')].value")
                        .value("SEM_VALOR_FINANCEIRO"))
                .andReturn().getResponse().getContentAsString();
        assertSafe(settingsBody);

        mockMvc.perform(get("/api/admin/moderation").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").isString())
                .andExpect(jsonPath("$.content[0].reason").isString());
    }

    @Test
    void participantGets403AndAnonymousGets401() throws Exception {
        User player = users.findByEmailIgnoreCase("jogador@arenapredict.com").orElseThrow();

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(player)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void participantCannotExecuteMutatingAdministrativeOperations() throws Exception {
        User player = users.findByEmailIgnoreCase("jogador@arenapredict.com").orElseThrow();
        String authorization = bearer(player);

        mockMvc.perform(post("/api/admin/sports")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"FORBIDDEN\",\"name\":\"Não permitido\",\"category\":\"TRADITIONAL\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/markets/1/status")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/events/1/result")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeScore\":1,\"awayScore\":0,\"finishEvent\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void successfulAdminMutationCreatesAppendOnlyAuditWithCorrelationId() throws Exception {
        User admin = users.findByEmailIgnoreCase("admin@arenapredict.com").orElseThrow();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String sportName = "Modalidade auditada " + suffix;
        String correlationId = "audit-" + suffix.toLowerCase();

        mockMvc.perform(post("/api/admin/sports")
                        .header("Authorization", bearer(admin))
                        .header("X-Correlation-Id", correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"AUDIT_" + suffix + "\",\"name\":\"" + sportName
                                + "\",\"category\":\"TRADITIONAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Correlation-Id", correlationId));

        mockMvc.perform(get("/api/admin/audit")
                        .param("search", sportName)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("SPORT_CREATED"))
                .andExpect(jsonPath("$.content[0].actor").value("Administrador Demo"))
                .andExpect(jsonPath("$.content[0].correlationId").value(correlationId));
    }

    @Test
    void resultRegistrationIsReplaySafeAndRejectsKeyReuseWithDifferentPayload() throws Exception {
        User admin = users.findByEmailIgnoreCase("admin@arenapredict.com").orElseThrow();
        var event = events.findByExternalKey("demo-nba-open").orElseThrow();
        event.setStartsAt(Instant.now().minusSeconds(60));
        events.saveAndFlush(event);
        String key = "result-" + UUID.randomUUID();
        int homeScore = 10_000 + Math.abs(key.hashCode() % 10_000);
        String payload = "{\"homeScore\":" + homeScore + ",\"awayScore\":1,\"finishEvent\":false}";
        long auditsBefore = resultAuditCount(event.getId());

        mockMvc.perform(put("/api/admin/events/{id}/result", event.getId())
                        .header("Authorization", bearer(admin))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(homeScore))
                .andExpect(jsonPath("$.awayScore").value(1));

        mockMvc.perform(put("/api/admin/events/{id}/result", event.getId())
                        .header("Authorization", bearer(admin))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeScore").value(homeScore));

        assertThat(resultAuditCount(event.getId()) - auditsBefore).isEqualTo(1);

        mockMvc.perform(put("/api/admin/events/{id}/result", event.getId())
                        .header("Authorization", bearer(admin))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeScore\":" + homeScore + ",\"awayScore\":2,\"finishEvent\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("dados diferentes")));
    }

    @Test
    void validationResponseIncludesEveryFieldErrorForInlineForms() throws Exception {
        User admin = users.findByEmailIgnoreCase("admin@arenapredict.com").orElseThrow();

        mockMvc.perform(post("/api/admin/sports")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"name\":\"\",\"category\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.fieldErrors.code").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("must"))))
                .andExpect(jsonPath("$.fieldErrors.name").isString())
                .andExpect(jsonPath("$.fieldErrors.category").isString());
    }

    private long resultAuditCount(Long eventId) {
        return audits.findAll().stream()
                .filter(entry -> "EVENT_RESULT_RECORDED".equals(entry.getAction()))
                .filter(entry -> eventId.toString().equals(entry.getResourceId()))
                .count();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generate(user);
    }

    private void assertSafe(String body) {
        assertThat(body).doesNotContain("passwordHash", "idempotencyKey", "inviteCode", "jwt.secret");
    }
}
