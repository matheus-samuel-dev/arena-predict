package com.bolao.copa.arena.config;

import static com.bolao.copa.arena.api.ArenaDtos.PlacePredictionRequest;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true", matchIfMissing = true)
public class ArenaDemoInitializer {
    private final SportRepository sports;
    private final ChampionshipRepository championships;
    private final CompetitorRepository competitors;
    private final ArenaEventRepository events;
    private final PredictionMarketRepository markets;
    private final MarketOptionRepository options;
    private final ArenaPoolRepository pools;
    private final ArenaPoolMemberRepository members;
    private final ArenaNotificationRepository notificationRepository;
    private final EventParticipantRepository eventParticipants;
    private final UserRepository users;
    private final PointWalletService wallets;
    private final ArenaPredictionService predictions;
    private final DemoLiveEventService liveEvents;

    public ArenaDemoInitializer(SportRepository sports, ChampionshipRepository championships,
                                CompetitorRepository competitors, ArenaEventRepository events,
                                PredictionMarketRepository markets, MarketOptionRepository options,
                                ArenaPoolRepository pools, ArenaPoolMemberRepository members,
                                ArenaNotificationRepository notificationRepository, EventParticipantRepository eventParticipants,
                                UserRepository users,
                                PointWalletService wallets, ArenaPredictionService predictions,
                                DemoLiveEventService liveEvents) {
        this.sports = sports; this.championships = championships; this.competitors = competitors; this.events = events;
        this.markets = markets; this.options = options; this.pools = pools; this.members = members;
        this.notificationRepository = notificationRepository; this.users = users; this.wallets = wallets;
        this.eventParticipants = eventParticipants;
        this.predictions = predictions; this.liveEvents = liveEvents;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        Map<String, Sport> sport = new LinkedHashMap<>();
        sport.put("FOOTBALL", sport("FOOTBALL", "Futebol", SportCategory.TRADITIONAL, "football", 1));
        sport.put("BASKETBALL", sport("BASKETBALL", "Basquete", SportCategory.TRADITIONAL, "basketball", 2));
        sport.put("VOLLEYBALL", sport("VOLLEYBALL", "Vôlei", SportCategory.TRADITIONAL, "volleyball", 3));
        sport.put("TENNIS", sport("TENNIS", "Tênis", SportCategory.TRADITIONAL, "tennis", 4));
        sport.put("MOTORSPORT", sport("MOTORSPORT", "Automobilismo", SportCategory.MOTORSPORT, "gauge", 5));
        sport.put("AMERICAN_FOOTBALL", sport("AMERICAN_FOOTBALL", "Futebol americano", SportCategory.TRADITIONAL, "trophy", 6));
        sport.put("CS2", sport("CS2", "Counter-Strike 2", SportCategory.ESPORTS, "crosshair", 7));
        sport.put("VALORANT", sport("VALORANT", "Valorant", SportCategory.ESPORTS, "target", 8));
        sport.put("LEAGUE_OF_LEGENDS", sport("LEAGUE_OF_LEGENDS", "League of Legends", SportCategory.ESPORTS, "swords", 9));
        sport.put("DOTA2", sport("DOTA2", "Dota 2", SportCategory.ESPORTS, "shield", 10));

        Championship brasileirao = championship(sport.get("FOOTBALL"), "Brasileirão Série A", "brasileirao-serie-a", "2026");
        Championship nba = championship(sport.get("BASKETBALL"), "NBA", "nba", "2026");
        Championship blast = championship(sport.get("CS2"), "Blast Premier", "blast-premier", "2026");
        Championship vct = championship(sport.get("VALORANT"), "VCT Americas", "vct-americas", "2026");
        Championship wimbledon = championship(sport.get("TENNIS"), "Wimbledon", "wimbledon", "2026");
        Championship lck = championship(sport.get("LEAGUE_OF_LEGENDS"), "LCK Summer", "lck-summer", "2026");
        Championship formula1 = championship(sport.get("MOTORSPORT"), "Fórmula 1", "formula-1", "2026");

        Competitor palmeiras = competitor(sport.get("FOOTBALL"), "Palmeiras", "PAL", "Brasil");
        Competitor flamengo = competitor(sport.get("FOOTBALL"), "Flamengo", "FLA", "Brasil");
        Competitor celtics = competitor(sport.get("BASKETBALL"), "Boston Celtics", "BOS", "Estados Unidos");
        Competitor mavericks = competitor(sport.get("BASKETBALL"), "Dallas Mavericks", "DAL", "Estados Unidos");
        Competitor furia = competitor(sport.get("CS2"), "FURIA", "FURIA", "Brasil");
        Competitor navi = competitor(sport.get("CS2"), "NAVI", "NAVI", "Ucrânia");
        Competitor leviatan = competitor(sport.get("VALORANT"), "Leviatán", "LEV", "Argentina");
        Competitor loud = competitor(sport.get("VALORANT"), "LOUD", "LOUD", "Brasil");
        Competitor alcaraz = competitor(sport.get("TENNIS"), "Carlos Alcaraz", "ALC", "Espanha");
        Competitor sinner = competitor(sport.get("TENNIS"), "Jannik Sinner", "SIN", "Itália");
        Competitor t1 = competitor(sport.get("LEAGUE_OF_LEGENDS"), "T1", "T1", "Coreia do Sul");
        Competitor geng = competitor(sport.get("LEAGUE_OF_LEGENDS"), "Gen.G", "GENG", "Coreia do Sul");
        Competitor verstappen = competitor(sport.get("MOTORSPORT"), "Max Verstappen", "VER", "Países Baixos");
        Competitor norris = competitor(sport.get("MOTORSPORT"), "Lando Norris", "NOR", "Reino Unido");
        Competitor leclerc = competitor(sport.get("MOTORSPORT"), "Charles Leclerc", "LEC", "Mônaco");
        Competitor piastri = competitor(sport.get("MOTORSPORT"), "Oscar Piastri", "PIA", "Austrália");

        Instant now = Instant.now();
        ArenaEvent footballLive = event("demo-football-live", brasileirao, palmeiras, flamengo, "Palmeiras x Flamengo",
                now.minusSeconds(4_000), now.minusSeconds(7_200), EventStatus.LIVE, EventFormat.STANDARD, 1, true);
        ArenaEvent csLive = event("demo-cs2-live", blast, furia, navi, "FURIA x NAVI",
                now.minusSeconds(3_000), now.minusSeconds(5_400), EventStatus.LIVE, EventFormat.BO3, 3, true);
        ArenaEvent basketballOpen = event("demo-nba-open", nba, celtics, mavericks, "Boston Celtics x Dallas Mavericks",
                now.plusSeconds(14_400), now.plusSeconds(13_500), EventStatus.OPEN_FOR_PREDICTIONS, EventFormat.STANDARD, 1, true);
        ArenaEvent valorantOpen = event("demo-vct-open", vct, leviatan, loud, "Leviatán x LOUD",
                now.plusSeconds(28_800), now.plusSeconds(27_900), EventStatus.OPEN_FOR_PREDICTIONS, EventFormat.BO3, 3, true);
        ArenaEvent tennisOpen = event("demo-tennis-open", wimbledon, alcaraz, sinner, "Alcaraz x Sinner",
                now.plusSeconds(86_400), now.plusSeconds(84_600), EventStatus.OPEN_FOR_PREDICTIONS, EventFormat.STANDARD, 1, true);
        ArenaEvent lolOpen = event("demo-lol-open", lck, t1, geng, "T1 x Gen.G",
                now.plusSeconds(172_800), now.plusSeconds(171_000), EventStatus.OPEN_FOR_PREDICTIONS, EventFormat.BO3, 3, false);
        ArenaEvent raceOpen = event("demo-f1-open", formula1, null, null, "Grande Prêmio da Arena",
                now.plusSeconds(259_200), now.plusSeconds(255_600), EventStatus.OPEN_FOR_PREDICTIONS, EventFormat.RACE, 1, true);
        seedEventParticipants(raceOpen, verstappen, norris, leclerc, piastri);
        ArenaEvent settled = event("demo-football-settled", brasileirao, flamengo, palmeiras, "Flamengo x Palmeiras · histórico",
                now.plusSeconds(7_200), now.plusSeconds(6_300), EventStatus.OPEN_FOR_PREDICTIONS, EventFormat.STANDARD, 1, false);

        market(footballLive, "WINNER", "Vencedor da partida", MarketStatus.SUSPENDED, 25,
                choice("HOME", "Palmeiras", "1.65"), choice("DRAW", "Empate", "3.30"), choice("AWAY", "Flamengo", "2.10"));
        market(csLive, "SERIES_WINNER", "Vencedor da série", MarketStatus.SUSPENDED, 25,
                choice("HOME", "FURIA", "1.80"), choice("AWAY", "NAVI", "1.95"));
        PredictionMarket nbaWinner = market(basketballOpen, "WINNER", "Vencedor da partida", MarketStatus.OPEN, 20,
                choice("HOME", "Boston Celtics", "1.72"), choice("AWAY", "Dallas Mavericks", "2.05"));
        PredictionMarket valorantWinner = market(valorantOpen, "SERIES_WINNER", "Vencedor da série", MarketStatus.OPEN, 20,
                choice("HOME", "Leviatán", "1.88"), choice("AWAY", "LOUD", "1.84"));
        market(tennisOpen, "MATCH_WINNER", "Vencedor da partida", MarketStatus.OPEN, 20,
                choice("HOME", "Carlos Alcaraz", "1.66"), choice("AWAY", "Jannik Sinner", "2.12"));
        market(lolOpen, "SERIES_WINNER", "Vencedor da série", MarketStatus.OPEN, 20,
                choice("HOME", "T1", "1.78"), choice("AWAY", "Gen.G", "1.98"));
        market(raceOpen, "RACE_WINNER", "Vencedor da corrida", MarketStatus.OPEN, 20,
                choice("VER", "Max Verstappen", "2.10"), choice("NOR", "Lando Norris", "2.35"),
                choice("LEC", "Charles Leclerc", "3.10"), choice("PIA", "Oscar Piastri", "3.40"));
        PredictionMarket historicalMarket = market(settled, "WINNER", "Vencedor da partida", MarketStatus.OPEN, 20,
                choice("HOME", "Flamengo", "2.05"), choice("DRAW", "Empate", "3.10"), choice("AWAY", "Palmeiras", "1.75"));

        liveEvents.refresh();
        List<User> demoUsers = users.findAll().stream().filter(user -> user.getEmail().endsWith("@arenapredict.com") || user.getEmail().endsWith("@bolao.com")).toList();
        demoUsers.forEach(wallets::ensureWallet);
        ArenaPool pool = seedPool(brasileirao, sport.get("FOOTBALL"), demoUsers);
        for (User user : demoUsers) {
            placeIfAbsent(user, basketballOpen, nbaWinner, "HOME", 120, null, "seed-active-nba");
            placeIfAbsent(user, valorantOpen, valorantWinner, user.getRole().name().equals("ADMIN") ? "HOME" : "AWAY", 80, null, "seed-active-vct");
            placeIfAbsent(user, settled, historicalMarket, user.getRole().name().equals("ADMIN") ? "HOME" : "AWAY", 100, pool, "seed-historical");
        }
        settled.setStartsAt(now.minusSeconds(86_400)); settled.setPredictionClosesAt(now.minusSeconds(90_000));
        settled.setHomeScore(1); settled.setAwayScore(2); settled.setStatus(EventStatus.FINISHED);
        events.save(settled);
        if (historicalMarket.getStatus() != MarketStatus.SETTLED) {
            historicalMarket.setStatus(MarketStatus.CLOSED);
            markets.save(historicalMarket);
            predictions.settleMarket(historicalMarket.getId(), "AWAY");
        }
        demoUsers.forEach(this::seedNotifications);
    }

