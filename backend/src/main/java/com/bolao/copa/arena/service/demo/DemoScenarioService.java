package com.bolao.copa.arena.service.demo;

import static com.bolao.copa.arena.api.ArenaDtos.*;
import com.bolao.copa.arena.api.DemoScenarioDtos;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.entity.User;
import com.bolao.copa.security.DemoAccessPolicy;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name={"app.demo.enabled", "app.demo.controlled-enabled"}, havingValue="true")
public class DemoScenarioService {
    private final DemoScenarioStore store;
    private final DemoScenarioFactory factory;
    private final ArenaEventRepository events;
    private final ChampionshipRepository championships;
    private final PredictionMarketRepository markets;
    private final ArenaPredictionRepository predictions;
    private final ArenaPredictionService predictionService;
    private final AdminEventResultService results;
    private final ArenaCatalogService catalog;
    private final ArenaPoolRankingService rankings;
    private final DemoAccessPolicy access;
    private final AdminAuditService audit;
    private final EntityManager entityManager;
    private final int liveTimeoutMinutes;

    public DemoScenarioService(DemoScenarioStore store, DemoScenarioFactory factory, ArenaEventRepository events,
            ChampionshipRepository championships, PredictionMarketRepository markets, ArenaPredictionRepository predictions,
            ArenaPredictionService predictionService, AdminEventResultService results, ArenaCatalogService catalog,
            ArenaPoolRankingService rankings, DemoAccessPolicy access, AdminAuditService audit, EntityManager entityManager,
            @Value("${app.demo.live-timeout-minutes:15}") int liveTimeoutMinutes) {
        if (liveTimeoutMinutes < 1 || liveTimeoutMinutes > 120) throw new IllegalArgumentException("Demo live timeout must be between 1 and 120 minutes");
        this.store=store; this.factory=factory; this.events=events; this.championships=championships; this.markets=markets;
        this.predictions=predictions; this.predictionService=predictionService; this.results=results; this.catalog=catalog;
        this.rankings=rankings; this.access=access; this.audit=audit; this.entityManager=entityManager; this.liveTimeoutMinutes=liveTimeoutMinutes;
    }

    @Transactional
    public void initialize() {
        var state = store.lock();
        if (state.generation() == 0) {
            Championship championship = factory.championship();
            ArenaEvent history = factory.event(championship, 1, true);
            ArenaEvent active = factory.event(championship, 1, false);
            factory.ensureDemoBalance(1);
            entityManager.flush();
            store.save(1, championship.getId(), active.getId(), history.getId());
        }
        // Retire only old built-in live fixtures. No invented result and no external event is modified.
        for (ArenaEvent event : events.findByStatusOrderByStartsAtAsc(EventStatus.LIVE)) {
            if (event.isDemo() && !event.isDemoManaged() && event.getExternalProvider() == null
                    && event.getExternalKey().startsWith("demo-")) predictionService.cancelEvent(event.getId());
        }
    }

    @Transactional(readOnly=true)
    public DemoScenarioDtos.Scenario scenario(User user) {
        requireReader(user);
        return snapshot(initialized(store.read()), user);
    }

    @Transactional
    public DemoScenarioDtos.Scenario start(Long eventId, User user) {
        access.requireDemoAdmin(user);
        var state = initialized(store.lock());
        ArenaEvent event = currentEvent(state, eventId);
        startEvent(event);
        store.touch();
        return snapshot(store.read(), user);
    }

    @Transactional
    public DemoScenarioDtos.Scenario finish(Long eventId, DemoScenarioDtos.Result score, User user) {
        access.requireDemoAdmin(user);
        var state = initialized(store.lock());
        ArenaEvent event = currentEvent(state, eventId);
        if (event.getStatus() != EventStatus.FINISHED) startEvent(event);
        // This is the existing administrative result path, including engine validation and transactional settlement.
        results.record(event.getId(), new EventResultRequest(score.homeScore(), score.awayScore(), true, Map.of(), true),
                "controlled-result:" + event.getId());
        event.setResultProcessedAt(event.getResultProcessedAt() == null ? Instant.now() : event.getResultProcessedAt());
        event.setFinishedAt(event.getFinishedAt() == null ? Instant.now() : event.getFinishedAt());
        store.touch();
        return snapshot(store.read(), user);
    }

    @Transactional
    public DemoScenarioDtos.Scenario reset(long expectedGeneration, User user) {
        access.requireDemoAdmin(user);
        var state = initialized(store.lock());
        // The generation shown in the confirmation is the idempotency token; a retry cannot reset the next visitor's round.
        if (state.generation() == expectedGeneration + 1) return snapshot(state, user);
        if (state.generation() != expectedGeneration) throw new ArenaProblem.Conflict("A demonstração já mudou. Atualize a página antes de confirmar outro reset.");
        ArenaEvent previous = currentEvent(state, state.activeEventId());
        if (previous.getStatus() != EventStatus.FINISHED && previous.getStatus() != EventStatus.CANCELLED)
            predictionService.cancelEvent(previous.getId());
        previous.setDemoArchived(true);
        Championship championship = previous.getChampionship();
        ArenaEvent next = factory.event(championship, state.generation() + 1, false);
        factory.ensureDemoBalance(state.generation() + 1);
        entityManager.flush();
        store.save(state.generation() + 1, championship.getId(), next.getId(), state.historyEventId());
        audit.record("DEMO_ROUND_RESET", "EVENT", previous.getId(),
                "Rodada Demo arquivada; nova rodada " + (state.generation() + 1) + ". Dados externos e histórico preservados.");
        return snapshot(store.read(), user);
    }

