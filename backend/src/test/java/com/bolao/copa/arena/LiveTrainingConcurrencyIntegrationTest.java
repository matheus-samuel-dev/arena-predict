package com.bolao.copa.arena;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.bolao.copa.arena.api.ArenaDtos.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.provider.*;
import com.bolao.copa.arena.service.sync.*;
import com.bolao.copa.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Committed disposable fixtures are needed across request threads; cleanup removes only this test's IDs. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class LiveTrainingConcurrencyIntegrationTest {
    @Autowired MockMvc http;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;@Autowired JwtService jwt;
    @Autowired SportsCatalogSyncService catalog;@Autowired SportsMatchSyncService sync;@Autowired ArenaCatalogService api;
    @Autowired PlatformTransactionManager transactions;
    private final String fixture=UUID.randomUUID().toString().substring(0,10);
    private Long eventId;private String session;private String token;private MarketResponse market;
    private void prepare() throws Exception {
        var source=new SportsMatch(fixture,"Fixture concorrente de treino",new SportsTeam("h-"+fixture,"A",null,null),new SportsTeam("a-"+fixture,"B",null,null),
                new SportsChampionship("c-"+fixture,"Teste concorrente",null,"2026",null,null,null,null,null),Instant.now(),null,EventStatus.LIVE,null,null,3,null,false,false,false,"CS2");
        sync.synchronize("PANDASCORE",source,catalog.synchronize("PANDASCORE",List.of(source)));
        eventId=db.queryForObject("select id from arena_events where external_provider='PANDASCORE' and external_id=?",Long.class,fixture);
        market=api.eventResponse(eventId).markets().getFirst();
        var result=http.perform(post("/api/auth/demo").contentType(MediaType.APPLICATION_JSON).content("{\"profile\":\"PARTICIPANT\",\"training\":true}")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        token=json.readTree(result.getResponse().getContentAsString()).get("token").asText();session=jwt.parse(token).trainingSession();
    }
    private List<MvcResult> race(int stake,String firstKey,String secondKey) throws Exception {
        var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<MvcResult>> pending=new ArrayList<>();
            for(String key:List.of(firstKey,secondKey)) pending.add(pool.submit(()->{
                var option=market.options().getFirst();var request=new PlacePredictionRequest(eventId,market.id(),option.id(),stake,null,key,option.multiplier());
                ready.countDown();if(!go.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Race barrier expired");
                return http.perform(post("/api/training/predictions").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andReturn();
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();
            List<MvcResult> results=new ArrayList<>();for(var future:pending)results.add(future.get(20,TimeUnit.SECONDS));return results;
        }
    }
    @Test void simultaneousRetryCreatesOnePredictionAndOneDebit() throws Exception {
        prepare();var results=race(25,"same","same");
        assertThat(results).allSatisfy(r->assertThat(r.getResponse().getStatus()).isEqualTo(201));
        assertThat(json.readTree(results.get(0).getResponse().getContentAsString()).get("id"))
                .isEqualTo(json.readTree(results.get(1).getResponse().getContentAsString()).get("id"));
        assertThat(db.queryForObject("select count(*) from demo_training_predictions where session_id=?",Long.class,session)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from demo_training_ledger where session_id=? and type='PREDICTION_PLACED'",Long.class,session)).isEqualTo(1);
        assertThat(db.queryForObject("select balance from demo_training_sessions where id=?",Long.class,session)).isEqualTo(4975);
    }
    @Test void simultaneousDifferentIntentsCannotSpendTheSameBalanceTwice() throws Exception {
        prepare();var results=race(3000,"first","second");
        assertThat(results.stream().map(r->r.getResponse().getStatus()).toList()).containsExactlyInAnyOrder(201,422);
        assertThat(db.queryForObject("select balance from demo_training_sessions where id=?",Long.class,session)).isEqualTo(2000);
        assertThat(db.queryForObject("select count(*) from demo_training_predictions where session_id=?",Long.class,session)).isEqualTo(1);
    }
    @AfterEach void cleanupOnlyCommittedFixture() {
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            if(session!=null) {
                db.update("delete from demo_training_ledger where session_id=?",session);
                db.update("delete from demo_training_predictions where session_id=?",session);
                db.update("delete from demo_training_sessions where id=?",session);
            }
            if(eventId!=null) {
                db.update("delete from arena_market_options where market_id in (select id from arena_markets where event_id=?)",eventId);
                db.update("delete from arena_markets where event_id=?",eventId);
                db.update("delete from arena_event_participants where event_id=?",eventId);
                db.update("delete from arena_events where id=?",eventId);
            }
            db.update("delete from arena_championships where external_provider='PANDASCORE' and external_id=?","c-"+fixture);
            db.update("delete from arena_competitors where external_provider='PANDASCORE' and external_id in (?,?)","h-"+fixture,"a-"+fixture);
        });
    }
}
