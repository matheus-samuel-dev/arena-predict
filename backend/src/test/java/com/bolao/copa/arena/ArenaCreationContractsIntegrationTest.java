package com.bolao.copa.arena;

import static com.bolao.copa.arena.api.AdminEngagementDtos.*;
import static com.bolao.copa.arena.api.ArenaDtos.*;
import static com.bolao.copa.arena.api.ExperienceDtos.CommentRequest;
import static com.bolao.copa.arena.api.ExperienceDtos.PostRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArenaCreationContractsIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @Autowired UserRepository users;
    @Autowired SportRepository sports;
    @Autowired ChampionshipRepository championships;
    @Autowired CompetitorRepository competitors;
    @Autowired ArenaEventRepository events;
    @Autowired PredictionMarketRepository markets;
    @Autowired EventParticipantRepository eventParticipants;
    @Autowired ArenaPoolRepository pools;
    @Autowired ArenaPoolMemberRepository poolMembers;
    @Autowired CommunityPostRepository communityPosts;
    @Autowired CommunityLikeRepository communityLikes;
    @Autowired CommunityCommentRepository communityComments;
    @Autowired AchievementDefinitionRepository achievements;
    @Autowired ChallengeDefinitionRepository challenges;
    @Autowired ArenaNotificationRepository notifications;

    @Test
    @Transactional
    void administrativeCatalogCreationPersistsAndReturnsFriendlyDuplicateConflicts() throws Exception {
        User admin = admin();
        String suffix = suffix();
        var sportRequest = new SportRequest("PORTFOLIO-" + suffix, "Modalidade de contrato " + suffix,
                SportCategory.TRADITIONAL, "trophy", true, 40);
        long sportId = responseId(mockMvc.perform(post("/api/admin/sports")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(sportRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_" + suffix))
                .andReturn().getResponse().getContentAsString());
        assertThat(sports.findById(sportId)).isPresent();

        mockMvc.perform(post("/api/admin/sports")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(sportRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("modalidade")));

        Instant championshipStart = Instant.now().plusSeconds(86_400);
        var championshipRequest = new ChampionshipRequest(sportId, "Copa de contrato " + suffix,
                "Copa Ágil " + suffix, "2026", ChampionshipStatus.ACTIVE, null,
                championshipStart, championshipStart.plusSeconds(604_800));
        long championshipId = responseId(mockMvc.perform(post("/api/admin/championships")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(championshipRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("copa-agil-" + suffix.toLowerCase()))
                .andReturn().getResponse().getContentAsString());
        assertThat(championships.findById(championshipId)).isPresent();
        mockMvc.perform(post("/api/admin/championships")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(championshipRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("campeonato")));

        var homeRequest = new CompetitorRequest(sportId, "Equipe Aurora " + suffix, "AUR-" + suffix,
                null, "Brasil", true);
        var awayRequest = new CompetitorRequest(sportId, "Equipe Horizonte " + suffix, "HOR-" + suffix,
                null, "Portugal", true);
        long homeId = createCompetitor(admin, homeRequest);
        long awayId = createCompetitor(admin, awayRequest);
        assertThat(competitors.findById(homeId)).isPresent();
        mockMvc.perform(post("/api/admin/competitors")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(homeRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("participante")));

        Instant startsAt = Instant.now().plusSeconds(172_800);
        String externalKey = "contract-event-" + suffix.toLowerCase();
        var eventRequest = new EventRequest(externalKey, championshipId, homeId, awayId,
                "Aurora x Horizonte " + suffix, "Final", null, null, null, startsAt,
                startsAt.minusSeconds(1_800), EventStatus.OPEN_FOR_PREDICTIONS, EventFormat.STANDARD,
                1, true, true, List.of());
        long eventId = responseId(mockMvc.perform(post("/api/admin/events")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(eventRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andReturn().getResponse().getContentAsString());
        var persistedEvent = events.findById(eventId).orElseThrow();
        assertThat(eventParticipants.findByEventOrderByDisplayOrderAsc(persistedEvent)).hasSize(2);
        mockMvc.perform(post("/api/admin/events")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(eventRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("chave externa")));

        long otherSportId = sports.findByCodeIgnoreCase("FOOTBALL").orElseThrow().getId();
        var reassignedCompetitor = new CompetitorRequest(otherSportId, homeRequest.name(), homeRequest.code(),
                null, homeRequest.country(), true);
        mockMvc.perform(put("/api/admin/competitors/{id}", homeId)
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(reassignedCompetitor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("vinculada a eventos")));
        var reassignedChampionship = new ChampionshipRequest(otherSportId, championshipRequest.name(),
                championshipRequest.slug(), championshipRequest.season(), championshipRequest.status(), null,
                championshipRequest.startsAt(), championshipRequest.endsAt());
        mockMvc.perform(put("/api/admin/championships/{id}", championshipId)
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(reassignedChampionship)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("eventos vinculados")));

        var marketRequest = new MarketRequest(eventId, "MATCH-WINNER", "Vencedor do confronto",
                MarketStatus.OPEN, 10, List.of(
                new MarketOptionRequest("HOME", "Aurora", new BigDecimal("1.800"), true),
                new MarketOptionRequest("AWAY", "Horizonte", new BigDecimal("2.100"), true)));
        long marketId = responseId(mockMvc.perform(post("/api/admin/markets")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(marketRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.options.length()").value(2))
                .andReturn().getResponse().getContentAsString());
        assertThat(markets.findById(marketId)).isPresent();
        mockMvc.perform(post("/api/admin/markets")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(marketRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("mercado")));
    }

    @Test
    @Transactional
    void invalidNestedAndChronologicalPayloadsNeverReachTheDatabaseAsServerErrors() throws Exception {
        User admin = admin();
        User player = player();
        var football = sports.findByCodeIgnoreCase("FOOTBALL").orElseThrow();
        var brasileirao = championships.findBySportOrderByNameAsc(football).getFirst();
        var palmeiras = competitors.findBySportAndCodeIgnoreCase(football, "PAL").orElseThrow();
        Instant start = Instant.now().plusSeconds(86_400);

        var invalidChampionship = new ChampionshipRequest(football.getId(), "Período inválido", "invalid-" + suffix(),
                "2026", ChampionshipStatus.ACTIVE, null, start, start.minusSeconds(1));
        mockMvc.perform(post("/api/admin/championships")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalidChampionship)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", containsString("posterior")));

        var sameCompetitor = new EventRequest("same-competitor-" + suffix(), brasileirao.getId(),
                palmeiras.getId(), palmeiras.getId(), "Confronto inválido", null, null, null, null,
                start, start.minusSeconds(900), EventStatus.SCHEDULED, EventFormat.STANDARD, 1,
                false, true, List.of());
        mockMvc.perform(post("/api/admin/events")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(sameCompetitor)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", containsString("diferentes")));

        var openEvent = events.findByExternalKey("demo-nba-open").orElseThrow();
        var invalidMarket = new MarketRequest(openEvent.getId(), "INVALID_" + suffix(), "Mercado inválido",
                MarketStatus.OPEN, 10, List.of(
                new MarketOptionRequest("", "", BigDecimal.ONE, true),
                new MarketOptionRequest("NO", "Não", new BigDecimal("1.500"), true)));
        mockMvc.perform(post("/api/admin/markets")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalidMarket)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isMap());

        var equalDeadline = new EventRequest("equal-deadline-" + suffix(), brasileirao.getId(),
                palmeiras.getId(), competitors.findBySportAndCodeIgnoreCase(football, "FLA").orElseThrow().getId(),
                "Prazo inválido", null, null, null, null, start, start, EventStatus.SCHEDULED,
                EventFormat.STANDARD, 1, false, true, null);
        mockMvc.perform(post("/api/admin/events")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(equalDeadline)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", containsString("anterior")));

        var race = events.findByExternalKey("demo-f1-open").orElseThrow();
        mockMvc.perform(put("/api/admin/events/{id}/classification", race.getId())
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participants\":[null],\"finishEvent\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isMap());

        mockMvc.perform(patch("/api/profile")
                        .header("Authorization", bearer(player)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jogador Demo\",\"email\":\"jogador@arenapredict.com\",\"favoriteSports\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isMap());

        mockMvc.perform(patch("/api/profile")
                        .header("Authorization", bearer(player)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jogador Demo\",\"email\":\"jogador@arenapredict.com\","
                                + "\"avatarUrl\":\"https://tracker.invalid/avatar.png\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", containsString("galeria")));

        var invalidPool = new PoolRequest("Bolão com período inválido", null, null, brasileirao.getId(),
                true, 20, 0, "Pontuação exclusivamente virtual.", start, start.minusSeconds(1),
                PoolType.POOL, false);
        mockMvc.perform(post("/api/pools")
                        .header("Authorization", bearer(player)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalidPool)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", containsString("posterior")));

        var excessivePrize = new PoolRequest("Prêmio inválido", null, football.getId(), null,
                true, 20, 1_000_001, "Pontuação exclusivamente virtual.", start, start.plusSeconds(3_600),
                PoolType.POOL, false);
        mockMvc.perform(post("/api/pools")
                        .header("Authorization", bearer(player)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(excessivePrize)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.virtualPrizePoints").isString());
    }

    @Test
    @Transactional
    void participantPoolAndCommunityCreationsPersistWithCoherentRelations() throws Exception {
        User player = player();
        var football = sports.findByCodeIgnoreCase("FOOTBALL").orElseThrow();
        var brasileirao = championships.findBySportOrderByNameAsc(football).getFirst();
        String suffix = suffix();
        Instant start = Instant.now().plusSeconds(3_600);
        var poolRequest = new PoolRequest("Bolão da comunidade " + suffix, "  Temporada entre amigos.  ",
                null, brasileirao.getId(), true, 24, 250,
                "Somente pontos virtuais, sem valor financeiro.", start, start.plusSeconds(604_800),
                PoolType.POOL, false);
        long poolId = responseId(mockMvc.perform(post("/api/pools")
                        .header("Authorization", bearer(player)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(poolRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sport.id").value(football.getId()))
                .andExpect(jsonPath("$.joined").value(true))
                .andExpect(jsonPath("$.owner").value(true))
                .andReturn().getResponse().getContentAsString());
        var pool = pools.findById(poolId).orElseThrow();
        assertThat(pool.getSport().getId()).isEqualTo(football.getId());
        assertThat(pool.getDescription()).isEqualTo("Temporada entre amigos.");
        assertThat(poolMembers.findByPoolAndUser(pool, player)).isPresent();

        long postId = responseId(mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", bearer(player)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PostRequest("Análise da rodada da liga " + suffix + ".", "Brasileirão"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content", containsString(suffix)))
                .andReturn().getResponse().getContentAsString());
        assertThat(communityPosts.findById(postId)).isPresent();

        long commentId = responseId(mockMvc.perform(post("/api/community/posts/{id}/comments", postId)
                        .header("Authorization", bearer(player)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CommentRequest("Leitura registrada para a próxima rodada."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(postId))
                .andReturn().getResponse().getContentAsString());
        assertThat(communityComments.findById(commentId)).isPresent();

        mockMvc.perform(get("/api/community/posts").param("page", "0").param("size", "10")
                        .header("Authorization", bearer(player)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.first").value(true));

        mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", bearer(player)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PostRequest(" ", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").isString());
    }

    @Test
    @Transactional
    void engagementCreationsValidateDuplicatesPeriodsRecipientsAndPersistence() throws Exception {
        User admin = admin();
        User player = player();
        String suffix = suffix();
        var achievementRequest = new AchievementDefinitionRequest("PORTFOLIO-" + suffix,
                "Conquista de contrato " + suffix, "Valida persistência e duplicidade.", "RARE",
                AchievementRule.PREDICTION_COUNT, 5, 100, true);
        long achievementId = responseId(mockMvc.perform(post("/api/admin/achievements")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(achievementRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(achievements.findById(achievementId)).isPresent();
        mockMvc.perform(post("/api/admin/achievements")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(achievementRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("conquista")));

        Instant startsAt = Instant.now().plusSeconds(3_600);
        var challengeRequest = new ChallengeDefinitionRequest("CHALLENGE-" + suffix,
                "Desafio de contrato " + suffix, "Valida o período do desafio.",
                ChallengeMetric.PREDICTION_COUNT, 3, 80, startsAt, startsAt.plusSeconds(86_400), true);
        long challengeId = responseId(mockMvc.perform(post("/api/admin/challenges")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(challengeRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(challenges.findById(challengeId)).isPresent();
        var invalidChallenge = new ChallengeDefinitionRequest("INVALID-" + suffix,
                "Desafio inválido", "O período não é cronológico.", ChallengeMetric.WON_COUNT,
                1, 0, startsAt, startsAt, true);
        mockMvc.perform(post("/api/admin/challenges")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalidChallenge)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", containsString("depois")));

        var invalidCode = new AchievementDefinitionRequest("---", "Código inválido",
                "Não deve chegar à constraint do banco.", "COMMON", AchievementRule.FIRST_WIN,
                1, 0, true);
        mockMvc.perform(post("/api/admin/achievements")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalidCode)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", containsString("letras ou números")));

        long notificationsBefore = notifications.count();
        var notificationRequest = new AdminNotificationRequest(player.getId(), NotificationType.ADMIN_NOTICE,
                "Agenda atualizada", "Confira os novos eventos disponíveis.", "  /events  ");
        mockMvc.perform(post("/api/admin/notifications")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(notificationRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipients").value(1))
                .andExpect(jsonPath("$.scope").value("USER"));
        assertThat(notifications.count()).isEqualTo(notificationsBefore + 1);
        assertThat(notifications.findAll()).anyMatch(value -> "/events".equals(value.getTargetUrl()));

        var externalTarget = new AdminNotificationRequest(player.getId(), NotificationType.ADMIN_NOTICE,
                "Destino inválido", "A URL externa não deve ser persistida.", "//tracker.invalid");
        mockMvc.perform(post("/api/admin/notifications")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(externalTarget)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", containsString("rota interna")));
        assertThat(notifications.count()).isEqualTo(notificationsBefore + 1);

        var missingRecipient = new AdminNotificationRequest(Long.MAX_VALUE, NotificationType.ADMIN_NOTICE,
                "Destino inexistente", "Esta mensagem não deve ser persistida.", null);
        mockMvc.perform(post("/api/admin/notifications")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(missingRecipient)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("Usuário")));
    }

    @Test
    void participantCannotCreateAdministrativeResourcesAndAnonymousCannotCreateParticipantResources() throws Exception {
        var request = new SportRequest("DENIED_" + suffix(), "Sem permissão",
                SportCategory.TRADITIONAL, null, true, 1);
        mockMvc.perform(post("/api/admin/sports")
                        .header("Authorization", bearer(player())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/pools").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void communityReactionEndpointsTogglePersistentlyAndRequireAuthentication() throws Exception {
        var player = player();
        long postId = responseId(mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", bearer(player)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PostRequest("Publicação para validar o ciclo completo da curtida.", "Teste"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/community/posts/{id}/like", postId)
                        .header("Authorization", bearer(player)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByCurrentUser").value(true));
        mockMvc.perform(post("/api/community/posts/{id}/like", postId)
                        .header("Authorization", bearer(player)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(delete("/api/community/posts/{id}/like", postId)
                        .header("Authorization", bearer(player)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.likedByCurrentUser").value(false));
        mockMvc.perform(delete("/api/community/posts/{id}/like", postId)
                        .header("Authorization", bearer(player)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0));
        assertThat(communityLikes.countByPost(communityPosts.findById(postId).orElseThrow())).isZero();

        mockMvc.perform(post("/api/community/posts/{id}/like", postId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/community/posts/{id}/like", postId))
                .andExpect(status().isUnauthorized());
    }

    private long createCompetitor(User admin, CompetitorRequest request) throws Exception {
        return responseId(mockMvc.perform(post("/api/admin/competitors")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private User admin() { return users.findByEmailIgnoreCase("admin@arenapredict.com").orElseThrow(); }
    private User player() { return users.findByEmailIgnoreCase("jogador@arenapredict.com").orElseThrow(); }
    private String bearer(User user) { return "Bearer " + jwtService.generate(user); }
    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private long responseId(String body) throws Exception { return objectMapper.readTree(body).path("id").asLong(); }
    private String suffix() { return UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
}
