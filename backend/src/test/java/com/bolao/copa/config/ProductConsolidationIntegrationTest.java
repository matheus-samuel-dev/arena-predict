package com.bolao.copa.config;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bolao.copa.arena.api.ExperienceDtos.PostRequest;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.CommunityService;
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

/** Real JWT + controllers + persisted projections, including the public sandbox boundary. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ProductConsolidationIntegrationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired CommunityService community;
    @Autowired CommunityPostRepository posts;
    @Autowired CommunityLikeRepository likes;
    @Autowired com.bolao.copa.arena.service.ArenaPoolRankingService pools;

    @ParameterizedTest
    @ValueSource(strings = {"dashboard", "sports", "championships", "competitors", "events", "markets", "users", "pools", "scoring-rules", "challenges", "achievements", "notifications", "moderation", "reports", "audit", "settings", "sports-sync/status"})
    void allMaintainedBackofficeModulesHaveProtectedWorkingContracts(String resource) throws Exception {
        http.perform(get("/api/admin/" + resource).header("Authorization", token("ADMIN"))).andExpect(status().isOk());
        http.perform(get("/api/admin/" + resource).header("Authorization", token("PARTICIPANT"))).andExpect(status().isForbidden());
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                    org.springframework.http.HttpMethod.valueOf(method), "/api/admin/" + resource);
            http.perform(request.header("Authorization", token("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void demoUserProjectionMasksEmailAndCannotSearchByPrivateEmail() throws Exception {
        var regular = RegularTestUsers.participant(users);
        String result = http.perform(get("/api/admin/users").header("Authorization", token("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(result).doesNotContain(regular.getEmail(), "passwordHash", "token").contains("Protegido na demonstração");
        http.perform(get("/api/admin/users").param("search", regular.getEmail()).header("Authorization", token("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        http.perform(get("/api/admin/users").param("search", regular.getEmail()).header("Authorization", "Bearer " + jwt.generate(RegularTestUsers.admin(users))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].email").value(regular.getEmail()));
    }

    @ParameterizedTest @ValueSource(strings = {"dashboard", "events", "predictions", "wallet", "pools", "rankings", "challenges", "achievements", "community/posts", "profile", "notifications"})
    void participantModulesHaveRealReadContracts(String resource) throws Exception {
        http.perform(get("/api/" + resource).header("Authorization", token("PARTICIPANT"))).andExpect(status().isOk());
    }

    @Test void communityDemoPersistsLikesAndCommentsButRejectsRealPostIds() throws Exception {
        String auth = token("PARTICIPANT");
        var real = community.create(new PostRequest("Publicação real protegida", "Teste"), RegularTestUsers.participant(users));
        var demoPost = posts.findBySourceKey("demo-community-analysis").orElseThrow();
        http.perform(delete("/api/community/posts/" + demoPost.getId() + "/like").header("Authorization", auth)).andExpect(status().isOk());
        long before = likes.count();
        for (String action : List.of("like", "comments", "reports")) {
            http.perform(post("/api/community/posts/" + real.id() + "/" + action).header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Comentário\",\"reason\":\"Teste\"}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(likes.count()).isEqualTo(before);
        http.perform(post("/api/community/posts/" + demoPost.getId() + "/like").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.likedByCurrentUser").value(true));
        http.perform(post("/api/community/posts/" + demoPost.getId() + "/like").header("Authorization", auth)).andExpect(status().isOk());
        assertThat(likes.count()).isEqualTo(before + 1);
        http.perform(get("/api/community/posts").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].demo", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
        http.perform(post("/api/community/posts/" + demoPost.getId() + "/comments").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Análise demonstrativa persistida\"}"))
                .andExpect(status().isCreated());
        http.perform(get("/api/community/posts/" + demoPost.getId() + "/comments").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].content", org.hamcrest.Matchers.hasItem("Análise demonstrativa persistida")));
    }

    @Test void demoCannotJoinRealPoolByIdOrInviteAndCanCreateItsOwnSocialPool() throws Exception {
        var regularPool = pools.create(new com.bolao.copa.arena.api.ArenaDtos.PoolRequest(
                "Grupo pessoal real", null, null, null, true, 20, 0, "Regras virtuais", null, null), RegularTestUsers.participant(users));
        String auth = token("PARTICIPANT");
        http.perform(post("/api/pools/" + regularPool.id() + "/join").header("Authorization", auth)).andExpect(status().isForbidden());
        http.perform(post("/api/pools/join").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("inviteCode", regularPool.inviteCode())))).andExpect(status().isForbidden());
        assertThat(pools.get(regularPool.id(), RegularTestUsers.participant(users)).participantCount()).isEqualTo(1);
        http.perform(post("/api/pools").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Grupo Demo persistido\",\"rules\":\"Pontos virtuais do sandbox\",\"publicPool\":true,\"maxParticipants\":20}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.demo").value(true)).andExpect(jsonPath("$.owner").value(true));
    }

    @Test void demoOwnPreferencesWorkAndSharedPasswordStaysProtected() throws Exception {
        String auth = token("PARTICIPANT");
        http.perform(patch("/api/profile/preferences").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"light\",\"notifications\":false,\"publicProfile\":true}"))
                .andExpect(status().isOk());
        http.perform(patch("/api/profile/password").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"anything\",\"newPassword\":\"ChangedPassword123\"}"))
                .andExpect(status().isForbidden());
        http.perform(get("/api/rankings").param("source", "INVALID").header("Authorization", auth)).andExpect(status().isBadRequest());
        for (String source : List.of("REAL", "DEMO", "ALL"))
            http.perform(get("/api/rankings").param("source", source).header("Authorization", auth)).andExpect(status().isOk());
    }

    private String token(String profile) throws Exception {
        String result = http.perform(post("/api/auth/demo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"profile\":\"" + profile + "\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(result).get("token").asText();
    }
}