    private Sport sport(String code, String name, SportCategory category, String icon, int order) {
        return sports.findByCodeIgnoreCase(code).orElseGet(() -> { Sport value = new Sport(); value.setCode(code); value.setName(name); value.setCategory(category); value.setIcon(icon); value.setDisplayOrder(order); return sports.save(value); });
    }
    private Championship championship(Sport sport, String name, String slug, String season) {
        return championships.findBySportAndSlugAndSeason(sport, slug, season).orElseGet(() -> { Championship value = new Championship(); value.setSport(sport); value.setName(name); value.setSlug(slug); value.setSeason(season); return championships.save(value); });
    }
    private Competitor competitor(Sport sport, String name, String code, String country) {
        return competitors.findBySportAndCodeIgnoreCase(sport, code).orElseGet(() -> { Competitor value = new Competitor(); value.setSport(sport); value.setName(name); value.setCode(code); value.setCountry(country); return competitors.save(value); });
    }
    private ArenaEvent event(String key, Championship championship, Competitor home, Competitor away, String title,
                             Instant starts, Instant closes, EventStatus status, EventFormat format, int bestOf, boolean featured) {
        ArenaEvent value = events.findByExternalKey(key).orElseGet(ArenaEvent::new); value.setExternalKey(key); value.setChampionship(championship);
        value.setHomeCompetitor(home); value.setAwayCompetitor(away); value.setTitle(title); value.setStage("Demonstração"); value.setVenue("Arena digital");
        value.setBroadcast("Provider interno demo"); value.setStartsAt(starts); value.setPredictionClosesAt(closes); value.setStatus(status);
        value.setFormat(format); value.setBestOf(bestOf); value.setFeatured(featured); value.setDemo(true); return events.save(value);
    }
    private PredictionMarket market(ArenaEvent event, String code, String name, MarketStatus status, int minimum, Option... choices) {
        PredictionMarket value = markets.findByEventAndCode(event, code).orElseGet(() -> { PredictionMarket created = new PredictionMarket(); created.setEvent(event); created.setCode(code); return created; });
        value.setName(name); if (value.getStatus() != MarketStatus.SETTLED) value.setStatus(status); value.setMinimumPoints(minimum); value = markets.save(value);
        for (Option choice : choices) { if (options.findByMarketAndKey(value, choice.key()).isEmpty()) { MarketOption option = new MarketOption(); option.setMarket(value); option.setKey(choice.key()); option.setLabel(choice.label()); option.setMultiplier(new BigDecimal(choice.multiplier())); options.save(option); } }
        return value;
    }
    private void seedEventParticipants(ArenaEvent event, Competitor... values) {
        Map<Long, EventParticipant> existing = eventParticipants.findByEventOrderByDisplayOrderAsc(event).stream()
                .collect(java.util.stream.Collectors.toMap(value -> value.getCompetitor().getId(), value -> value));
        for (int index = 0; index < values.length; index++) {
            Competitor competitor = values[index];
            EventParticipant participant = existing.getOrDefault(competitor.getId(), new EventParticipant());
            participant.setEvent(event); participant.setCompetitor(competitor); participant.setDisplayOrder(index);
            eventParticipants.save(participant);
        }
    }
    private ArenaPool seedPool(Championship championship, Sport sport, List<User> demoUsers) {
        ArenaPool pool = pools.findByInviteCodeIgnoreCase("ARENA26").orElseGet(() -> { ArenaPool value = new ArenaPool(); value.setName("Liga Arena 2026"); value.setDescription("Bolão demo entre amigos com recompensas exclusivamente virtuais."); value.setChampionship(championship); value.setSport(sport); value.setOwner(demoUsers.stream().filter(user -> user.getRole().name().equals("ADMIN")).findFirst().orElseGet(() -> users.findAll().getFirst())); value.setInviteCode("ARENA26"); value.setPublicPool(true); value.setMaxParticipants(100); value.setVirtualPrizePoints(2500); value.setRules("Pontuação por acertos; sem entrada financeira e sem conversão dos pontos em dinheiro."); return pools.save(value); });
        pool.setPoolType(PoolType.LEAGUE);
        pool.setRecurring(true);
        pool = pools.save(pool);
        for (User user : demoUsers) if (members.findByPoolAndUser(pool, user).isEmpty()) { ArenaPoolMember member = new ArenaPoolMember(); member.setPool(pool); member.setUser(user); member.setModerator(user.getId().equals(pool.getOwner().getId())); members.save(member); }
        return pool;
    }
    private void placeIfAbsent(User user, ArenaEvent event, PredictionMarket market, String optionKey, int points, ArenaPool pool, String key) {
        MarketOption option = options.findByMarketAndKey(market, optionKey).orElseThrow();
        try { predictions.place(new PlacePredictionRequest(event.getId(), market.getId(), option.getId(), points,
                pool == null ? null : pool.getId(), key), key, user); }
        catch (ArenaProblem.RuleViolation ignored) { /* Existing demo databases can have already-closed sample events. */ }
    }
    private void seedNotifications(User user) {
        if (!notificationRepository.findTop100ByUserOrderByCreatedAtDesc(user).isEmpty()) return;
        notification(user, NotificationType.POOL_INVITE, "Bem-vindo à Liga Arena", "Você já faz parte do bolão de demonstração.", "/pools");
        notification(user, NotificationType.EVENT_STARTED, "Evento ao vivo · demo", "Palmeiras x Flamengo está em andamento com dados simulados.", "/events");
        notification(user, NotificationType.ADMIN_NOTICE, "Pontos exclusivamente virtuais", "Os pontos da plataforma não possuem valor financeiro.", "/help");
    }
    private void notification(User user, NotificationType type, String title, String message, String target) { ArenaNotification value = new ArenaNotification(); value.setUser(user); value.setType(type); value.setTitle(title); value.setMessage(message); value.setTargetUrl(target); notificationRepository.save(value); }
    private Option choice(String key, String label, String multiplier) { return new Option(key, label, multiplier); }
    private record Option(String key, String label, String multiplier) { }
}
