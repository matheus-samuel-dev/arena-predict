package com.bolao.copa.arena.service.demo;

import static com.bolao.copa.arena.api.ArenaDtos.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.config.DemoParticipantCatalog;
import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import com.bolao.copa.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in, atomic enrichment of the existing Demo. Replaying its receipt never changes history. */
@Service
@ConditionalOnProperty(name={"app.demo.enabled", "app.demo.controlled-enabled", "app.demo.portfolio-seed-enabled"}, havingValue="true")
public class DemoPortfolioSeedService {
    public static final String VERSION = "demo-portfolio-v1";
    private static final String OPERATION = "DEMO_PORTFOLIO_SEED";
    private static final Logger log = LoggerFactory.getLogger(DemoPortfolioSeedService.class);
    private final DemoScenarioStore store;
    private final AdminOperationKeyRepository receipts;
    private final UserRepository users;
    private final ArenaEventRepository events;
    private final EventParticipantRepository participants;
    private final ArenaPredictionRepository predictions;
    private final MarketOptionRepository options;
    private final MarketTemplateService templates;
    private final ArenaPredictionService commands;
    private final AdminEventResultService results;
    private final ArenaPoolRepository pools;
    private final ProgressionService progression;
    private final AdminAuditService audit;
    private final EntityManager entityManager;

    public DemoPortfolioSeedService(DemoScenarioStore store, AdminOperationKeyRepository receipts, UserRepository users,
            ArenaEventRepository events, EventParticipantRepository participants, ArenaPredictionRepository predictions,
            MarketOptionRepository options, MarketTemplateService templates, ArenaPredictionService commands,
            AdminEventResultService results, ArenaPoolRepository pools, ProgressionService progression, AdminAuditService audit,
            EntityManager entityManager) {
        this.store=store; this.receipts=receipts; this.users=users; this.events=events; this.participants=participants;
        this.predictions=predictions; this.options=options; this.templates=templates; this.commands=commands;
        this.results=results; this.pools=pools; this.progression=progression; this.audit=audit;
        this.entityManager=entityManager;
    }

    @Transactional
    public void seed() {
        // Reuse the durable Demo mutex. No process-local lock or official-event mutation.
        if (store.lock().generation() == 0) throw new IllegalStateException("Inicialize o cenário Demo antes do portfólio.");
        if (receipts.findByOperationAndIdempotencyKey(OPERATION, VERSION).isPresent()) return;
        List<User> people = DemoParticipantCatalog.participants().stream().map(person ->
                users.findByEmailIgnoreCase(person.email()).filter(user -> user.getRole().canonical() == UserRole.PARTICIPANTE)
                        .orElseThrow(() -> new IllegalStateException("O catálogo demonstrativo precisa estar completo."))).toList();
        Instant now = Instant.now();
        int index = 0;
        for (var fixture : DemoPortfolioFixtures.HISTORY) seedHistory(fixture, people, now, index++);
        seedPending("demo-football-open", "WINNER", people, now);
        seedPending("demo-cs2-open", "SERIES_WINNER", people, now);
        people.forEach(progression::refresh);
        receipts.save(new AdminOperationKey(OPERATION, VERSION, VERSION, VERSION, now));
        audit.record("DEMO_PORTFOLIO_SEEDED", "DEMO", VERSION,
                "24 participantes; seis retrospectos multiesportivos; dois eventos abertos. Pontos virtuais calculados pelo domínio.");
        log.info("[DEMO_SEED] version={} participants={} historyEvents=6 pendingEvents=2 status=success", VERSION, people.size());
    }

