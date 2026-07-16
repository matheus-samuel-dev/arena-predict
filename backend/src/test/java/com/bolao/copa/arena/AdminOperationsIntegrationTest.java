package com.bolao.copa.arena;

import static com.bolao.copa.arena.api.ExperienceDtos.PostRequest;
import static com.bolao.copa.arena.api.ExperienceDtos.ReportRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bolao.copa.arena.service.CommunityService;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminOperationsIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository users;
    @Autowired CommunityService community;

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
                        .param("search", "Palmeiras x Flamengo")
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

    private String bearer(User user) {
        return "Bearer " + jwtService.generate(user);
    }

    private void assertSafe(String body) {
        assertThat(body).doesNotContain("passwordHash", "idempotencyKey", "inviteCode", "jwt.secret");
    }
}
