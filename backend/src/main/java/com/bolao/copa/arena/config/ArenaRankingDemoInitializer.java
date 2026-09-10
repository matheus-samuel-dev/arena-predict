package com.bolao.copa.arena.config;

import static com.bolao.copa.arena.api.ArenaDtos.PlacePredictionRequest;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.ArenaPredictionService;
import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import com.bolao.copa.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Small, deterministic history used by the demo environment. Predictions are
 * placed and settled through the production service, so ranking, wallets,
 * progression and idempotency exercise the same rules as a real flow.
 */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class ArenaRankingDemoInitializer {
    static final String KEY_PREFIX = "demo-ranking-v2-";
    private static final Map<String, String> OUTCOMES = Map.of(
            "ana.ribeiro@arenapredict.com", "111110101",
            "beatriz.nunes@arenapredict.com", "111010110",
            "camila.rocha@arenapredict.com", "110101011",
            "diego.ferreira@arenapredict.com", "101010100",
            "jogador@arenapredict.com", "011100111",
            "lucas.almeida@arenapredict.com", "100100010",
            "marina.costa@arenapredict.com", "010010101",
            "rafael.lima@arenapredict.com", "001000011"
    );
    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario(0, "football-week", "FOOTBALL", "PAL", "FLA", "Palmeiras x Flamengo · retrospecto", 1, 90, "2.00"),
            new Scenario(1, "cs2-week", "CS2", "FURIA", "NAVI", "FURIA x NAVI · retrospecto", 2, 100, "1.80"),
            new Scenario(2, "tennis-week", "TENNIS", "ALC", "SIN", "Carlos Alcaraz x Jannik Sinner · retrospecto", 3, 110, "1.75"),
            new Scenario(3, "motorsport-week", "MOTORSPORT", "VER", "NOR", "Verstappen x Norris · retrospecto", 4, 80, "2.20"),
            new Scenario(4, "valorant-week", "VALORANT", "LEV", "LOUD", "Leviatán x LOUD · retrospecto", 5, 95, "1.90"),
            new Scenario(5, "basketball-week", "BASKETBALL", "BOS", "DAL", "Celtics x Mavericks · retrospecto", 6, 85, "1.85"),
            new Scenario(6, "football-month", "FOOTBALL", "FLA", "PAL", "Flamengo x Palmeiras · retrospecto mensal", 12, 120, "2.05"),
            new Scenario(7, "cs2-month", "CS2", "NAVI", "FURIA", "NAVI x FURIA · retrospecto mensal", 22, 100, "1.90"),
            new Scenario(8, "tennis-all", "TENNIS", "SIN", "ALC", "Jannik Sinner x Carlos Alcaraz · histórico", 45, 105, "1.80")
    );

    private final UserRepository users;
    private final SportRepository sports;
    private final ChampionshipRepository championships;
    private final CompetitorRepository competitors;
    private final ArenaEventRepository events;
    private final EventParticipantRepository eventParticipants;
    private final PredictionMarketRepository markets;
    private final MarketOptionRepository options;
    private final ArenaPredictionRepository predictionRepository;
    private final ArenaPredictionService predictionService;

    public ArenaRankingDemoInitializer(UserRepository users, SportRepository sports,
                                       ChampionshipRepository championships, CompetitorRepository competitors,
                                       ArenaEventRepository events, EventParticipantRepository eventParticipants,
                                       PredictionMarketRepository markets, MarketOptionRepository options,
                                       ArenaPredictionRepository predictionRepository,
                                       ArenaPredictionService predictionService) {
        this.users = users;
        this.sports = sports;
        this.championships = championships;
        this.competitors = competitors;
        this.events = events;
        this.eventParticipants = eventParticipants;
        this.markets = markets;
        this.options = options;
        this.predictionRepository = predictionRepository;
        this.predictionService = predictionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(120)
    @Transactional
    public void seed() {
        Instant now = Instant.now();
        List<User> participants = users.findAll().stream()
                .filter(user -> user.getRole().canonical() == UserRole.PARTICIPANTE)
                .filter(user -> OUTCOMES.containsKey(user.getEmail().toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(User::getEmail))
                .toList();
        if (participants.size() != OUTCOMES.size()) {
            throw new IllegalStateException("Os oito participantes do ranking demonstrativo devem existir antes do histórico.");
        }

        normalizeLegacyHistory(now, participants);
        SCENARIOS.forEach(scenario -> seedScenario(scenario, participants, now));
        normalizeRacingHistory();
    }

    // The old history used a football-shaped score for a two-driver comparison.
    // Preserve its predictions and payouts while presenting the actual racing result.
    private void normalizeRacingHistory() {
        events.findByExternalKey(KEY_PREFIX + "motorsport-week").filter(ArenaEvent::isDemo).ifPresent(event -> {
            event.setFormat(EventFormat.RACE);
            event.setTitle("GP demonstrativo · classificação histórica");
            event.setHomeScore(null);
            event.setAwayScore(null);
            eventParticipants.findByEventOrderByDisplayOrderAsc(event).forEach(participant ->
                    participant.setPosition(participant.getDisplayOrder() + 1));
            markets.findByEventOrderByIdAsc(event).stream().filter(market -> "DEMO_RESULT".equals(market.getCode()))
                    .forEach(market -> market.setName("Confronto entre pilotos · Verstappen / Norris"));
        });
    }

    private void seedScenario(Scenario scenario, List<User> participants, Instant now) {
        String externalKey = KEY_PREFIX + scenario.key();
        if (events.findByExternalKey(externalKey).isPresent()) return;

        Sport sport = sports.findByCodeIgnoreCase(scenario.sportCode()).orElseThrow();
        Championship championship = championships.findBySportOrderByNameAsc(sport).stream().findFirst().orElseThrow();
        Competitor home = competitors.findBySportAndCodeIgnoreCase(sport, scenario.homeCode()).orElseThrow();
        Competitor away = competitors.findBySportAndCodeIgnoreCase(sport, scenario.awayCode()).orElseThrow();

        ArenaEvent event = new ArenaEvent();
        event.setExternalKey(externalKey);
        event.setChampionship(championship);
        event.setHomeCompetitor(home);
        event.setAwayCompetitor(away);
        event.setTitle(scenario.title());
        event.setStage("Histórico demonstrativo do ranking");
        event.setVenue("Arena digital");
        event.setBroadcast("Provider interno demonstrativo");
        event.setStartsAt(now.plus(Duration.ofHours(6)));
        event.setPredictionClosesAt(now.plus(Duration.ofHours(5)));
        event.setStatus(EventStatus.OPEN_FOR_PREDICTIONS);
        event.setFormat(EventFormat.STANDARD);
        event.setBestOf(1);
        event.setDemo(true);
        event.setFeatured(false);
        event = events.saveAndFlush(event);
        addParticipant(event, home, 0);
        addParticipant(event, away, 1);

        PredictionMarket market = new PredictionMarket();
        market.setEvent(event);
        market.setCode("DEMO_RESULT");
        market.setName("Vencedor do confronto");
        market.setStatus(MarketStatus.OPEN);
        market.setMinimumPoints(20);
        market = markets.saveAndFlush(market);
        MarketOption homeOption = option(market, "HOME", home.getName(), scenario.multiplier());
        MarketOption awayOption = option(market, "AWAY", away.getName(), "1.82");

        Instant resolvedAt = now.minus(Duration.ofDays(scenario.daysAgo()));
        List<Long> predictionIds = new ArrayList<>();
        for (User participant : participants) {
            String outcomes = OUTCOMES.get(participant.getEmail().toLowerCase(Locale.ROOT));
            MarketOption selected = outcomes.charAt(scenario.index()) == '1' ? homeOption : awayOption;
            String idempotencyKey = "ranking-v2-" + scenario.key();
            var response = predictionService.place(new PlacePredictionRequest(event.getId(), market.getId(),
                    selected.getId(), scenario.stake(), null, idempotencyKey), idempotencyKey, participant);
            ArenaPrediction prediction = predictionRepository.findById(response.id()).orElseThrow();
            prediction.setPlacedAt(resolvedAt.minus(Duration.ofHours(3)));
            predictionRepository.saveAndFlush(prediction);
            predictionIds.add(prediction.getId());
        }

        event.setStartsAt(resolvedAt.minus(Duration.ofHours(1)));
        event.setPredictionClosesAt(resolvedAt.minus(Duration.ofHours(4)));
        event.setHomeScore(2);
        event.setAwayScore(1);
        event.setStatus(EventStatus.FINISHED);
        events.saveAndFlush(event);
        market.setStatus(MarketStatus.CLOSED);
        markets.saveAndFlush(market);
        predictionService.settleMarket(market.getId(), "HOME");

        for (Long predictionId : predictionIds) {
            ArenaPrediction prediction = predictionRepository.findById(predictionId).orElseThrow();
            prediction.setResolvedAt(resolvedAt);
            predictionRepository.save(prediction);
        }
        market.setSettledAt(resolvedAt);
        markets.save(market);
    }

    private void normalizeLegacyHistory(Instant now, List<User> participants) {
        ArenaEvent legacy = events.findByExternalKey("demo-football-settled").orElse(null);
        if (legacy == null || !legacy.isDemo()) return;
        Instant historicalAt = now.minus(Duration.ofDays(90));
        boolean changed = false;
        for (User participant : participants) {
            for (ArenaPrediction prediction : predictionRepository.findByUserOrderByPlacedAtDesc(participant)) {
                if (!prediction.getEvent().getId().equals(legacy.getId())
                        || (prediction.getStatus() != PredictionStatus.WON && prediction.getStatus() != PredictionStatus.LOST)
                        || (prediction.getResolvedAt() != null && !prediction.getResolvedAt().isAfter(now.minus(Duration.ofDays(60))))) {
                    continue;
                }
                prediction.setPlacedAt(historicalAt.minus(Duration.ofHours(3)));
                prediction.setResolvedAt(historicalAt);
                predictionRepository.save(prediction);
                changed = true;
            }
        }
        if (changed) {
            legacy.setStartsAt(historicalAt.minus(Duration.ofHours(1)));
            legacy.setPredictionClosesAt(historicalAt.minus(Duration.ofHours(4)));
            events.save(legacy);
            markets.findByEventOrderByIdAsc(legacy).forEach(market -> {
                if (market.getStatus() == MarketStatus.SETTLED) market.setSettledAt(historicalAt);
            });
        }
    }

    private void addParticipant(ArenaEvent event, Competitor competitor, int order) {
        EventParticipant participant = new EventParticipant();
        participant.setEvent(event);
        participant.setCompetitor(competitor);
        participant.setDisplayOrder(order);
        eventParticipants.save(participant);
    }

    private MarketOption option(PredictionMarket market, String key, String label, String multiplier) {
        MarketOption option = new MarketOption();
        option.setMarket(market);
        option.setKey(key);
        option.setLabel(label);
        option.setMultiplier(new BigDecimal(multiplier));
        return options.save(option);
    }

    private record Scenario(int index, String key, String sportCode, String homeCode, String awayCode,
                            String title, int daysAgo, int stake, String multiplier) { }
}
