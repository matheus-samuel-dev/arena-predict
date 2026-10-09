package com.bolao.copa.arena;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.bolao.copa.arena.api.ArenaDtos.*;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.provider.*;
import com.bolao.copa.arena.service.sync.*;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.security.JwtService;
import com.bolao.copa.support.RegularTestUsers;
import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class LiveTrainingIntegrationTest {
    @Autowired MockMvc http; @Autowired ObjectMapper json; @Autowired JdbcTemplate db;
    @Autowired SportsCatalogSyncService catalog; @Autowired SportsMatchSyncService sync;
    @Autowired ArenaEventRepository events; @Autowired PredictionMarketRepository markets;
    @Autowired MarketOptionRepository options; @Autowired ArenaPredictionRepository normalPredictions;
    @Autowired ArenaPredictionService normalCommands; @Autowired ArenaCatalogService api;
    @Autowired PointWalletService wallets; @Autowired UserRepository users; @Autowired JwtService jwt;
    private final String fixture=UUID.randomUUID().toString().substring(0,10);
    private SportsMatch snapshot(EventStatus status,Integer home,Integer away,boolean score) {
        var h=new SportsTeam("h-"+fixture,"Equipe de teste A",null,null);
        var a=new SportsTeam("a-"+fixture,"Equipe de teste B",null,null);
        return new SportsMatch(fixture,"Fixture de treino isolado",h,a,new SportsChampionship("training-champ-"+fixture,"Teste",null,"2026",null,null,null,null,null),
                Instant.now().plusSeconds(3600),status==EventStatus.FINISHED?Instant.now():null,status,home,away,3,
                status==EventStatus.FINISHED&&home!=null&&away!=null?(home>away?h.externalId():a.externalId()):null,false,false,score,"CS2");
    }
    private ArenaEvent apply(EventStatus status,Integer h,Integer a,boolean score) {
        var source=snapshot(status,h,a,score);sync.synchronize("PANDASCORE",source,catalog.synchronize("PANDASCORE",List.of(source)));
        return events.findByExternalProviderAndExternalId("PANDASCORE",fixture).orElseThrow();
    }
    private String enter() throws Exception {
        var result=http.perform(post("/api/auth/demo").contentType(MediaType.APPLICATION_JSON).content("{\"profile\":\"PARTICIPANT\",\"training\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.demoTraining").value(true)).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
    private PlacePredictionRequest request(ArenaEvent event,String optionKey,String key) {
        var market=markets.findByEventOrderByIdAsc(event).stream().filter(m->"SERIES_WINNER_LIVE".equals(m.getTemplateCode())).findFirst().orElseThrow();
        var option=options.findByMarketAndKey(market,optionKey).orElseThrow();
        return new PlacePredictionRequest(event.getId(),market.getId(),option.getId(),25,null,key,api.marketResponse(market).options().stream().filter(o->o.id().equals(option.getId())).findFirst().orElseThrow().multiplier());
    }
    private JsonNode place(String token,PlacePredictionRequest request,int code) throws Exception {
        var result=http.perform(post("/api/training/predictions").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().is(code)).andReturn();return json.readTree(result.getResponse().getContentAsString());
    }
    @Test void twoVisitorsPersistSeparateWalletsAndCannotReadOrWriteTheSharedAccount() throws Exception {
        var event=apply(EventStatus.LIVE,0,0,true);String first=enter(),second=enter();
        assertThat(jwt.parse(first).trainingSession()).isNotEqualTo(jwt.parse(second).trainingSession());
        long normalCount=normalPredictions.count();var row=place(first,request(event,"HOME","one"),201);
        assertThat(row.get("demo").asBoolean()).isTrue();assertThat(row.get("canCancel").asBoolean()).isFalse();
        http.perform(get("/api/training/predictions?sessionId="+jwt.parse(first).trainingSession()).header("Authorization","Bearer "+second)).andExpect(jsonPath("$.length()").value(0));
        http.perform(get("/api/training/wallet").header("Authorization","Bearer "+first)).andExpect(jsonPath("$.balance").value(4975));
        http.perform(get("/api/training/wallet").header("Authorization","Bearer "+second)).andExpect(jsonPath("$.balance").value(5000));
        for(String path:List.of("/api/wallet","/api/predictions","/api/demo/scenario","/api/admin/users"))
            http.perform(get(path).header("Authorization","Bearer "+first)).andExpect(status().isForbidden());
        for(String path:List.of("/api/predictions","/api/demo/reset","/api/demo/events/1/result","/api/community/posts"))
            http.perform(post(path).header("Authorization","Bearer "+first).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        assertThat(normalPredictions.count()).isEqualTo(normalCount);assertThat(event.isDemo()).isFalse();assertThat(event.getStatus()).isEqualTo(EventStatus.LIVE);
    }
    @Test void officialResultPaysExactlyOnceAndLeavesNormalWalletAndHistoryUntouched() throws Exception {
        var event=apply(EventStatus.LIVE,0,0,true);String winner=enter(),loser=enter();
        var regular=RegularTestUsers.freshParticipant(users);normalCommands.place(request(event,"HOME","normal"),"normal",regular);
        long beforeNormalCount=normalPredictions.count();long normalInitial=wallets.wallet(regular).balance();
        var won=place(winner,request(event,"HOME","win"),201);place(loser,request(event,"AWAY","lose"),201);
        for(int n=0;n<10;n++)apply(EventStatus.FINISHED,2,1,false);
        http.perform(get("/api/training/predictions").header("Authorization","Bearer "+winner)).andExpect(jsonPath("$[0].status").value("WON")).andExpect(jsonPath("$[0].rewardedPoints").value(50));
        http.perform(get("/api/training/wallet").header("Authorization","Bearer "+winner)).andExpect(jsonPath("$.balance").value(5025));
        http.perform(get("/api/training/predictions").header("Authorization","Bearer "+loser)).andExpect(jsonPath("$[0].status").value("LOST")).andExpect(jsonPath("$[0].rewardedPoints").value(0));
        assertThat(db.queryForObject("select count(*) from demo_training_ledger where session_id=? and type='PREDICTION_WON'",Long.class,jwt.parse(winner).trainingSession())).isEqualTo(1);
        assertThat(normalPredictions.count()).isEqualTo(beforeNormalCount);
        assertThat(normalCommands.list(regular)).hasSize(1).allSatisfy(p->assertThat(p.demo()).isFalse());
        // The normal official winner receives only its own normal reward/conquest, never the training credit.
        long normalAfter=wallets.wallet(regular).balance();apply(EventStatus.FINISHED,2,1,false);
        assertThat(wallets.wallet(regular).balance()).isEqualTo(normalAfter).isGreaterThan(normalInitial);
        assertThat(db.queryForObject("select rewarded_points from demo_training_predictions where id=?",Integer.class,won.get("id").asLong())).isEqualTo(50);
    }
    @Test void retryIsSingleDebitAndConflictingPayloadOrInsufficientBalanceRollBack() throws Exception {
        var event=apply(EventStatus.LIVE,0,0,true);String token=enter();var request=request(event,"HOME","retry");
        var first=place(token,request,201);var retry=place(token,request,201);assertThat(retry.get("id")).isEqualTo(first.get("id"));
        place(token,request(event,"AWAY","retry"),409);
        var tooMuch=new PlacePredictionRequest(event.getId(),request.marketId(),request.optionId(),6000,null,"poor",request.expectedMultiplier());
        place(token,tooMuch,422);
        http.perform(get("/api/training/wallet").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.balance").value(4975));
        assertThat(db.queryForObject("select count(*) from demo_training_predictions where session_id=?",Long.class,jwt.parse(token).trainingSession())).isEqualTo(1);
    }
    @Test void officialCancellationRefundsOnce() throws Exception {
        var event=apply(EventStatus.LIVE,0,0,true);String token=enter();place(token,request(event,"HOME","cancel"),201);
        apply(EventStatus.CANCELLED,null,null,false);apply(EventStatus.CANCELLED,null,null,false);
        http.perform(get("/api/training/predictions").header("Authorization","Bearer "+token)).andExpect(jsonPath("$[0].status").value("REFUNDED"));
        http.perform(get("/api/training/wallet").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.balance").value(5000));
        assertThat(db.queryForObject("select count(*) from demo_training_ledger where session_id=? and type='REFUND'",Long.class,jwt.parse(token).trainingSession())).isEqualTo(1);
    }
    @Test void closedSuspendedStaleOrFinishedMarketsCannotAcceptPredictions() throws Exception {
        var event=apply(EventStatus.LIVE,1,0,true);String token=enter();var request=request(event,"HOME","blocked");
        var market=markets.findById(request.marketId()).orElseThrow();market.setStatus(MarketStatus.SUSPENDED);place(token,request,422);
        market.setStatus(MarketStatus.CLOSED);place(token,request,422);
        market.setStatus(MarketStatus.OPEN);event.setLastSyncedAt(Instant.now().minusSeconds(301));place(token,request,422);
        event.setLastSyncedAt(Instant.now());var option=options.findById(request.optionId()).orElseThrow();option.setActive(false);place(token,request,422);
        option.setActive(true);apply(EventStatus.FINISHED,2,1,false);place(token,request,422);
        http.perform(get("/api/training/wallet").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.balance").value(5000));
    }
    @Test void trainingEndpointsRejectNormalLegacyAdminAndExpiredSessions() throws Exception {
        var regular=RegularTestUsers.freshParticipant(users);
        http.perform(get("/api/training/wallet").header("Authorization","Bearer "+jwt.generate(regular))).andExpect(status().isForbidden());
        http.perform(post("/api/auth/demo").contentType(MediaType.APPLICATION_JSON).content("{\"profile\":\"ADMIN\",\"training\":true}")).andExpect(status().isForbidden());
        String token=enter();db.update("update demo_training_sessions set expires_at=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),jwt.parse(token).trainingSession());
        http.perform(get("/api/training/wallet").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void staleLiveScoreIsHiddenByApiWhileOfficialFinishedScoreRemainsVisible() {
        var event=apply(EventStatus.LIVE,1,0,true);event.setLastSyncedAt(Instant.now().minusSeconds(301));
        var result=api.eventResponse(event);assertThat(result.homeScore()).isNull();assertThat(result.awayScore()).isNull();assertThat(result.liveScoreAvailable()).isFalse();
        apply(EventStatus.FINISHED,2,1,false);event.setLastSyncedAt(Instant.now().minusSeconds(301));
        assertThat(api.eventResponse(event).homeScore()).isEqualTo(2);
    }
    @Test void incompleteOfficialResultNeverPaysUntilConfirmed() throws Exception {
        var event=apply(EventStatus.LIVE,0,0,true);String token=enter();place(token,request(event,"HOME","pending"),201);
        apply(EventStatus.FINISHED,null,null,false);
        http.perform(get("/api/training/predictions").header("Authorization","Bearer "+token)).andExpect(jsonPath("$[0].status").value("ACTIVE"));
        http.perform(get("/api/training/wallet").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.balance").value(4975));
        apply(EventStatus.FINISHED,2,1,false);
        http.perform(get("/api/training/predictions").header("Authorization","Bearer "+token)).andExpect(jsonPath("$[0].status").value("WON"));
    }
}
