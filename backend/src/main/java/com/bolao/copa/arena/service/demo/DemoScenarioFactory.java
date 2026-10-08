package com.bolao.copa.arena.service.demo;

import static com.bolao.copa.arena.api.ArenaDtos.*;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.config.DemoProperties;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Creates persisted scenario fixtures; all predictions and scores pass through the existing domain services. */
@Component
public class DemoScenarioFactory {
    private final SportRepository sports;
    private final ChampionshipRepository championships;
    private final CompetitorRepository teams;
    private final ArenaEventRepository events;
    private final EventParticipantRepository participants;
    private final MarketTemplateService templates;
    private final MarketOptionRepository options;
    private final ArenaPredictionService predictions;
    private final AdminEventResultService results;
    private final PointWalletService wallets;
    private final UserRepository users;
    private final DemoProperties demo;
    private final int windowHours;

    public DemoScenarioFactory(SportRepository sports, ChampionshipRepository championships, CompetitorRepository teams,
            ArenaEventRepository events, EventParticipantRepository participants, MarketTemplateService templates,
            MarketOptionRepository options, ArenaPredictionService predictions, AdminEventResultService results,
            PointWalletService wallets, UserRepository users, DemoProperties demo,
            @Value("${app.demo.round-window-hours:24}") int windowHours) {
        if (windowHours < 1 || windowHours > 168) throw new IllegalArgumentException("Demo round window must be between 1 and 168 hours");
        this.sports=sports; this.championships=championships; this.teams=teams; this.events=events;
        this.participants=participants; this.templates=templates; this.options=options; this.predictions=predictions;
        this.results=results; this.wallets=wallets; this.users=users; this.demo=demo; this.windowHours=windowHours;
    }

    public Championship championship() {
        Sport sport = sports.findByCodeIgnoreCase("CS2").orElseThrow();
        Championship value = new Championship();
        value.setSport(sport); value.setName("Competição de Demonstração");
        value.setSlug("arena-controlled-demo"); value.setSeason("DEMO"); value.setDemoManaged(true);
        return championships.saveAndFlush(value);
    }

    public ArenaEvent event(Championship championship, long generation, boolean historical) {
        if (!championship.isDemoManaged() || championship.getExternalProvider() != null)
            throw new ArenaProblem.Conflict("Competição fora do escopo demonstrativo.");
        ArenaEvent event = new ArenaEvent();
        event.setExternalKey(historical ? "controlled-demo-history" : "controlled-demo-round-" + generation);
        event.setChampionship(championship); event.setDemo(true);
        event.setHomeCompetitor(team(championship.getSport(), "DEMO_AURORA", "Aurora Demo"));
        event.setAwayCompetitor(team(championship.getSport(), "DEMO_HORIZONTE", "Horizonte Demo"));
        event.setTitle("Aurora Demo × Horizonte Demo");
        event.setStage(historical ? "Histórico demonstrativo" : "Rodada demonstrativa " + generation);
        event.setVenue("Arena demonstrativa"); event.setBroadcast("Simulação controlada pelo Administrador Demo");
        event.setFormat(EventFormat.BO3); event.setBestOf(3); event.setStatus(EventStatus.OPEN_FOR_PREDICTIONS);
        event.setHomeScore(null); event.setAwayScore(null);
        moveWindow(event, Instant.now());
        event = events.saveAndFlush(event);
        addParticipant(event, event.getHomeCompetitor(), 0); addParticipant(event, event.getAwayCompetitor(), 1);
        List<PredictionMarket> published = templates.generate(event.getId());
        if (historical) seedHistory(event, published);
        return event;
    }

    public void moveWindow(ArenaEvent event, Instant now) {
        event.setStartsAt(now.plus(Duration.ofHours(windowHours)));
        event.setPredictionClosesAt(event.getStartsAt().minusSeconds(60));
    }

    /** Only the dedicated participant gets a documented, ledger-backed refill when otherwise unable to repeat the demo. */
    public void ensureDemoBalance(long generation) {
        User participant = users.findByEmailIgnoreCase(demo.participantEmail()).orElseThrow();
        wallets.lockParticipant(participant);
        long missing = Math.max(0, PointWalletService.INITIAL_VIRTUAL_POINTS - wallets.wallet(participant).balance());
        if (missing > 0) wallets.apply(participant, missing, PointTransactionType.INITIAL_BONUS,
                "controlled-demo-refill:" + generation, "DEMO_ROUND", Long.toString(generation),
                "Reposição de pontos virtuais para repetir a demonstração");
    }

    private void seedHistory(ArenaEvent event, List<PredictionMarket> published) {
        PredictionMarket market = published.stream().filter(m -> "SERIES_WINNER".equals(m.getTemplateCode())).findFirst().orElseThrow();
        List<String> emails = List.of(demo.participantEmail(), "marina.costa@arenapredict.com", "rafael.lima@arenapredict.com");
        for (int i=0; i<emails.size(); i++) {
            User user = users.findByEmailIgnoreCase(emails.get(i)).orElseThrow();
            MarketOption option = options.findByMarketAndKey(market, i == 1 ? "HOME" : "AWAY").orElseThrow();
            wallets.ensureWallet(user);
            predictions.placeDemoSeed(new PlacePredictionRequest(event.getId(), market.getId(), option.getId(),
                    market.getMinimumPoints(), null, "controlled-history-" + user.getId()), null, user);
        }
        event.setStartsAt(Instant.now().minus(Duration.ofHours(2)));
        event.setPredictionClosesAt(event.getStartsAt().minusSeconds(60));
        results.record(event.getId(), new EventResultRequest(2, 0, true, Map.of(), true), "controlled-history-result");
        event.setResultProcessedAt(Instant.now());
        event.setFinishedAt(Instant.now());
    }

    private Competitor team(Sport sport, String code, String name) {
        Competitor value = teams.findBySportAndCodeIgnoreCase(sport, code).orElse(null);
        if (value != null) {
            if (value.getExternalProvider() != null) throw new ArenaProblem.Conflict("Identidade Demo em uso por catálogo externo.");
            return value;
        }
        value = new Competitor(); value.setSport(sport); value.setCode(code); value.setName(name);
        return teams.saveAndFlush(value);
    }
    private void addParticipant(ArenaEvent event, Competitor team, int position) {
        EventParticipant entry = new EventParticipant(); entry.setEvent(event); entry.setCompetitor(team);
        entry.setDisplayOrder(position); participants.save(entry);
    }
}
