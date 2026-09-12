package com.bolao.copa.arena.config;

import static com.bolao.copa.arena.api.ArenaDtos.PlacePredictionRequest;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.ArenaPredictionService;
import com.bolao.copa.arena.service.PointWalletService;
import com.bolao.copa.config.DemoParticipantCatalog;
import com.bolao.copa.entity.User;
import com.bolao.copa.entity.UserRole;
import com.bolao.copa.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final PointWalletService walletService;

    public ArenaRankingDemoInitializer(UserRepository users, SportRepository sports,
                                       ChampionshipRepository championships, CompetitorRepository competitors,
                                       ArenaEventRepository events, EventParticipantRepository eventParticipants,
                                       PredictionMarketRepository markets, MarketOptionRepository options,
                                       ArenaPredictionRepository predictionRepository,
                                       ArenaPredictionService predictionService,
                                       PointWalletService walletService) {
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
        this.walletService = walletService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(120)
    @Transactional
    public void seed() {
        Instant now = Instant.now();
        List<User> participants = demoParticipants();
        if (participants.size() != DemoParticipantCatalog.participants().size()) {
            throw new IllegalStateException("Todos os participantes do ranking demonstrativo devem existir antes do histórico.");
        }

        normalizeLegacyHistory(now, participants);
        SCENARIOS.forEach(scenario -> seedScenario(scenario, participants, now));
        normalizeRacingHistory();
    }

    @Scheduled(
            fixedDelayString = "${app.demo.history-refresh-ms:21600000}",
            initialDelayString = "${app.demo.history-initial-delay-ms:60000}"
    )
    @Transactional
    public void refreshHistoryTimelines() {
        Instant now = Instant.now();
        List<User> participants = demoParticipants();
        // Some integration-test slices intentionally boot with only a subset of
        // the demo catalog. A scheduled maintenance pass must be a no-op there,
        // never an asynchronous error that pollutes the application logs.
        if (participants.size() != DemoParticipantCatalog.participants().size()
                || SCENARIOS.stream().anyMatch(scenario -> !scenarioReferencesExist(scenario))) return;
        SCENARIOS.forEach(scenario -> seedScenario(scenario, participants, now));
        normalizeRacingHistory();
    }

    private boolean scenarioReferencesExist(Scenario scenario) {
        return sports.findByCodeIgnoreCase(scenario.sportCode())
                .map(sport -> !championships.findBySportOrderByNameAsc(sport).isEmpty()
                        && competitors.findBySportAndCodeIgnoreCase(sport, scenario.homeCode()).isPresent()
                        && competitors.findBySportAndCodeIgnoreCase(sport, scenario.awayCode()).isPresent())
                .orElse(false);
    }

    private List<User> demoParticipants() {
        return users.findAll().stream()
                .filter(user -> user.getRole().canonical() == UserRole.PARTICIPANTE)
                .filter(user -> DemoParticipantCatalog.contains(user.getEmail()))
                .sorted(Comparator.comparing(User::getEmail))
                .toList();
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
        Sport sport = sports.findByCodeIgnoreCase(scenario.sportCode()).orElseThrow();
        Championship championship = championships.findBySportOrderByNameAsc(sport).stream().findFirst().orElseThrow();
        Competitor home = competitors.findBySportAndCodeIgnoreCase(sport, scenario.homeCode()).orElseThrow();
        Competitor away = competitors.findBySportAndCodeIgnoreCase(sport, scenario.awayCode()).orElseThrow();
        ArenaEvent event = events.findByExternalKey(externalKey).orElse(null);
        boolean created = event == null;
        if (created) {
            event = new ArenaEvent();
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
        }

        PredictionMarket market;
        MarketOption homeOption;
        MarketOption awayOption;
        if (created) {
            market = new PredictionMarket();
            market.setEvent(event);
            market.setCode("DEMO_RESULT");
            market.setName("Vencedor do confronto");
            market.setStatus(MarketStatus.OPEN);
            market.setMinimumPoints(20);
            market = markets.saveAndFlush(market);
            homeOption = option(market, "HOME", home.getName(), scenario.multiplier());
            awayOption = option(market, "AWAY", away.getName(), "1.82");
        } else {
            market = markets.findByEventAndCode(event, "DEMO_RESULT").orElseThrow();
            homeOption = options.findByMarketAndKey(market, "HOME").orElseThrow();
            awayOption = options.findByMarketAndKey(market, "AWAY").orElseThrow();
        }

        Instant resolvedAt = now.minus(Duration.ofDays(scenario.daysAgo()));
        List<Long> predictionIds = new ArrayList<>();
        for (User participant : participants) {
            String outcomes = DemoParticipantCatalog.byEmail()
                    .get(participant.getEmail().toLowerCase(Locale.ROOT)).outcomes();
            MarketOption selected = outcomes.charAt(scenario.index()) == '1' ? homeOption : awayOption;
            String clientKey = "ranking-v2-" + scenario.key();
            String persistedKey = "prediction:user:" + participant.getId() + ":" + clientKey;
            int stake = stakeFor(participant, scenario);
            ArenaPrediction prediction = predictionRepository.findByIdempotencyKey(persistedKey).orElse(null);
            if (prediction == null && created) {
                var response = predictionService.place(new PlacePredictionRequest(event.getId(), market.getId(),
                        selected.getId(), stake, null, clientKey), clientKey, participant);
                prediction = predictionRepository.findById(response.id()).orElseThrow();
            } else if (prediction == null) {
                prediction = backfillSettledPrediction(participant, event, market, selected, stake, persistedKey,
                        resolvedAt, outcomes.charAt(scenario.index()) == '1');
            }
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
        market.setStatus(created ? MarketStatus.CLOSED : MarketStatus.SETTLED);
        market.setResultOptionKey("HOME");
        markets.saveAndFlush(market);
        if (created) predictionService.settleMarket(market.getId(), "HOME");

        for (Long predictionId : predictionIds) {
            ArenaPrediction prediction = predictionRepository.findById(predictionId).orElseThrow();
            prediction.setResolvedAt(resolvedAt);
            predictionRepository.save(prediction);
        }
        market.setSettledAt(resolvedAt);
        markets.save(market);
    }

    private ArenaPrediction backfillSettledPrediction(User user, ArenaEvent event, PredictionMarket market,
                                                       MarketOption selected, int stake, String idempotencyKey,
                                                       Instant resolvedAt, boolean won) {
        ArenaPrediction prediction = new ArenaPrediction();
        prediction.setUser(user);
        prediction.setEvent(event);
        prediction.setMarket(market);
        prediction.setOption(selected);
        prediction.setStakePoints(stake);
        prediction.setMultiplier(selected.getMultiplier());
        int potential = selected.getMultiplier().multiply(BigDecimal.valueOf(stake))
                .setScale(0, RoundingMode.DOWN).intValueExact();
        prediction.setPotentialPoints(potential);
        prediction.setStatus(won ? PredictionStatus.WON : PredictionStatus.LOST);
        prediction.setRewardedPoints(won ? potential : 0);
        prediction.setPlacedAt(resolvedAt.minus(Duration.ofHours(3)));
        prediction.setResolvedAt(resolvedAt);
        prediction.setIdempotencyKey(idempotencyKey);
        prediction = predictionRepository.saveAndFlush(prediction);
        walletService.apply(user, -stake, PointTransactionType.PREDICTION_PLACED,
                "ledger:" + idempotencyKey, "PREDICTION", prediction.getId().toString(),
                "Pontos utilizados no histórico demonstrativo: " + event.getTitle());
        if (won) {
            walletService.apply(user, potential, PointTransactionType.PREDICTION_WON,
                    "prediction-win:" + prediction.getId(), "PREDICTION", prediction.getId().toString(),
                    "Recompensa do histórico demonstrativo: " + event.getTitle());
        }
        return prediction;
    }

    private int stakeFor(User participant, Scenario scenario) {
        int index = 0;
        for (var value : DemoParticipantCatalog.participants()) {
            if (value.email().equalsIgnoreCase(participant.getEmail())) break;
            index++;
        }
        return index < 8 ? scenario.stake() : scenario.stake() + (index - 7) * 3 + scenario.index();
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
