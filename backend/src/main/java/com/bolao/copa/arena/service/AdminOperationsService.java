package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.AdminOperationsDtos.*;
import static com.bolao.copa.arena.api.ArenaDtos.VIRTUAL_POINTS_NOTICE;

import com.bolao.copa.arena.api.ArenaDtos.AdminDashboardResponse;
import com.bolao.copa.arena.api.ArenaDtos.CompetitorSummary;
import com.bolao.copa.arena.api.ArenaDtos.EventParticipantResponse;
import com.bolao.copa.arena.api.ArenaDtos.MarketOptionResponse;
import com.bolao.copa.arena.api.ExperienceDtos.ReportResponse;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOperationsService {
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository users;
    private final PointWalletRepository wallets;
    private final ArenaPoolRepository pools;
    private final ArenaPoolMemberRepository poolMembers;
    private final ChampionshipRepository championships;
    private final ArenaEventRepository events;
    private final PredictionMarketRepository markets;
    private final MarketOptionRepository marketOptions;
    private final PointLedgerRepository ledger;
    private final ArenaDashboardService dashboards;
    private final CommunityService community;
    private final EventParticipantRepository eventParticipants;
    private final String brandName;
    private final boolean demoMode;
    private final boolean demoLiveProvider;

    public AdminOperationsService(UserRepository users, PointWalletRepository wallets,
                                  ArenaPoolRepository pools, ArenaPoolMemberRepository poolMembers,
                                  ChampionshipRepository championships, ArenaEventRepository events,
                                  PredictionMarketRepository markets, MarketOptionRepository marketOptions,
                                  PointLedgerRepository ledger, ArenaDashboardService dashboards,
                                  CommunityService community, EventParticipantRepository eventParticipants,
                                  @Value("${app.brand.name:ArenaPredict}") String brandName,
                                  @Value("${app.demo.enabled:true}") boolean demoMode,
                                  @Value("${app.demo.live-provider-enabled:true}") boolean demoLiveProvider) {
        this.users = users;
        this.wallets = wallets;
        this.pools = pools;
        this.poolMembers = poolMembers;
        this.championships = championships;
        this.events = events;
        this.markets = markets;
        this.marketOptions = marketOptions;
        this.ledger = ledger;
        this.dashboards = dashboards;
        this.community = community;
        this.eventParticipants = eventParticipants;
        this.brandName = brandName;
        this.demoMode = demoMode;
        this.demoLiveProvider = demoLiveProvider;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> users(int page, int size, String search) {
        Pageable pageable = page(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> result = hasText(search)
                ? users.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(search.trim(), search.trim(), pageable)
                : users.findAll(pageable);
        Map<Long, PointWallet> walletByUser = result.isEmpty() ? Map.of() : wallets.findByUserIn(result.getContent()).stream()
                .collect(Collectors.toMap(wallet -> wallet.getUser().getId(), Function.identity()));
        return result.map(user -> userResponse(user, walletByUser.get(user.getId())));
    }

    @Transactional(readOnly = true)
    public Page<AdminPoolResponse> pools(int page, int size, String search) {
        Pageable pageable = page(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ArenaPool> result = hasText(search)
                ? pools.findByNameContainingIgnoreCase(search.trim(), pageable)
                : pools.findAll(pageable);
        Map<Long, Long> membersByPool = result.isEmpty() ? Map.of() : poolMembers.countGrouped(result.getContent()).stream()
                .collect(Collectors.toMap(ArenaPoolMemberRepository.PoolMemberCount::getPoolId,
                        ArenaPoolMemberRepository.PoolMemberCount::getTotal));
        return result.map(pool -> poolResponse(pool, membersByPool.getOrDefault(pool.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public Page<AdminChampionshipResponse> championships(int page, int size, String search) {
        Pageable pageable = page(page, size, Sort.by(Sort.Direction.ASC, "name"));
        Page<Championship> result = hasText(search)
                ? championships.search(search.trim(), pageable)
                : championships.findAll(pageable);
        return result.map(this::championshipResponse);
    }

    @Transactional(readOnly = true)
    public Page<AdminEventResponse> events(int page, int size, String search) {
        Pageable pageable = page(page, size, Sort.by(Sort.Direction.DESC, "startsAt"));
        Page<ArenaEvent> result = hasText(search)
                ? events.search(search.trim(), pageable)
                : events.findAll(pageable);
        Map<Long, List<EventParticipantResponse>> participantsByEvent = result.isEmpty() ? Map.of()
                : eventParticipants.findByEventInOrderByEventIdAscDisplayOrderAsc(result.getContent()).stream()
                        .collect(Collectors.groupingBy(value -> value.getEvent().getId(), LinkedHashMap::new,
                                Collectors.mapping(value -> new EventParticipantResponse(value.getId(), competitorSummary(value.getCompetitor()),
                                        value.getDisplayOrder(), value.getPosition(), value.getScoreLabel()), Collectors.toList())));
        return result.map(event -> eventResponse(event, participantsByEvent.getOrDefault(event.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public Page<AdminMarketResponse> markets(int page, int size, String search) {
        Pageable pageable = page(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<PredictionMarket> result = hasText(search)
                ? markets.search(search.trim(), pageable)
                : markets.findAll(pageable);
        Map<Long, List<MarketOptionResponse>> optionsByMarket = result.isEmpty() ? Map.of()
                : marketOptions.findForMarkets(result.getContent()).stream().collect(Collectors.groupingBy(
                        option -> option.getMarket().getId(), LinkedHashMap::new,
                        Collectors.mapping(this::marketOptionResponse, Collectors.toList())));
        return result.map(market -> marketResponse(market, optionsByMarket.getOrDefault(market.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public List<ScoringRuleResponse> scoringRules() {
        return List.of(
                new ScoringRuleResponse("prediction-debit", "Confirmação de palpite",
                        "Ao confirmar, os pontos informados são debitados uma única vez da carteira virtual.",
                        "ACTIVE", "saldo = saldo - pontosDoPalpite", "pontos virtuais", null),
                new ScoringRuleResponse("potential-points", "Potencial de pontos",
                        "O potencial usa o multiplicador simulado da opção e arredonda para baixo.",
                        "ACTIVE", "potencial = floor(pontosDoPalpite × multiplicador)", "pontos virtuais", null),
                new ScoringRuleResponse("winner-reward", "Recompensa por acerto",
                        "Um palpite vencedor recebe exatamente o potencial calculado; o processamento é idempotente.",
                        "ACTIVE", "crédito = potencial", "pontos virtuais", null),
                new ScoringRuleResponse("refund", "Reembolso",
                        "Cancelamentos permitidos e eventos cancelados devolvem somente os pontos utilizados.",
                        "ACTIVE", "reembolso = pontosDoPalpite", "pontos virtuais", null),
                new ScoringRuleResponse("market-minimum", "Mínimo por mercado",
                        "Cada mercado define seu próprio mínimo e palpites abaixo dele são recusados.",
                        "ACTIVE", "pontosDoPalpite >= mínimoDoMercado", "pontos virtuais", null)
        );
    }

    @Transactional(readOnly = true)
    public List<OperationalReportResponse> reports() {
        AdminDashboardResponse metrics = dashboards.adminDashboard();
        Instant snapshot = Instant.now();
        return List.of(
                report("registered-users", "Usuários cadastrados", "Contas registradas na plataforma.", metrics.users(), "usuários", snapshot),
                report("live-events", "Eventos ao vivo", "Eventos atualmente marcados como ao vivo.", metrics.liveEvents(), "eventos", snapshot),
                report("upcoming-events", "Próximos eventos", "Eventos agendados ou abertos para palpites.", metrics.upcomingEvents(), "eventos", snapshot),
                report("active-predictions", "Palpites ativos", "Palpites aguardando processamento de resultado.", metrics.activePredictions(), "palpites", snapshot),
                report("open-markets", "Mercados abertos", "Mercados disponíveis para novos palpites.", metrics.openMarkets(), "mercados", snapshot),
                report("active-pools", "Bolões ativos", "Bolões abertos ou em andamento.", metrics.activePools(), "bolões", snapshot),
                report("points-moved", "Pontos movimentados", "Volume absoluto registrado no razão de pontos virtuais.", metrics.pointsMoved(), "pontos virtuais", snapshot),
                report("awaiting-results", "Eventos aguardando resultado", "Eventos encerrados que exigem conferência operacional.", metrics.eventsAwaitingResult(), "eventos", snapshot)
        );
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> audit(int page, int size, String search) {
        Pageable pageable = page(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PointLedgerEntry> entries = hasText(search)
                ? ledger.search(search.trim(), pageable)
                : ledger.findAll(pageable);
        return entries.map(this::auditResponse);
    }

    @Transactional(readOnly = true)
    public List<PublicSettingResponse> settings() {
        return List.of(
                setting("brand-name", "Nome da aplicação", "Marca pública configurável da plataforma.",
                        "ACTIVE", brandName, "BRANDING"),
                setting("demo-mode", "Modo demonstração", "Controla a carga explícita de dados de portfólio.",
                        enabled(demoMode), Boolean.toString(demoMode), "DEMO"),
                setting("demo-live-provider", "Provider ao vivo demonstrativo",
                        "Atualizações simuladas; nenhuma integração externa é presumida.",
                        enabled(demoLiveProvider), Boolean.toString(demoLiveProvider), "DEMO"),
                setting("initial-demo-points", "Bônus inicial demo",
                        "Saldo inicial concedido apenas em pontos virtuais.", "ACTIVE",
                        Integer.toString(PointWalletService.INITIAL_DEMO_POINTS), "VIRTUAL_POINTS"),
                setting("virtual-points-policy", "Natureza dos pontos", VIRTUAL_POINTS_NOTICE,
                        "ENFORCED", "SEM_VALOR_FINANCEIRO", "VIRTUAL_POINTS")
        );
    }

    @Transactional(readOnly = true)
    public Page<ModerationQueueResponse> moderation(int page, int size) {
        return community.reports(page, safeSize(size)).map(this::moderationResponse);
    }

    private AdminUserResponse userResponse(User user, PointWallet wallet) {
        return new AdminUserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().canonical(),
                "ACTIVE", wallet == null ? 0 : wallet.getBalance(), user.getCreatedAt(),
                wallet == null ? user.getCreatedAt() : wallet.getUpdatedAt());
    }

    private AdminPoolResponse poolResponse(ArenaPool pool, long participantCount) {
        return new AdminPoolResponse(pool.getId(), pool.getName(), pool.getDescription(), pool.getStatus(),
                pool.getOwner().getId(), pool.getOwner().getName(), pool.isPublicPool() ? "PUBLIC" : "PRIVATE",
                Math.toIntExact(participantCount), pool.getMaxParticipants(), pool.getVirtualPrizePoints(),
                pool.getSport() == null ? null : pool.getSport().getName(),
                pool.getChampionship() == null ? null : pool.getChampionship().getName(), pool.getPoolType(), pool.isRecurring(),
                pool.getStartsAt(), pool.getEndsAt(), pool.getCreatedAt());
    }

    private AdminChampionshipResponse championshipResponse(Championship championship) {
        return new AdminChampionshipResponse(championship.getId(), championship.getName(), championship.getSlug(),
                championship.getStatus().name(), championship.getSport().getId(), championship.getSport().getName(),
                championship.getSeason(), championship.getImageUrl(), championship.getStartsAt(), championship.getEndsAt());
    }

    private AdminEventResponse eventResponse(ArenaEvent event, List<EventParticipantResponse> participants) {
        Championship championship = event.getChampionship();
        return new AdminEventResponse(event.getId(), event.getTitle(), event.getExternalKey(), event.getStatus().name(),
                championship.getId(), championship.getName(), championship.getSport().getName(),
                competitorSummary(event.getHomeCompetitor()), competitorSummary(event.getAwayCompetitor()),
                event.getStage(), event.getVenue(), event.getBroadcast(), event.getImageUrl(), event.getFormat().name(),
                event.getBestOf(), participants, event.getStartsAt(),
                event.getPredictionClosesAt(), event.getHomeScore(), event.getAwayScore(), event.isFeatured(), event.isDemo());
    }

    private AdminMarketResponse marketResponse(PredictionMarket market, List<MarketOptionResponse> options) {
        return new AdminMarketResponse(market.getId(), market.getName(), market.getCode(), market.getStatus().name(),
                market.getEvent().getId(), market.getEvent().getTitle(), market.getEvent().getStatus().name(),
                market.getMinimumPoints(), options.size(), options, market.getResultOptionKey(), market.getSettledAt());
    }

    private MarketOptionResponse marketOptionResponse(MarketOption option) {
        return new MarketOptionResponse(option.getId(), option.getKey(), option.getLabel(),
                option.getMultiplier(), option.isActive());
    }

    private CompetitorSummary competitorSummary(Competitor competitor) {
        return competitor == null ? null : new CompetitorSummary(competitor.getId(), competitor.getName(),
                competitor.getCode(), competitor.getImageUrl());
    }

    private AuditEntryResponse auditResponse(PointLedgerEntry entry) {
        User actor = entry.getWallet().getUser();
        return new AuditEntryResponse(entry.getId(), entry.getDescription(), entry.getType().name(),
                actor.getName(), actor.getId(), entry.getType(), "RECORDED", entry.getAmount(),
                entry.getBalanceAfter(), entry.getReferenceType(), entry.getReferenceId(), entry.getCreatedAt());
    }

    private ModerationQueueResponse moderationResponse(ReportResponse report) {
        return new ModerationQueueResponse(report.id(), "Denúncia #" + report.id(), report.postId(),
                report.postExcerpt(), report.reporterId(), report.reporterName(), report.reason(), report.status(),
                report.moderatorNote(), report.createdAt(), report.reviewedAt());
    }

    private OperationalReportResponse report(String id, String name, String description, long value,
                                               String unit, Instant snapshot) {
        return new OperationalReportResponse(id, name, description, "CURRENT", value, unit, snapshot);
    }

    private PublicSettingResponse setting(String id, String name, String description, String status,
                                          String value, String category) {
        return new PublicSettingResponse(id, name, description, status, value, category, null);
    }

    private Pageable page(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(0, page), safeSize(size), sort);
    }

    private int safeSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String enabled(boolean value) {
        return value ? "ENABLED" : "DISABLED";
    }
}
