package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.ArenaDtos.*;

import com.bolao.copa.arena.api.ExperienceDtos;
import com.bolao.copa.arena.domain.ArenaPrediction;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArenaDashboardService {
    private final PointWalletService walletService;
    private final ArenaPredictionRepository predictions;
    private final ArenaCatalogService catalog;
    private final ArenaPoolRankingService pools;
    private final ArenaNotificationService notifications;
    private final ArenaEventRepository eventRepository;
    private final PredictionMarketRepository marketRepository;
    private final ArenaPoolRepository poolRepository;
    private final PointLedgerRepository ledger;
    private final UserRepository users;
    private final ProgressionService progression;
    private final boolean demoMode;

    public ArenaDashboardService(PointWalletService walletService, ArenaPredictionRepository predictions,
                                 ArenaCatalogService catalog, ArenaPoolRankingService pools,
                                 ArenaNotificationService notifications, ArenaEventRepository eventRepository,
                                 PredictionMarketRepository marketRepository, ArenaPoolRepository poolRepository,
                                 PointLedgerRepository ledger, UserRepository users, ProgressionService progression,
                                 @Value("${app.demo.enabled:true}") boolean demoMode) {
        this.walletService = walletService; this.predictions = predictions; this.catalog = catalog; this.pools = pools;
        this.notifications = notifications; this.eventRepository = eventRepository; this.marketRepository = marketRepository;
        this.poolRepository = poolRepository; this.ledger = ledger; this.users = users; this.progression = progression; this.demoMode = demoMode;
    }

    @Transactional
    public DashboardResponse dashboard(User user) {
        List<ExperienceDtos.ChallengeResponse> challenges = progression.challenges(user);
        List<ExperienceDtos.AchievementResponse> recentAchievements = progression.achievements(user).stream()
                .filter(ExperienceDtos.AchievementResponse::unlocked).limit(3).toList();
        WalletResponse wallet = walletService.wallet(user);
        PlayerProgress playerProgress = playerProgress(wallet.lifetimeEarned());
        List<ArenaPrediction> mine = predictions.findByUserOrderByPlacedAtDesc(user);
        long active = mine.stream().filter(p -> p.getStatus() == PredictionStatus.ACTIVE).count();
        long finished = mine.stream().filter(p -> p.getStatus() == PredictionStatus.WON || p.getStatus() == PredictionStatus.LOST).count();
        long won = mine.stream().filter(p -> p.getStatus() == PredictionStatus.WON).count();
        double accuracy = finished == 0 ? 0 : Math.round(won * 10_000.0 / finished) / 100.0;
        int streak = 0;
        for (ArenaPrediction p : mine) { if (p.getStatus() == PredictionStatus.WON) streak++; else if (p.getStatus() == PredictionStatus.LOST) break; }
        List<RankingRow> ranking = pools.ranking(user, RankingPeriod.WEEKLY, RankingScope.GLOBAL, null);
        Integer position = ranking.stream().filter(RankingRow::currentUser).map(RankingRow::position).findFirst().orElse(null);
        List<EventResponse> allEvents = catalog.listEvents(null, null, null);
        List<EventResponse> featured = allEvents.stream().filter(EventResponse::featured).limit(6).toList();
        List<EventResponse> live = allEvents.stream().filter(e -> e.status() == EventStatus.LIVE).toList();
        List<EventResponse> upcoming = allEvents.stream().filter(e -> e.status() == EventStatus.OPEN_FOR_PREDICTIONS || e.status() == EventStatus.SCHEDULED).limit(8).toList();
        return new DashboardResponse(user.getName(), playerProgress.level(), playerProgress.title(),
                playerProgress.xp(), playerProgress.nextLevelXp(), wallet.balance(), position, active, finished, won,
                accuracy, streak, notifications.unread(user), featured, live, upcoming,
                mine.stream().limit(6).map(this::predictionResponse).toList(), pools.list(user).stream().filter(p -> p.status() == PoolStatus.OPEN || p.status() == PoolStatus.IN_PROGRESS).limit(4).toList(),
                ranking.stream().limit(8).toList(), VIRTUAL_POINTS_NOTICE, demoMode, challenges, recentAchievements,
                performance(mine));
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse adminDashboard() {
        long pointsMoved = ledger.findAll().stream().mapToLong(entry -> Math.abs(entry.getAmount())).sum();
        return new AdminDashboardResponse(users.count(), eventRepository.countByStatus(EventStatus.LIVE),
                eventRepository.countByStatus(EventStatus.SCHEDULED) + eventRepository.countByStatus(EventStatus.OPEN_FOR_PREDICTIONS),
                predictions.countByStatus(PredictionStatus.ACTIVE), marketRepository.countByStatus(MarketStatus.OPEN),
                poolRepository.findAll().stream().filter(pool -> pool.getStatus() == PoolStatus.OPEN || pool.getStatus() == PoolStatus.IN_PROGRESS).count(),
                pointsMoved, eventRepository.countAwaitingMarketSettlement(EventStatus.FINISHED, MarketStatus.SETTLED), demoMode);
    }
    private PredictionResponse predictionResponse(ArenaPrediction value) {
        return new PredictionResponse(value.getId(), value.getEvent().getId(), value.getEvent().getTitle(), value.getMarket().getId(),
                value.getMarket().getName(), value.getOption().getId(), value.getOption().getLabel(), value.getStakePoints(), value.getMultiplier(),
                value.getPotentialPoints(), value.getRewardedPoints(), value.getStatus(), value.getPool() == null ? null : value.getPool().getId(),
                value.getPlacedAt(), value.getResolvedAt(),
                value.getStatus() == PredictionStatus.ACTIVE && Instant.now().isBefore(value.getEvent().getPredictionClosesAt()));
    }
    private PlayerProgress playerProgress(long lifetimeEarned) {
        long xp = Math.max(0, lifetimeEarned);
        int level = Math.toIntExact(Math.min(Integer.MAX_VALUE, xp / 5_000 + 1));
        long nextLevelXp = Math.multiplyExact((long) level, 5_000L);
        String title = xp >= 20_000 ? "Elite" : xp >= 10_000 ? "Analista" : xp >= 5_000 ? "Competidor" : "Novato";
        return new PlayerProgress(level, title, xp, nextLevelXp);
    }
    private List<PerformancePoint> performance(List<ArenaPrediction> predictions) {
        List<ArenaPrediction> settled = predictions.stream()
                .filter(value -> value.getStatus() == PredictionStatus.WON || value.getStatus() == PredictionStatus.LOST)
                .sorted(Comparator.comparing(ArenaPrediction::getPlacedAt))
                .toList();
        int from = Math.max(0, settled.size() - 12);
        long wins = settled.subList(0, from).stream().filter(value -> value.getStatus() == PredictionStatus.WON).count();
        long total = from;
        List<PerformancePoint> points = new ArrayList<>();
        for (int i = from; i < settled.size(); i++) {
            ArenaPrediction prediction = settled.get(i);
            total++;
            if (prediction.getStatus() == PredictionStatus.WON) wins++;
            double accuracy = Math.round(wins * 10_000.0 / total) / 100.0;
            points.add(new PerformancePoint("P" + (i + 1), prediction.getResolvedAt() == null ? prediction.getPlacedAt() : prediction.getResolvedAt(), accuracy));
        }
        return points;
    }
    private record PlayerProgress(int level, String title, long xp, long nextLevelXp) { }
}