    @Transactional
    public void maintain() {
        var state = store.lock();
        if (state.generation() == 0) return;
        ArenaEvent event = currentEvent(state, state.activeEventId());
        Instant now = Instant.now();
        if (event.getStatus() == EventStatus.LIVE && event.getStartsAt().isBefore(now.minus(Duration.ofMinutes(liveTimeoutMinutes)))) {
            predictionService.cancelEvent(event.getId());
            audit.record("DEMO_LIVE_EXPIRED", "EVENT", event.getId(), "Rodada Demo expirada sem resultado; palpites reembolsados. Use Resetar demonstração.");
            store.touch();
        } else if (event.getStatus() == EventStatus.OPEN_FOR_PREDICTIONS && !now.isBefore(event.getPredictionClosesAt())) {
            factory.moveWindow(event, now);
            for (PredictionMarket market : markets.findByEventForUpdate(event)) {
                if (market.getStatus() == MarketStatus.OPEN) {
                    market.setOpensAt(now); market.setClosesAt(event.getPredictionClosesAt());
                }
            }
            store.touch();
        }
    }

    private void startEvent(ArenaEvent event) {
        if (event.getStatus() == EventStatus.LIVE) return;
        if (event.getStatus() != EventStatus.OPEN_FOR_PREDICTIONS && event.getStatus() != EventStatus.SCHEDULED)
            throw new ArenaProblem.Conflict("Somente uma partida Demo aberta pode ser iniciada. Use o reset para uma nova rodada.");
        event.setStatus(EventStatus.LIVE); event.setStartsAt(Instant.now());
        event.setPredictionClosesAt(event.getStartsAt().minusSeconds(1));
        event.setHomeScore(null); event.setAwayScore(null); event.setLiveScoreAvailable(false);
        markets.findByEventForUpdate(event).forEach(market -> {
            if (market.getStatus() == MarketStatus.OPEN || market.getStatus() == MarketStatus.SUSPENDED) {
                market.setStatus(MarketStatus.CLOSED);
                market.setStatusReason("Palpites encerrados — esta partida demonstrativa já foi iniciada.");
                market.setClosesAt(event.getPredictionClosesAt());
            }
        });
        audit.record("DEMO_EVENT_STARTED", "EVENT", event.getId(), "Partida Demo iniciada; palpites bloqueados pelo domínio.");
    }

    private ArenaEvent currentEvent(DemoScenarioStore.State state, Long id) {
        if (!Objects.equals(state.activeEventId(), id)) throw new ArenaProblem.Conflict("Este evento não é a rodada demonstrativa atual.");
        ArenaEvent event = events.findByIdForUpdate(id).orElseThrow();
        if (!event.isDemo() || event.isDemoArchived() || event.getExternalProvider() != null
                || !event.getChampionship().isDemoManaged() || !Objects.equals(event.getChampionship().getId(), state.championshipId()))
            throw new ArenaProblem.Conflict("Evento fora da competição demonstrativa controlada.");
        return event;
    }
    private DemoScenarioDtos.Scenario snapshot(DemoScenarioStore.State state, User user) {
        Championship championship = championships.findById(state.championshipId()).orElseThrow();
        return new DemoScenarioDtos.Scenario(state.generation(), catalog.championshipResponse(championship),
                catalog.eventResponse(state.activeEventId()), List.of(catalog.eventResponse(state.historyEventId())),
                predictions.findCurrentChampionshipPredictions(championship.getId()).stream()
                        .filter(p -> p.getUser().getId().equals(user.getId())).map(predictionService::response).toList(),
                rankings.championshipRanking(championship.getId(), user), access.isDemoAdmin(user), access.isDemoParticipant(user),
                state.updatedAt(), "Demonstração compartilhada entre visitantes. Resultado, palpites e ranking são persistidos. O reset arquiva a rodada anterior sem apagar o histórico ou dados reais.");
    }
    private DemoScenarioStore.State initialized(DemoScenarioStore.State state) {
        if (state.generation() == 0) throw new ArenaProblem.Conflict("A competição demonstrativa ainda está sendo preparada.");
        return state;
    }
    private void requireReader(User user) {
        if (!access.isDemoAccount(user)) throw new org.springframework.security.access.AccessDeniedException("Entre com um perfil Demo para acessar a competição demonstrativa.");
    }
}
