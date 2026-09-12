package com.bolao.copa.arena.config;

import static com.bolao.copa.arena.api.ArenaDtos.PlacePredictionRequest;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import com.bolao.copa.config.DemoParticipantCatalog;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class ArenaDemoInitializer {
    private static final Duration MAX_DEMO_LIVE_AGE = Duration.ofHours(6);
    private static final String PALMEIRAS_LOGO = null;
    private static final String FLAMENGO_LOGO = null;
    private static final String FURIA_LOGO = "/assets/teams/furia.svg";
    private static final String LEGACY_FURIA_WORDMARK = "https://us.furia.gg/images/brand/logotipo-white.svg";
    private static final String LEGACY_GENERIC_BADGE = "/assets/teams/team-placeholder.svg";
    private static final String NAVI_LOGO = null;
    private static final String LOUD_LOGO = null;
    private static final String T1_LOGO = null;
    private static final String GENG_LOGO = null;
    private static final String LEVIATAN_IDENTITY = "/assets/teams/leviatan.svg";
    private static final String ALCARAZ_IDENTITY = "/assets/teams/carlos-alcaraz.svg";
    private static final String SINNER_IDENTITY = "/assets/teams/jannik-sinner.svg";
    private static final String VERSTAPPEN_IDENTITY = "/assets/teams/max-verstappen.svg";
    private static final String NORRIS_IDENTITY = "/assets/teams/lando-norris.svg";
    private static final String LECLERC_IDENTITY = "/assets/teams/charles-leclerc.svg";
    private static final String PIASTRI_IDENTITY = "/assets/teams/oscar-piastri.svg";
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
    private final ArenaPredictionRepository predictionRepository;
    private final UserRepository users;
    private final PointWalletService wallets;
    private final ArenaPredictionService predictions;
    private final DemoLiveEventService liveEvents;

    public ArenaDemoInitializer(SportRepository sports, ChampionshipRepository championships,
                                CompetitorRepository competitors, ArenaEventRepository events,
                                PredictionMarketRepository markets, MarketOptionRepository options,
                                ArenaPoolRepository pools, ArenaPoolMemberRepository members,
                                ArenaNotificationRepository notificationRepository, EventParticipantRepository eventParticipants,
                                ArenaPredictionRepository predictionRepository,
                                UserRepository users,
                                PointWalletService wallets, ArenaPredictionService predictions,
                                DemoLiveEventService liveEvents) {
        this.sports = sports; this.championships = championships; this.competitors = competitors; this.events = events;
        this.markets = markets; this.options = options; this.pools = pools; this.members = members;
        this.notificationRepository = notificationRepository; this.users = users; this.wallets = wallets;
        this.eventParticipants = eventParticipants;
        this.predictionRepository = predictionRepository;
        this.predictions = predictions; this.liveEvents = liveEvents;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
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
        Championship superliga = championship(sport.get("VOLLEYBALL"), "Superliga Arena", "superliga-arena", "2026");
        Championship gridiron = championship(sport.get("AMERICAN_FOOTBALL"), "Temporada Arena Football", "arena-football", "2026");
        Championship dota = championship(sport.get("DOTA2"), "Arena Dota Series", "arena-dota-series", "2026");

        Competitor palmeiras = competitor(sport.get("FOOTBALL"), "Palmeiras", "PAL", "Brasil", PALMEIRAS_LOGO);
        Competitor flamengo = competitor(sport.get("FOOTBALL"), "Flamengo", "FLA", "Brasil", FLAMENGO_LOGO);
        Competitor celtics = competitor(sport.get("BASKETBALL"), "Boston Celtics", "BOS", "Estados Unidos", null);
        Competitor mavericks = competitor(sport.get("BASKETBALL"), "Dallas Mavericks", "DAL", "Estados Unidos", null);
        Competitor furia = competitor(sport.get("CS2"), "FURIA", "FURIA", "Brasil", FURIA_LOGO);
        Competitor navi = competitor(sport.get("CS2"), "NAVI", "NAVI", "Ucrânia", NAVI_LOGO);
        Competitor leviatan = competitor(sport.get("VALORANT"), "Leviatán", "LEV", "Argentina", LEVIATAN_IDENTITY);
        Competitor loud = competitor(sport.get("VALORANT"), "LOUD", "LOUD", "Brasil", LOUD_LOGO);
        Competitor alcaraz = competitor(sport.get("TENNIS"), "Carlos Alcaraz", "ALC", "Espanha", ALCARAZ_IDENTITY);
        Competitor sinner = competitor(sport.get("TENNIS"), "Jannik Sinner", "SIN", "Itália", SINNER_IDENTITY);
        Competitor t1 = competitor(sport.get("LEAGUE_OF_LEGENDS"), "T1", "T1", "Coreia do Sul", T1_LOGO);
        Competitor geng = competitor(sport.get("LEAGUE_OF_LEGENDS"), "Gen.G", "GENG", "Coreia do Sul", GENG_LOGO);
        Competitor verstappen = competitor(sport.get("MOTORSPORT"), "Max Verstappen", "VER", "Países Baixos", VERSTAPPEN_IDENTITY);
        Competitor norris = competitor(sport.get("MOTORSPORT"), "Lando Norris", "NOR", "Reino Unido", NORRIS_IDENTITY);
        Competitor leclerc = competitor(sport.get("MOTORSPORT"), "Charles Leclerc", "LEC", "Mônaco", LECLERC_IDENTITY);
        Competitor piastri = competitor(sport.get("MOTORSPORT"), "Oscar Piastri", "PIA", "Austrália", PIASTRI_IDENTITY);
        Competitor hamilton = competitor(sport.get("MOTORSPORT"), "Lewis Hamilton", "HAM", "Reino Unido", null);
        Competitor russell = competitor(sport.get("MOTORSPORT"), "George Russell", "RUS", "Reino Unido", null);
        Competitor minas = competitor(sport.get("VOLLEYBALL"), "Minas", "MIN", "Brasil", null);
        Competitor cruzeiro = competitor(sport.get("VOLLEYBALL"), "Sada Cruzeiro", "CRU", "Brasil", null);
        Competitor falcons = competitor(sport.get("AMERICAN_FOOTBALL"), "Aurora Falcons", "FAL", "Brasil", null);
        Competitor bears = competitor(sport.get("AMERICAN_FOOTBALL"), "Horizonte Bears", "BEA", "Brasil", null);
        Competitor spirit = competitor(sport.get("DOTA2"), "Team Spirit", "SPI", "Internacional", null);
        Competitor liquid = competitor(sport.get("DOTA2"), "Team Liquid", "LIQ", "Internacional", null);

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
        ArenaEvent footballOpen = event("demo-football-open", brasileirao, palmeiras, flamengo, "Palmeiras x Flamengo · próxima rodada",
                now.plusSeconds(21_600), now.plusSeconds(21_300), EventStatus.SCHEDULED, EventFormat.STANDARD, 1, true);
        ArenaEvent csOpen = event("demo-cs2-open", blast, furia, navi, "FURIA x NAVI · próxima série",
                now.plusSeconds(32_400), now.plusSeconds(32_100), EventStatus.SCHEDULED, EventFormat.BO3, 3, false);
        ArenaEvent volleyballOpen = event("demo-volleyball-open", superliga, minas, cruzeiro, "Minas x Sada Cruzeiro",
                now.plusSeconds(36_000), now.plusSeconds(35_700), EventStatus.SCHEDULED, EventFormat.BO5, 5, false);
        ArenaEvent americanOpen = event("demo-american-football-open", gridiron, falcons, bears, "Aurora Falcons x Horizonte Bears",
                now.plusSeconds(39_600), now.plusSeconds(39_300), EventStatus.SCHEDULED, EventFormat.STANDARD, 1, false);
        ArenaEvent dotaOpen = event("demo-dota2-open", dota, spirit, liquid, "Team Spirit x Team Liquid",
                now.plusSeconds(46_800), now.plusSeconds(46_500), EventStatus.SCHEDULED, EventFormat.BO3, 3, false);
        seedEventParticipants(footballOpen, palmeiras, flamengo);
        seedEventParticipants(csOpen, furia, navi);
        seedEventParticipants(volleyballOpen, minas, cruzeiro);
        seedEventParticipants(americanOpen, falcons, bears);
        seedEventParticipants(dotaOpen, spirit, liquid);
        seedEventParticipants(footballLive, palmeiras, flamengo);
        seedEventParticipants(csLive, furia, navi);
        seedEventParticipants(basketballOpen, celtics, mavericks);
        seedEventParticipants(valorantOpen, leviatan, loud);
        seedEventParticipants(tennisOpen, alcaraz, sinner);
        seedEventParticipants(lolOpen, t1, geng);
        seedEventParticipants(raceOpen, verstappen, norris, leclerc, piastri, hamilton, russell);
        if (tennisOpen.getBestOf() == 1 && tennisOpen.getStatus() != EventStatus.FINISHED
                && tennisOpen.getStatus() != EventStatus.CANCELLED) {
            tennisOpen.setBestOf(3);
            tennisOpen.setFormat(EventFormat.BO3);
            events.save(tennisOpen);
        }
        boolean newHistoricalEvent = events.findByExternalKey("demo-football-settled").isEmpty();
        ArenaEvent settled = event("demo-football-settled", brasileirao, flamengo, palmeiras, "Flamengo x Palmeiras · histórico",
                now.plusSeconds(7_200), now.plusSeconds(6_300), EventStatus.OPEN_FOR_PREDICTIONS, EventFormat.STANDARD, 1, false);
        boolean newCancelledEvent = events.findByExternalKey("demo-football-cancelled").isEmpty();
        ArenaEvent cancelled = event("demo-football-cancelled", brasileirao, palmeiras, flamengo,
                "Palmeiras x Flamengo", now.plusSeconds(43_200), now.plusSeconds(41_400),
                EventStatus.OPEN_FOR_PREDICTIONS, EventFormat.STANDARD, 1, false);
        seedEventParticipants(settled, flamengo, palmeiras);
        seedEventParticipants(cancelled, palmeiras, flamengo);

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
                choice("LEC", "Charles Leclerc", "3.10"), choice("PIA", "Oscar Piastri", "3.40"),
                choice("HAM", "Lewis Hamilton", "5.50"), choice("RUS", "George Russell", "6.00"));
        PredictionMarket historicalMarket = market(settled, "WINNER", "Vencedor da partida", MarketStatus.OPEN, 20,
                choice("HOME", "Flamengo", "2.05"), choice("DRAW", "Empate", "3.10"), choice("AWAY", "Palmeiras", "1.75"));
        PredictionMarket cancelledMarket = market(cancelled, "WINNER", "Vencedor da partida", MarketStatus.OPEN, 20,
                choice("HOME", "Palmeiras", "1.75"), choice("DRAW", "Empate", "3.20"), choice("AWAY", "Flamengo", "2.00"));

        liveEvents.refresh();
        List<User> demoParticipants = users.findAll().stream()
                .filter(user -> user.getRole().canonical() == com.bolao.copa.entity.UserRole.PARTICIPANTE)
                .filter(user -> DemoParticipantCatalog.contains(user.getEmail()))
                .toList();
        demoParticipants.forEach(wallets::ensureWallet);
        ArenaPool pool = seedPool(brasileirao, sport.get("FOOTBALL"), demoParticipants);
        for (User user : demoParticipants) {
            placeIfAbsent(user, basketballOpen, nbaWinner, "HOME", 120, null, "seed-active-nba");
            placeIfAbsent(user, valorantOpen, valorantWinner,
                    user.getId() % 2 == 0 ? "HOME" : "AWAY", 80, null, "seed-active-vct");
            placeIfAbsent(user, settled, historicalMarket,
                    user.getId() % 2 == 0 ? "HOME" : "AWAY", 100, pool, "seed-historical");
            if (newCancelledEvent)
                placeIfAbsent(user, cancelled, cancelledMarket, "HOME", 60, null, "seed-cancelled-refund");
        }
        if (newHistoricalEvent) {
            settled.setStartsAt(now.minusSeconds(86_400)); settled.setPredictionClosesAt(now.minusSeconds(90_000));
            settled.setHomeScore(1); settled.setAwayScore(2); settled.setStatus(EventStatus.FINISHED);
            events.save(settled);
            historicalMarket.setStatus(MarketStatus.CLOSED);
            markets.save(historicalMarket);
            predictions.settleMarket(historicalMarket.getId(), "AWAY");
        }
        if (newCancelledEvent) predictions.cancelEvent(cancelled.getId());
        demoParticipants.forEach(this::seedNotifications);
    }

    private Sport sport(String code, String name, SportCategory category, String icon, int order) {
        return sports.findByCodeIgnoreCase(code).orElseGet(() -> { Sport value = new Sport(); value.setCode(code); value.setName(name); value.setCategory(category); value.setIcon(icon); value.setDisplayOrder(order); return sports.save(value); });
    }
    private Championship championship(Sport sport, String name, String slug, String season) {
        return championships.findBySportAndSlugAndSeason(sport, slug, season).orElseGet(() -> { Championship value = new Championship(); value.setSport(sport); value.setName(name); value.setSlug(slug); value.setSeason(season); return championships.save(value); });
    }
    private Competitor competitor(Sport sport, String name, String code, String country, String imageUrl) {
        Competitor value = competitors.findBySportAndCodeIgnoreCase(sport, code).orElse(null);
        if (value == null) {
            value = new Competitor();
            value.setSport(sport);
            value.setName(name);
            value.setCode(code);
            value.setCountry(country);
            value.setImageUrl(imageUrl);
            return competitors.save(value);
        }
        boolean changed = false;
        if (!Objects.equals(value.getName(), name)) {
            value.setName(name);
            changed = true;
        }
        if (!Objects.equals(value.getCountry(), country)) {
            value.setCountry(country);
            changed = true;
        }
        if (shouldRepairDemoImage(code, value.getImageUrl(), imageUrl)) {
            value.setImageUrl(imageUrl);
            changed = true;
        }
        return changed ? competitors.save(value) : value;
    }

    private boolean shouldRepairDemoImage(String code, String currentImageUrl, String expectedImageUrl) {
        if (expectedImageUrl == null) {
            return currentImageUrl != null && (currentImageUrl.startsWith("http://") || currentImageUrl.startsWith("https://"));
        }
        if (currentImageUrl == null || currentImageUrl.isBlank() || LEGACY_GENERIC_BADGE.equals(currentImageUrl)) return true;
        return "FURIA".equalsIgnoreCase(code) && !Objects.equals(currentImageUrl, expectedImageUrl);
    }
    private ArenaEvent event(String key, Championship championship, Competitor home, Competitor away, String title,
                             Instant starts, Instant closes, EventStatus status, EventFormat format, int bestOf, boolean featured) {
        ArenaEvent value = events.findByExternalKey(key).orElse(null);
        if (value != null) {
            Instant previousStart = value.getStartsAt();
            if (refreshRollingDemoSchedule(value, status, starts, closes, Instant.now())) {
                long shift = java.time.Duration.between(previousStart, value.getStartsAt()).getSeconds();
                for (var market : markets.findByEventOrderByIdAsc(value)) {
                    if (market.getStatus()==MarketStatus.SETTLED || market.getStatus()==MarketStatus.CANCELLED) continue;
                    if (market.getOpensAt()!=null) market.setOpensAt(market.getOpensAt().plusSeconds(shift));
                    if (market.getClosesAt()!=null) market.setClosesAt(market.getTimingMode()==MarketTimingMode.PRE_MATCH_ONLY
                            ? value.getPredictionClosesAt() : market.getClosesAt().plusSeconds(shift));
                }
                events.save(value);
            }
            return value;
        }
        return events.save(newEvent(key, championship, home, away, title, starts, closes, status, format, bestOf,
                featured));
    }

    private ArenaEvent newEvent(String key, Championship championship, Competitor home, Competitor away, String title,
                                Instant starts, Instant closes, EventStatus status, EventFormat format, int bestOf,
                                boolean featured) {
            ArenaEvent value = new ArenaEvent(); value.setExternalKey(key); value.setChampionship(championship);
            value.setHomeCompetitor(home); value.setAwayCompetitor(away); value.setTitle(title); value.setStage("Demonstração"); value.setVenue("Arena digital");
            value.setBroadcast("Provider interno demo"); value.setStartsAt(starts); value.setPredictionClosesAt(closes); value.setStatus(status);
            value.setFormat(format); value.setBestOf(bestOf); value.setFeatured(featured); value.setDemo(true); return value;
    }

    static boolean refreshRollingDemoSchedule(ArenaEvent event, EventStatus expectedStatus, Instant starts,
                                              Instant closes, Instant now) {
        if (!event.isDemo() || event.getStatus() != expectedStatus) return false;
        boolean expiredPredictionWindow = (expectedStatus == EventStatus.OPEN_FOR_PREDICTIONS || expectedStatus == EventStatus.SCHEDULED)
                && !now.isBefore(event.getPredictionClosesAt());
        boolean staleLiveWindow = expectedStatus == EventStatus.LIVE
                && event.getStartsAt().isBefore(now.minus(MAX_DEMO_LIVE_AGE));
        if (!expiredPredictionWindow && !staleLiveWindow) return false;
        event.setStartsAt(starts);
        event.setPredictionClosesAt(closes);
        return true;
    }
    private PredictionMarket market(ArenaEvent event, String code, String name, MarketStatus status, int minimum, Option... choices) {
        PredictionMarket value = markets.findByEventAndCode(event, code).orElseGet(() -> {
            PredictionMarket created = new PredictionMarket(); created.setEvent(event); created.setCode(code);
            created.setName(name); created.setStatus(status); created.setMinimumPoints(minimum); return markets.save(created);
        });
        for (Option choice : choices) { if (options.findByMarketAndKey(value, choice.key()).isEmpty()) { MarketOption option = new MarketOption(); option.setMarket(value); option.setKey(choice.key()); option.setLabel(choice.label()); option.setMultiplier(new BigDecimal(choice.multiplier())); options.save(option); } }
        return value;
    }
    private void seedEventParticipants(ArenaEvent event, Competitor... values) {
        Map<Long, EventParticipant> existing = eventParticipants.findByEventOrderByDisplayOrderAsc(event).stream()
                .collect(java.util.stream.Collectors.toMap(value -> value.getCompetitor().getId(), value -> value));
        for (int index = 0; index < values.length; index++) {
            Competitor competitor = values[index];
            if (!existing.containsKey(competitor.getId())) {
                EventParticipant participant = new EventParticipant();
                participant.setEvent(event); participant.setCompetitor(competitor); participant.setDisplayOrder(index);
                eventParticipants.save(participant);
            }
        }
    }
    private ArenaPool seedPool(Championship championship, Sport sport, List<User> demoUsers) {
        if (demoUsers.isEmpty()) throw new IllegalStateException("Participantes demo não encontrados.");
        User platformAdmin = users.findByEmailIgnoreCase("admin@arenapredict.com").orElseThrow();
        ArenaPool pool = pools.findByInviteCodeIgnoreCase("ARENA26").orElseGet(ArenaPool::new);
        pool.setName("Liga Arena 2026");
        pool.setDescription("Competição pública da plataforma. Os palpites elegíveis dos inscritos contam automaticamente durante a temporada.");
        pool.setChampionship(null); pool.setSport(null); pool.setOwner(platformAdmin);
        pool.setInviteCode("ARENA26"); pool.setPublicPool(true); pool.setMaxParticipants(100);
        pool.setVirtualPrizePoints(0); pool.setPoolType(PoolType.LEAGUE); pool.setRecurring(false);
        pool.setRules("Classificação pelo retorno em pontos virtuais dos palpites vencedores registrados após a inscrição e dentro da temporada. Todas as modalidades. Desempate por acertos e nome. Os desafios da plataforma concedem suas próprias recompensas; esta liga não distribui prêmio automático. Sem valor financeiro.");
        if (pool.getStartsAt() == null) pool.setStartsAt(LocalDate.now(ZoneOffset.UTC).withDayOfYear(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        if (pool.getEndsAt() == null) pool.setEndsAt(LocalDate.now(ZoneOffset.UTC).plusYears(1).withDayOfYear(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        pool = pools.save(pool);
        for (User user : demoUsers) if (members.findByPoolAndUser(pool, user).isEmpty()) { ArenaPoolMember member = new ArenaPoolMember(); member.setPool(pool); member.setUser(user); member.setModerator(user.getId().equals(pool.getOwner().getId())); members.save(member); }
        seedSocialPool("AMIGOS26", "Arquibancada dos amigos", true, championship, sport, demoUsers);
        seedSocialPool("TURMA26", "Turma da rodada", false, championship, sport, demoUsers.subList(0, Math.min(3, demoUsers.size())));
        return pool;
    }
    private void seedSocialPool(String code, String name, boolean visible, Championship championship, Sport sport, List<User> demoUsers) {
        ArenaPool pool = pools.findByInviteCodeIgnoreCase(code).orElse(null);
        if (pool == null) {
            pool = new ArenaPool(); pool.setName(name); pool.setDescription("Grupo social para acompanhar a rodada com amigos. O criador organiza o bolão.");
            pool.setOwner(demoUsers.getFirst()); pool.setInviteCode(code); pool.setPublicPool(visible);
            pool.setPoolType(PoolType.POOL); pool.setSport(sport); pool.setChampionship(championship);
            pool.setRules("Selecione este bolão ao registrar um palpite do Brasileirão. Ranking interno pelo retorno virtual dos acertos; desempate por acertos e nome. Sem prêmio automático ou valor financeiro.");
            pool = pools.save(pool);
        }
        for (User user : demoUsers) if (members.findByPoolAndUser(pool, user).isEmpty()) {
            ArenaPoolMember member = new ArenaPoolMember(); member.setPool(pool); member.setUser(user);
            member.setModerator(user.getId().equals(pool.getOwner().getId())); members.save(member);
        }
    }
    private void placeIfAbsent(User user, ArenaEvent event, PredictionMarket market, String optionKey, int points, ArenaPool pool, String key) {
        String persistedKey = "prediction:user:" + user.getId() + ":" + key;
        // Demo defaults evolve over time. Existing installations must preserve the
        // original prediction instead of replaying the seed with a changed stake,
        // option or pool and tripping the production idempotency guard.
        if (predictionRepository.findByIdempotencyKey(persistedKey).isPresent()) return;
        // A persisted demo database may already have advanced or closed this
        // sample. Avoid invoking the transactional command in that expected
        // state: catching its exception here would still mark the outer seed
        // transaction rollback-only and prevent the application from starting.
        if (event.getStatus() != EventStatus.OPEN_FOR_PREDICTIONS
                || market.getStatus() != MarketStatus.OPEN
                || !Instant.now().isBefore(event.getPredictionClosesAt())) return;
        MarketOption option = options.findByMarketAndKey(market, optionKey).orElseThrow();
        predictions.place(new PlacePredictionRequest(event.getId(), market.getId(), option.getId(), points,
                pool == null ? null : pool.getId(), key), key, user);
    }
    private void seedNotifications(User user) {
        if (!notificationRepository.findTop100ByUserOrderByCreatedAtDesc(user).isEmpty()) return;
        notification(user, NotificationType.POOL_INVITE, "Bem-vindo à Liga Arena", "Você está inscrito na competição pública da plataforma.", "/leagues");
        notification(user, NotificationType.EVENT_STARTED, "Evento ao vivo · demo", "Palmeiras x Flamengo está em andamento com dados simulados.", "/events");
        notification(user, NotificationType.ADMIN_NOTICE, "Pontos exclusivamente virtuais", "Os pontos da plataforma não possuem valor financeiro.", "/help");
    }
    private void notification(User user, NotificationType type, String title, String message, String target) { ArenaNotification value = new ArenaNotification(); value.setUser(user); value.setType(type); value.setTitle(title); value.setMessage(message); value.setTargetUrl(target); notificationRepository.save(value); }
    private Option choice(String key, String label, String multiplier) { return new Option(key, label, multiplier); }
    private record Option(String key, String label, String multiplier) { }
}
