package com.bolao.copa.arena;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.bolao.copa.arena.api.ArenaDtos.*;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.provider.*;
import com.bolao.copa.arena.service.sync.*;
import com.bolao.copa.repository.UserRepository;
import com.bolao.copa.support.RegularTestUsers;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Source fixtures are explicit, isolated and rolled back; no live token or provider network involved. */
@SpringBootTest @ActiveProfiles("test") @Transactional
class ProbabilityPricingIntegrationTest {
    @MockBean ConfirmedSeriesHistoryReader reader;
    @Autowired HistoricalTeamStrengthService strength;@Autowired SportsCatalogSyncService catalog;@Autowired SportsMatchSyncService sync;
    @Autowired ArenaEventRepository events;@Autowired PredictionMarketRepository markets;@Autowired MarketOptionRepository options;
    @Autowired ArenaPredictionRepository predictions;@Autowired ArenaPredictionService commands;@Autowired UserRepository users;
    @Autowired ArenaCatalogService api;@Autowired MarketAvailabilityService availability;@Autowired PointLedgerRepository ledger;
    @Autowired PricingSnapshotCodec codec;
    private final String id=UUID.randomUUID().toString().substring(0,10);
    @BeforeEach void resetSourceCache() { when(reader.read()).thenReturn(List.of());strength.refresh(); }
    private SportsMatch snapshot(EventStatus status,int h,int a,boolean score) {
        var home=new SportsTeam("h-"+id,"Nome sem efeito A",null,null);var away=new SportsTeam("a-"+id,"Nome sem efeito B",null,null);
        return new SportsMatch(id,"Fixture de modelo",home,away,new SportsChampionship("c-"+id,"Modelo teste",null,"2026",null,null,null,null,null),
                Instant.now().minusSeconds(300),status==EventStatus.FINISHED?Instant.now():null,status,h,a,3,
                status==EventStatus.FINISHED?(h>a?home.externalId():away.externalId()):null,false,false,score,"CS2");
    }
    private ArenaEvent apply(EventStatus status,int h,int a,boolean score) {
        var source=snapshot(status,h,a,score);sync.synchronize("PANDASCORE",source,catalog.synchronize("PANDASCORE",List.of(source)));
        return events.findByExternalProviderAndExternalId("PANDASCORE",id).orElseThrow();
    }
    private PredictionMarket winner(ArenaEvent event) { return markets.findByEventOrderByIdAsc(event).stream().filter(m->"SERIES_WINNER_LIVE".equals(m.getTemplateCode())).findFirst().orElseThrow(); }
    private List<BradleyTerryStrengthModel.Result> strongHistory() {
        var rows=new ArrayList<BradleyTerryStrengthModel.Result>();Instant ended=Instant.now().minusSeconds(3600);
        for(int n=0;n<10;n++)rows.add(new BradleyTerryStrengthModel.Result("past"+n,"CS2","h-"+id,"a-"+id,2,0,3,ended.minusSeconds(7200),ended,ended.plusSeconds(60),1));return rows;
    }
    @Test void probabilitiesConfidencePolicyAndDisplayedRewardsAreIndependentApiFields() {
        var event=apply(EventStatus.LIVE,1,0,true);var response=api.marketResponse(winner(event));var p=response.pricing();
        assertThat(p.available()).isTrue();assertThat(p.probabilities().get("HOME")).isCloseTo(.75,within(1e-12));
        assertThat(p.probabilities().get("AWAY")).isCloseTo(.25,within(1e-12));assertThat(p.confidence()).isEqualTo("LOW");
        assertThat(p.evidenceSource()).isEqualTo("SYMMETRIC_PRIOR");assertThat(p.rewardPolicy()).isEqualTo(VirtualRewardPolicy.VERSION);
        assertThat(response.options().stream().filter(o->o.key().equals("HOME")).findFirst().orElseThrow().multiplier()).isEqualByComparingTo("1.33");
        assertThat(response.options().stream().filter(o->o.key().equals("AWAY")).findFirst().orElseThrow().multiplier()).isEqualByComparingTo("4.00");
    }
    @Test void verifiedOpponentHistoryChangesOnlyNewQuotesAndFinalPaymentUsesTheFrozenSnapshot() {
        var event=apply(EventStatus.LIVE,0,0,true);var market=winner(event);var user=RegularTestUsers.freshParticipant(users);
        var result=commands.place(new PlacePredictionRequest(event.getId(),market.getId(),options.findByMarketAndKey(market,"HOME").orElseThrow().getId(),25,null,"frozen"),"frozen",user);
        var persisted=predictions.findById(result.id()).orElseThrow();String accepted=persisted.getPricingSnapshot();
        assertThat(persisted.getMultiplier()).isEqualByComparingTo("2.00");assertThat(codec.decode(accepted).probabilities().get("HOME")).isEqualTo(.5);
        when(reader.read()).thenReturn(strongHistory());strength.refresh();
        apply(EventStatus.LIVE,1,0,true);var fresh=api.marketResponse(market);
        assertThat(fresh.pricing().evidenceSource()).isEqualTo("PANDASCORE_CONFIRMED_RESULTS");assertThat(fresh.pricing().homeSamples()).isEqualTo(10);
        assertThat(fresh.pricing().probabilities().get("HOME")).isGreaterThan(.9);
        assertThat(persisted.getPricingSnapshot()).isEqualTo(accepted);assertThat(persisted.getPotentialPoints()).isEqualTo(50);
        for(int n=0;n<10;n++)apply(EventStatus.FINISHED,2,1,false);
        assertThat(persisted.getRewardedPoints()).isEqualTo(50);assertThat(persisted.getMultiplier()).isEqualByComparingTo("2");
        assertThat(persisted.getPricingSnapshot()).isEqualTo(accepted);assertThat(ledger.findByIdempotencyKey("prediction-win:"+result.id())).get().extracting(PointLedgerEntry::getAmount).isEqualTo(50L);
    }
    @Test void futureOrOldHistoryNeverRaisesConfidenceAndStableInputsHaveStablePrices() {
        var event=apply(EventStatus.LIVE,0,0,true);var market=winner(event);var old=strongHistory().stream().map(r->new BradleyTerryStrengthModel.Result(r.id(),r.sport(),r.home(),r.away(),2,0,3,
                Instant.now().minusSeconds(40*86400),Instant.now().minusSeconds(35*86400),Instant.now().minusSeconds(35*86400),1)).toList();
        when(reader.read()).thenReturn(old);strength.refresh();
        var before=api.marketResponse(market).pricing();assertThat(before.evidenceSource()).isEqualTo("SYMMETRIC_PRIOR");assertThat(before.homeSamples()).isZero();
        event.setLastSyncedAt(event.getLastSyncedAt().plusSeconds(1));
        var after=api.marketResponse(market).pricing();assertThat(after.probabilities()).isEqualTo(before.probabilities());assertThat(after.dataRevision()).isEqualTo(before.dataRevision());
        verify(reader,times(2)).read(); // Only explicit refreshes; API pricing never opens another database transaction.
        var future=strongHistory().stream().map(r->new BradleyTerryStrengthModel.Result(r.id(),r.sport(),r.home(),r.away(),2,0,3,r.startsAt(),Instant.now().plusSeconds(3600),Instant.now().plusSeconds(3601),1)).toList();
        when(reader.read()).thenReturn(future);strength.refresh();
        assertThat(api.marketResponse(market).pricing().homeSamples()).isZero();
    }
    @Test void missingScoreOrStaleSnapshotSuspendsPricingAndFreshStateRestoresIt() {
        var event=apply(EventStatus.LIVE,0,0,true);var market=winner(event);event.setLiveScoreAvailable(false);
        assertThat(availability.evaluate(market).code()).isEqualTo("PRICING_DATA_REQUIRED");assertThat(api.marketResponse(market).pricing().confidence()).isEqualTo("NONE");
        event.setLiveScoreAvailable(true);event.setLastSyncedAt(Instant.now().minusSeconds(301));
        assertThat(availability.evaluate(market).allowed()).isFalse();assertThat(api.marketResponse(market).pricing().available()).isFalse();
        event.setLastSyncedAt(Instant.now());assertThat(availability.evaluate(market).allowed()).isTrue();
    }
    @Test void unavailableHistoricalStorageFallsBackWithoutProviderCallOrFalseConfidence() {
        when(reader.read()).thenThrow(new DataAccessResourceFailureException("isolated fixture"));
        strength.refresh();
        var event=apply(EventStatus.LIVE,0,0,true);var p=api.marketResponse(winner(event)).pricing();
        assertThat(p.evidenceSource()).isEqualTo("SYMMETRIC_PRIOR");assertThat(p.confidence()).isEqualTo("LOW");assertThat(p.limitations()).contains("HISTORY_NOT_READY");
    }
}
