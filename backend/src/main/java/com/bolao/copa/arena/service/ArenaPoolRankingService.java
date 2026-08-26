package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.ArenaDtos.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArenaPoolRankingService {
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final ArenaPoolRepository pools;
    private final ArenaPoolMemberRepository members;
    private final SportRepository sports;
    private final ChampionshipRepository championships;
    private final ArenaPredictionRepository predictions;
    private final PointWalletRepository wallets;
    private final UserRepository users;
    private final ArenaCatalogService catalog;
    private final ProgressionService progression;

    public ArenaPoolRankingService(ArenaPoolRepository pools, ArenaPoolMemberRepository members,
                                   SportRepository sports, ChampionshipRepository championships,
                                   ArenaPredictionRepository predictions, PointWalletRepository wallets,
                                   UserRepository users, ArenaCatalogService catalog, ProgressionService progression) {
        this.pools = pools; this.members = members; this.sports = sports; this.championships = championships;
        this.predictions = predictions; this.wallets = wallets; this.users = users; this.catalog = catalog; this.progression = progression;
    }

    @Transactional(readOnly = true)
    public List<PoolResponse> list(User current) {
        return pools.findAll().stream().filter(pool -> canView(pool, current)).map(pool -> response(pool, current)).toList();
    }

    @Transactional(readOnly = true)
    public PoolResponse get(Long id, User current) {
        ArenaPool pool = pools.findById(id).filter(value -> canView(value, current))
                .orElseThrow(() -> new ArenaProblem.NotFound("Bolão não encontrado."));
        return response(pool, current);
    }

    @Transactional
    public PoolResponse create(PoolRequest request, User owner) {
        ArenaPool pool = new ArenaPool();
        pool.setName(request.name().trim());
        pool.setDescription(request.description());
        Sport sport = request.sportId() == null ? null : sports.findById(request.sportId())
                .orElseThrow(() -> new ArenaProblem.NotFound("Modalidade não encontrada."));
        Championship championship = request.championshipId() == null ? null : championships.findById(request.championshipId())
                .orElseThrow(() -> new ArenaProblem.NotFound("Campeonato não encontrado."));
        if (sport != null && championship != null && !championship.getSport().getId().equals(sport.getId()))
            throw new ArenaProblem.RuleViolation("O campeonato não pertence à modalidade selecionada.");
        pool.setSport(sport);
        pool.setChampionship(championship);
        pool.setOwner(owner);
        pool.setInviteCode(inviteCode());
        pool.setPublicPool(Boolean.TRUE.equals(request.publicPool()));
        PoolType poolType = request.poolType() == null ? PoolType.POOL : request.poolType();
        if (Boolean.TRUE.equals(request.recurring()) && poolType != PoolType.LEAGUE)
            throw new ArenaProblem.RuleViolation("Somente ligas podem ser recorrentes.");
        pool.setPoolType(poolType);
        pool.setRecurring(Boolean.TRUE.equals(request.recurring()));
        pool.setMaxParticipants(request.maxParticipants() == null ? 100 : request.maxParticipants());
        pool.setVirtualPrizePoints(request.virtualPrizePoints() == null ? 0 : request.virtualPrizePoints());
        pool.setRules(request.rules().trim());
        pool.setStartsAt(request.startsAt());
        pool.setEndsAt(request.endsAt());
        pool = pools.save(pool);
        addMember(pool, owner, true);
        progression.refresh(owner);
        return response(pool, owner);
    }

    @Transactional
    public PoolResponse join(String code, User user) {
        ArenaPool pool = pools.findByInviteCodeIgnoreCaseForUpdate(code.trim())
                .orElseThrow(() -> new ArenaProblem.NotFound("Código de convite inválido."));
        return join(pool, user);
    }

    @Transactional
    public PoolResponse joinPublic(Long id, User user) {
        ArenaPool pool = pools.findByIdForUpdate(id).orElseThrow(() -> new ArenaProblem.NotFound("Bolão não encontrado."));
        if (!pool.isPublicPool()) throw new ArenaProblem.RuleViolation("Bolões privados exigem um código de convite.");
        return join(pool, user);
    }

    private PoolResponse join(ArenaPool pool, User user) {
        // The pool row is locked by both public and invite-code entry points.
        // Capacity checks and the unique membership insert therefore form one
        // atomic decision even when multiple participants join simultaneously.
        if (members.findByPoolAndUser(pool, user).isPresent()) return response(pool, user);
        if (members.countByPool(pool) >= pool.getMaxParticipants()) throw new ArenaProblem.Conflict("Este bolão atingiu o limite de participantes.");
        if (pool.getStatus() != ArenaEnums.PoolStatus.OPEN) throw new ArenaProblem.RuleViolation("Este bolão não está aberto para novos participantes.");
        addMember(pool, user, false);
        progression.refresh(user);
        return response(pool, user);
    }

    @Transactional
    public void leave(Long id, User user) {
        ArenaPool pool = pools.findById(id).orElseThrow(() -> new ArenaProblem.NotFound("Bolão não encontrado."));
        if (pool.getOwner().getId().equals(user.getId()))
            throw new ArenaProblem.RuleViolation("O criador deve transferir a administração antes de sair do bolão.");
        ArenaPoolMember member = members.findByPoolAndUser(pool, user)
                .orElseThrow(() -> new ArenaProblem.NotFound("Você não participa deste bolão."));
        members.delete(member);
    }

    @Transactional(readOnly = true)
    public List<RankingRow> globalRanking(User current) {
        return ranking(current, RankingPeriod.ALL, RankingScope.GLOBAL, null);
    }

    @Transactional(readOnly = true)
    public List<RankingRow> ranking(User current, RankingPeriod period, RankingScope scope, String sport) {
        RankingPeriod effectivePeriod = period == null ? RankingPeriod.ALL : period;
        RankingScope effectiveScope = scope == null ? RankingScope.GLOBAL : scope;
        Instant now = Instant.now();
        Instant since = switch (effectivePeriod) {
            case WEEKLY -> now.minus(Duration.ofDays(7));
            case MONTHLY -> now.minus(Duration.ofDays(30));
            case ALL -> Instant.EPOCH;
        };
        Set<Long> eligibleUsers = effectiveScope == RankingScope.FRIENDS
                ? members.findUsersSharingPoolWith(current).stream().map(User::getId)
                        .collect(java.util.stream.Collectors.toSet())
                : null;
        Map<Long, List<ArenaPrediction>> byUser = predictions.findForRankingSince(since).stream()
                .filter(value -> value.getStatus() == PredictionStatus.ACTIVE
                        || value.getStatus() == PredictionStatus.WON || value.getStatus() == PredictionStatus.LOST)
                .filter(value -> eligibleUsers == null || eligibleUsers.contains(value.getUser().getId()))
                .filter(value -> matchesSport(value, sport))
                .collect(java.util.stream.Collectors.groupingBy(value -> value.getUser().getId(),
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        return rows(byUser.values().stream()
                .map(values -> predictionStats(values.getFirst().getUser(), values)).toList(), current);
    }

    @Transactional(readOnly = true)
    public List<RankingRow> poolRanking(Long poolId, User current) {
        ArenaPool pool = pools.findById(poolId).filter(value -> canView(value, current))
                .orElseThrow(() -> new ArenaProblem.NotFound("Bolão não encontrado."));
        List<ArenaPrediction> poolPredictions = predictions.findByPool(pool);
        List<PlayerStats> stats = members.findByPool(pool).stream().map(member -> {
            List<ArenaPrediction> mine = poolPredictions.stream().filter(p -> p.getUser().getId().equals(member.getUser().getId())).toList();
            return predictionStats(member.getUser(), mine);
        }).toList();
        return rows(stats, current);
    }

    public PoolResponse response(ArenaPool value, User current) {
        boolean owner = current != null && value.getOwner().getId().equals(current.getId());
        boolean joined = current != null && members.findByPoolAndUser(value, current).isPresent();
        return new PoolResponse(value.getId(), value.getName(), value.getDescription(), catalog.sportResponse(value.getSport()),
                catalog.championshipResponse(value.getChampionship()), value.getOwner().getName(), joined || owner ? value.getInviteCode() : null,
                value.isPublicPool(), value.getMaxParticipants(), (int) members.countByPool(value), value.getVirtualPrizePoints(),
                value.getRules(), value.getStatus(), value.getStartsAt(), value.getEndsAt(), joined, owner,
                value.getPoolType(), value.isRecurring());
    }

    private PlayerStats stats(User user, PointWallet wallet, ArenaPool ignored) {
        List<ArenaPrediction> mine = predictions.findByUserOrderByPlacedAtDesc(user);
        PlayerStats calculated = predictionStats(user, mine);
        long score = wallet == null ? 0 : wallet.getBalance();
        return new PlayerStats(user, score, calculated.wins(), calculated.total(), calculated.streak());
    }
    private PlayerStats predictionStats(User user, List<ArenaPrediction> values) {
        long wins = values.stream().filter(value -> value.getStatus() == PredictionStatus.WON).count();
        long total = values.stream().filter(value -> value.getStatus() == PredictionStatus.WON || value.getStatus() == PredictionStatus.LOST).count();
        long score = values.stream().filter(value -> value.getStatus() == PredictionStatus.WON).mapToLong(ArenaPrediction::getRewardedPoints).sum();
        return new PlayerStats(user, score, wins, total, currentStreak(values));
    }
    private boolean matchesSport(ArenaPrediction prediction, String filter) {
        if (filter == null || filter.isBlank()) return true;
        Sport sport = prediction.getEvent().getChampionship().getSport();
        String normalized = filter.trim();
        return sport.getId().toString().equals(normalized)
                || sport.getCode().equalsIgnoreCase(normalized)
                || sport.getName().equalsIgnoreCase(normalized);
    }
    private List<RankingRow> rows(List<PlayerStats> values, User current) {
        List<PlayerStats> sorted = values.stream().sorted(Comparator.comparingLong(PlayerStats::score).reversed()
                .thenComparing(Comparator.comparingLong(PlayerStats::wins).reversed()).thenComparing(value -> value.user().getName())).toList();
        List<RankingRow> response = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            PlayerStats value = sorted.get(i);
            double accuracy = value.total() == 0 ? 0 : Math.round(value.wins() * 10_000.0 / value.total()) / 100.0;
            response.add(new RankingRow(i + 1, value.user().getId(), value.user().getName(), value.score(), value.wins(),
                    value.total(), accuracy, value.streak(), current != null && value.user().getId().equals(current.getId())));
        }
        return response;
    }
    private void addMember(ArenaPool pool, User user, boolean moderator) {
        ArenaPoolMember member = new ArenaPoolMember(); member.setPool(pool); member.setUser(user); member.setModerator(moderator); members.save(member);
    }
    private boolean canView(ArenaPool pool, User user) {
        return pool.isPublicPool() || (user != null && (pool.getOwner().getId().equals(user.getId())
                || members.findByPoolAndUser(pool, user).isPresent()));
    }
    private String inviteCode() {
        String code;
        do { StringBuilder value = new StringBuilder(); for (int i = 0; i < 8; i++) value.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length()))); code = value.toString(); }
        while (pools.existsByInviteCode(code));
        return code;
    }
    private long currentStreak(List<ArenaPrediction> values) {
        long streak = 0;
        List<ArenaPrediction> settled = values.stream()
                .filter(value -> value.getStatus() == PredictionStatus.WON || value.getStatus() == PredictionStatus.LOST)
                .sorted(Comparator.comparing((ArenaPrediction value) -> value.getResolvedAt() == null ? value.getPlacedAt() : value.getResolvedAt()).reversed())
                .toList();
        for (ArenaPrediction prediction : settled) {
            if (prediction.getStatus() != PredictionStatus.WON) break;
            streak++;
        }
        return streak;
    }
    private record PlayerStats(User user, long score, long wins, long total, long streak) { }
}