    private void seedHistory(DemoPortfolioFixtures.Fixture fixture, List<User> people, Instant now, int fixtureIndex) {
        ArenaEvent event = createEvent(fixture.source(), "history", now);
        Set<String> codes = fixture.selections().stream().map(DemoPortfolioFixtures.Selection::template).collect(Collectors.toSet());
        var markets = templates.generateDemoSeed(event.getId(), codes);
        Instant resolved = now.minus(Duration.ofDays(fixture.daysAgo()));
        ArenaPool pool = "demo-football-open".equals(fixture.source())
                ? pools.findByInviteCodeIgnoreCase("AMIGOS26").orElseThrow() : null;
        // The social competition must have the exact internal championship, never a provider catalog.
        if (pool != null && (!Objects.equals(pool.getChampionship().getId(), event.getChampionship().getId())
                || !DemoParticipantCatalog.contains(pool.getOwner().getEmail())))
            throw new IllegalStateException("Bolão fora do escopo demonstrativo.");
        List<Long> created = new ArrayList<>();
        for (int marketIndex = 0; marketIndex < fixture.selections().size(); marketIndex++) {
            var selection = fixture.selections().get(marketIndex);
            var market = markets.stream().filter(value -> selection.template().equals(value.getTemplateCode())).findFirst().orElseThrow();
            var choices = options.findByMarketOrderByIdAsc(market);
            for (int personIndex = 0; personIndex < people.size(); personIndex++) {
                User user = people.get(personIndex);
                var profile = DemoParticipantCatalog.byEmail().get(user.getEmail().toLowerCase(Locale.ROOT));
                boolean won = profile.outcomes().charAt((fixtureIndex * 3 + marketIndex) % profile.outcomes().length()) == '1';
                MarketOption option = choices.stream().filter(choice -> won == selection.winningKey().equals(choice.getKey())).findFirst().orElseThrow();
                int stake = 30 + (personIndex % 6) * 10 + marketIndex * 15;
                String key = VERSION + ":" + fixture.source() + ":" + selection.template();
                var prediction = commands.placeDemoSeed(new PlacePredictionRequest(event.getId(), market.getId(), option.getId(),
                        stake, pool == null ? null : pool.getId(), key), key, user, resolved.minusSeconds(7200 + marketIndex * 300L));
                created.add(prediction.id());
                flushBatch();
            }
        }
        event = events.findByIdForUpdate(event.getId()).orElseThrow();
        event.setStartsAt(resolved.minusSeconds(3600));
        event.setPredictionClosesAt(event.getStartsAt().minusSeconds(60));
        results.record(event.getId(), new EventResultRequest(fixture.home(), fixture.away(), true, fixture.data(), true), VERSION + ":result:" + fixture.source());
        event.setFinishedAt(resolved); event.setResultProcessedAt(resolved);
        for (Long id : created) predictions.findById(id).orElseThrow().setResolvedAt(resolved);
    }

    private void seedPending(String source, String template, List<User> people, Instant now) {
        ArenaEvent event = createEvent(source, "pending", now);
        var market = templates.generateDemoSeed(event.getId(), Set.of(template)).getFirst();
        var choices = options.findByMarketOrderByIdAsc(market);
        for (int index = 0; index < people.size(); index++) {
            var choice = choices.get(index % choices.size());
            String key = VERSION + ":pending:" + source;
            commands.placeDemoSeed(new PlacePredictionRequest(event.getId(), market.getId(), choice.getId(),
                    40 + (index % 4) * 10, null, key), key, people.get(index));
            flushBatch();
        }
    }

    private void flushBatch() {
        // Keep fixture imports bounded: Hibernate must not dirty-check the entire imported history on every command.
        // Flush preserves the transaction/locks; clear only releases the persistence-context snapshots.
        entityManager.flush();
        entityManager.clear();
    }

    private ArenaEvent createEvent(String sourceKey, String kind, Instant now) {
        ArenaEvent source = events.findByExternalKey(sourceKey).orElseThrow();
        if (!source.isDemo() || source.getExternalProvider() != null || source.getChampionship().getExternalProvider() != null
                || source.getHomeCompetitor().getExternalProvider() != null || source.getAwayCompetitor().getExternalProvider() != null)
            throw new IllegalStateException("O portfólio reutiliza somente fixtures internas Demo.");
        String key = VERSION + ":" + kind + ":" + sourceKey;
        if (events.findByExternalKey(key).isPresent()) throw new IllegalStateException("Fixture sem recibo de seed: " + key);
        ArenaEvent event = new ArenaEvent();
        event.setExternalKey(key); event.setChampionship(source.getChampionship()); event.setDemo(true);
        event.setHomeCompetitor(source.getHomeCompetitor()); event.setAwayCompetitor(source.getAwayCompetitor());
        event.setTitle(source.getHomeCompetitor().getName() + " × " + source.getAwayCompetitor().getName() + " · demonstração");
        event.setStage("history".equals(kind) ? "Histórico demonstrativo" : "Rodada demonstrativa");
        event.setVenue("Ambiente demonstrativo"); event.setBroadcast("Fixture Demo, sem origem esportiva oficial");
        event.setFormat(source.getFormat()); event.setBestOf(source.getBestOf());
        event.setStatus(EventStatus.OPEN_FOR_PREDICTIONS);
        event.setStartsAt(now.plusSeconds(2 * 86400)); event.setPredictionClosesAt(event.getStartsAt().minusSeconds(60));
        events.saveAndFlush(event);
        for (int index = 0; index < 2; index++) {
            EventParticipant participant = new EventParticipant(); participant.setEvent(event);
            participant.setCompetitor(index == 0 ? event.getHomeCompetitor() : event.getAwayCompetitor());
            participant.setDisplayOrder(index); participants.save(participant);
        }
        return event;
    }
}
