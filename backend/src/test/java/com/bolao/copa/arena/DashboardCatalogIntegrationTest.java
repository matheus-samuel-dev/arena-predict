package com.bolao.copa.arena;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Rolled back fixtures; asserts bounded data acquisition rather than a machine-dependent stopwatch. */
@SpringBootTest @ActiveProfiles("test") @Transactional
class DashboardCatalogIntegrationTest {
    @Autowired ArenaCatalogService catalog;
    @Autowired ArenaEventRepository events;
    @SpyBean PredictionMarketRepository markets;
    @Autowired MarketOptionRepository options;
    @Autowired ChampionshipRepository championships;
    @Autowired ArenaPredictionRepository predictions;
    @Autowired ArenaDashboardService dashboards;
    @Autowired com.bolao.copa.repository.UserRepository users;

    @Test
    void performanceUsesTheChronologyOfResultsRatherThanSubmissionOrder() {
        var player = users.findByEmailIgnoreCase("jogador@arenapredict.com").orElseThrow();
        var settled = predictions.findByUserOrderByPlacedAtDesc(player).stream()
                .filter(p -> p.getStatus() == PredictionStatus.WON || p.getStatus() == PredictionStatus.LOST).toList();
        var now = Instant.now();
        settled.get(0).setResolvedAt(now.minusSeconds(7200));
        settled.get(1).setResolvedAt(now.minusSeconds(3600));
        predictions.flush();
        assertThat(dashboards.dashboard(player).performance().stream().map(point -> point.date()).toList()).isSorted();
    }

    @Test
    void legacyYearOnlyChampionshipUsesLeagueIdentityInBothApiRepresentations() {
        var championship = new Championship();
        championship.setSport(events.findAll().getFirst().getChampionship().getSport());
        championship.setSlug("championship-name-regression");
        championship.setExternalProvider("PANDASCORE");
        championship.setExternalId("championship-name-regression");
        championship.setName("2026");
        championship.setLeagueName("Thunderpick World Championship");
        championship.setSeriesName("2026");
        championships.saveAndFlush(championship);
        var event = new ArenaEvent();
        event.setChampionship(championship);
        event.setTitle("Championship name regression");
        event.setExternalKey("championship-name-regression");
        event.setExternalProvider("PANDASCORE");
        event.setExternalId("championship-name-regression");
        event.setStartsAt(Instant.now().plusSeconds(3600));
        event.setPredictionClosesAt(event.getStartsAt());
        events.saveAndFlush(event);
        assertThat(catalog.eventResponse(event).championship()).isEqualTo("Thunderpick World Championship · 2026");
        assertThat(catalog.championshipResponse(championship).name()).isEqualTo("Thunderpick World Championship · 2026");
        // Reading the corrected display name does not rewrite historical/provider data.
        assertThat(championship.getName()).isEqualTo("2026");
        var manual = new Championship();
        manual.setSport(championship.getSport());
        manual.setName("2026");
        manual.setLeagueName("Manual league");
        assertThat(catalog.championshipResponse(manual).name()).isEqualTo("2026");
    }

    private List<ArenaEvent> fixtures() {
        var existing = events.findAll().getFirst();
        // Existing fixtures must not affect ordering in these explicit selection scenarios.
        events.findAll().forEach(e -> { e.setStatus(EventStatus.FINISHED); e.setFeatured(false); });
        var created = new ArrayList<ArenaEvent>();
        var start = Instant.now().plusSeconds(3600);
        for (int i = 0; i < 120; i++) {
            var event = new ArenaEvent(); event.setExternalKey("dashboard-fixture-" + UUID.randomUUID());
            event.setTitle("Isolated dashboard selection " + i); event.setChampionship(existing.getChampionship());
            event.setHomeCompetitor(existing.getHomeCompetitor()); event.setAwayCompetitor(existing.getAwayCompetitor());
            event.setStartsAt(start.plusSeconds(i)); event.setPredictionClosesAt(start.plusSeconds(i));
            event.setStatus(EventStatus.SCHEDULED); event.setFeatured(true); created.add(events.save(event));
        }
        events.flush(); clearInvocations(markets); return created;
    }
    @Test void upcomingLimitIsAppliedBeforeMarketsAreLoadedAndArchivedEventsStayExcluded() {
        var created = fixtures(); created.getFirst().setDemo(true); created.getFirst().setDemoArchived(true); events.flush();
        var selected = catalog.scheduledEvents();
        assertThat(selected).extracting(e -> e.id()).containsExactlyElementsOf(created.subList(1, 9).stream().map(ArenaEvent::getId).toList());
        verify(markets).findByEventInOrderByEventIdAscIdAsc(argThat(values -> values.size() == 8));
        verifyNoMoreInteractions(markets);
    }
    @Test void normalUpcomingIncludesOpenPredictionsWhileTrainingPreservesScheduledSelection() {
        var created = fixtures(); created.getFirst().setStatus(EventStatus.OPEN_FOR_PREDICTIONS); events.flush();
        assertThat(catalog.upcomingEvents()).extracting(e -> e.id()).containsExactlyElementsOf(created.subList(0, 8).stream().map(ArenaEvent::getId).toList());
        assertThat(catalog.scheduledEvents()).extracting(e -> e.id()).containsExactlyElementsOf(created.subList(1, 9).stream().map(ArenaEvent::getId).toList());
    }
    @Test void featuredSelectionIsBoundedPrioritizesLiveAndExcludesTerminalEvents() {
        var created = fixtures(); created.getFirst().setStatus(EventStatus.CANCELLED);
        created.get(1).setStatus(EventStatus.FINISHED); created.get(119).setStatus(EventStatus.LIVE); events.flush();
        var selected = catalog.featuredEvents();
        assertThat(selected).hasSize(6); assertThat(selected.getFirst().id()).isEqualTo(created.get(119).getId());
        assertThat(selected).extracting(e -> e.id()).doesNotContain(created.get(0).getId(), created.get(1).getId());
        verify(markets).findByEventInOrderByEventIdAscIdAsc(argThat(values -> values.size() == 6));
    }
    @Test void adminCountStillHonorsSuspensionActiveOptionsAndTheEventState() {
        var event = fixtures().getFirst();
        var market = new PredictionMarket(); market.setEvent(event); market.setCode("CUSTOM_TEST");
        market.setName("Fixture isolated count"); market.setStatus(MarketStatus.OPEN); markets.saveAndFlush(market);
        var option = new MarketOption(); option.setMarket(market); option.setKey("HOME"); option.setLabel("Fixture choice");
        option.setMultiplier(new java.math.BigDecimal("2.00")); option.setActive(true); options.saveAndFlush(option);
        assertThat(catalog.availableMarketCount()).isEqualTo(1);
        option.setActive(false); options.flush(); assertThat(catalog.availableMarketCount()).isZero();
        option.setActive(true); market.setStatus(MarketStatus.SUSPENDED); markets.flush();
        assertThat(catalog.availableMarketCount()).isZero();
        market.setStatus(MarketStatus.OPEN); event.setStatus(EventStatus.FINISHED); events.flush();
        assertThat(catalog.availableMarketCount()).isZero();
    }
}
