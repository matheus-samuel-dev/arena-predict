package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.ArenaDtos.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.entity.User;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArenaPredictionService {
    private final ArenaPredictionRepository predictions;
    private final ArenaEventRepository events;
    private final PredictionMarketRepository markets;
    private final MarketOptionRepository options;
    private final ArenaPoolRepository pools;
    private final ArenaPoolMemberRepository members;
    private final PointWalletService wallets;
    private final ArenaNotificationService notifications;
    private final ProgressionService progression;

    public ArenaPredictionService(ArenaPredictionRepository predictions, ArenaEventRepository events,
                                  PredictionMarketRepository markets, MarketOptionRepository options,
                                  ArenaPoolRepository pools, ArenaPoolMemberRepository members,
                                  PointWalletService wallets, ArenaNotificationService notifications,
                                  ProgressionService progression) {
        this.predictions = predictions;
        this.events = events;
        this.markets = markets;
        this.options = options;
        this.pools = pools;
        this.members = members;
        this.wallets = wallets;
        this.notifications = notifications;
        this.progression = progression;
    }

    @Transactional
    public PredictionResponse place(PlacePredictionRequest request, String headerKey, User user) {
        String clientKey = firstNonBlank(headerKey, request.idempotencyKey(), UUID.randomUUID().toString());
        String key = "prediction:user:" + user.getId() + ":" + clientKey.trim();
        ArenaPrediction existing = predictions.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            if (!existing.getUser().getId().equals(user.getId())) throw new ArenaProblem.Conflict("Chave de idempotência já utilizada.");
            return response(existing);
        }

        ArenaEvent event = events.findById(request.eventId()).orElseThrow(() -> new ArenaProblem.NotFound("Evento não encontrado."));
        PredictionMarket market = markets.findByIdForUpdate(request.marketId()).orElseThrow(() -> new ArenaProblem.NotFound("Mercado não encontrado."));
        if (!market.getEvent().getId().equals(event.getId())) throw new ArenaProblem.RuleViolation("O mercado não pertence ao evento informado.");
        MarketOption option = options.findByIdAndMarket(request.optionId(), market)
                .orElseThrow(() -> new ArenaProblem.NotFound("Opção de palpite não encontrada."));
        validateOpen(event, market, option, request.stakePoints());

        ArenaPool pool = null;
        if (request.poolId() != null) {
            pool = pools.findById(request.poolId()).orElseThrow(() -> new ArenaProblem.NotFound("Bolão não encontrado."));
            if (members.findByPoolAndUser(pool, user).isEmpty()) throw new ArenaProblem.RuleViolation("Entre no bolão antes de registrar este palpite.");
            validatePool(pool, event);
        }

        ArenaPrediction prediction = new ArenaPrediction();
        prediction.setUser(user);
        prediction.setEvent(event);
        prediction.setMarket(market);
        prediction.setOption(option);
        prediction.setPool(pool);
        prediction.setStakePoints(request.stakePoints());
        prediction.setMultiplier(option.getMultiplier());
        prediction.setPotentialPoints(option.getMultiplier().multiply(java.math.BigDecimal.valueOf(request.stakePoints()))
                .setScale(0, RoundingMode.DOWN).intValueExact());
        prediction.setStatus(PredictionStatus.ACTIVE);
        prediction.setIdempotencyKey(key);
        prediction = predictions.saveAndFlush(prediction);

        wallets.apply(user, -request.stakePoints(), PointTransactionType.PREDICTION_PLACED,
                "ledger:" + key, "PREDICTION", prediction.getId().toString(),
                "Pontos utilizados no palpite: " + event.getTitle());
        progression.achievements(user);
        progression.challenges(user);
        return response(prediction);
    }

    @Transactional(readOnly = true)
    public List<PredictionResponse> list(User user) {
        return predictions.findByUserOrderByPlacedAtDesc(user).stream().map(this::response).toList();
    }

    @Transactional
    public PredictionResponse cancel(Long id, User user) {
        ArenaPrediction prediction = predictions.findByIdAndUser(id, user)
                .orElseThrow(() -> new ArenaProblem.NotFound("Palpite não encontrado."));
        if (prediction.getStatus() == PredictionStatus.CANCELLED || prediction.getStatus() == PredictionStatus.REFUNDED) return response(prediction);
        if (prediction.getStatus() != PredictionStatus.ACTIVE) throw new ArenaProblem.Conflict("Somente palpites ativos podem ser cancelados.");
        if (!Instant.now().isBefore(prediction.getEvent().getPredictionClosesAt()))
            throw new ArenaProblem.RuleViolation("O prazo para cancelamento deste palpite foi encerrado.");
        prediction.setStatus(PredictionStatus.CANCELLED);
        prediction.setResolvedAt(Instant.now());
        wallets.apply(user, prediction.getStakePoints(), PointTransactionType.REFUND,
                "prediction-cancel:" + prediction.getId(), "PREDICTION", prediction.getId().toString(),
                "Pontos devolvidos por cancelamento de palpite");
        return response(prediction);
    }

    @Transactional
    public SettlementResponse settleMarket(Long marketId, String correctOptionKey) {
        PredictionMarket market = markets.findByIdForUpdate(marketId).orElseThrow(() -> new ArenaProblem.NotFound("Mercado não encontrado."));
        if (market.getStatus() == MarketStatus.SETTLED) {
            return new SettlementResponse(marketId, market.getResultOptionKey(), 0, 0, 0, true);
        }
        if (market.getEvent().getStatus() != EventStatus.FINISHED)
            throw new ArenaProblem.RuleViolation("Finalize o evento antes de liquidar seus mercados.");
        if (market.getStatus() != MarketStatus.CLOSED)
            throw new ArenaProblem.RuleViolation("Feche o mercado antes de registrar o resultado correto.");
        MarketOption correct = options.findByMarketAndKey(market, correctOptionKey.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ArenaProblem.NotFound("Opção correta não encontrada neste mercado."));
        List<ArenaPrediction> active = predictions.findByMarketAndStatus(market, PredictionStatus.ACTIVE);
        int winners = 0;
        int losers = 0;
        long rewards = 0;
        Set<User> affectedUsers = new HashSet<>();
        for (ArenaPrediction prediction : active) {
            affectedUsers.add(prediction.getUser());
            prediction.setResolvedAt(Instant.now());
            if (prediction.getOption().getId().equals(correct.getId())) {
                prediction.setStatus(PredictionStatus.WON);
                prediction.setRewardedPoints(prediction.getPotentialPoints());
                wallets.apply(prediction.getUser(), prediction.getPotentialPoints(), PointTransactionType.PREDICTION_WON,
                        "prediction-win:" + prediction.getId(), "PREDICTION", prediction.getId().toString(),
                        "Recompensa pelo palpite vencedor: " + prediction.getEvent().getTitle());
                notifications.create(prediction.getUser(), NotificationType.PREDICTION_WON, "Palpite vencedor",
                        "Você recebeu " + prediction.getPotentialPoints() + " pontos virtuais.", "/predictions");
                winners++;
                rewards += prediction.getPotentialPoints();
            } else {
                prediction.setStatus(PredictionStatus.LOST);
                losers++;
            }
        }
        market.setResultOptionKey(correct.getKey());
        market.setStatus(MarketStatus.SETTLED);
        market.setSettledAt(Instant.now());
        affectedUsers.forEach(user -> {
            progression.achievements(user);
            progression.challenges(user);
        });
        return new SettlementResponse(marketId, correct.getKey(), winners, losers, rewards, false);
    }

    @Transactional
    public int cancelEvent(Long eventId) {
        ArenaEvent event = events.findById(eventId).orElseThrow(() -> new ArenaProblem.NotFound("Evento não encontrado."));
        if (event.getStatus() == EventStatus.CANCELLED) return 0;
        int refunds = 0;
        for (ArenaPrediction prediction : predictions.findByEventAndStatus(event, PredictionStatus.ACTIVE)) {
            prediction.setStatus(PredictionStatus.REFUNDED);
            prediction.setResolvedAt(Instant.now());
            wallets.apply(prediction.getUser(), prediction.getStakePoints(), PointTransactionType.REFUND,
                    "event-refund:prediction:" + prediction.getId(), "EVENT", event.getId().toString(),
                    "Reembolso por evento cancelado: " + event.getTitle());
            notifications.create(prediction.getUser(), NotificationType.REFUND, "Palpite reembolsado",
                    "O evento foi cancelado e " + prediction.getStakePoints() + " pontos retornaram à sua carteira.",
                    "/events/" + event.getId());
            refunds++;
        }
        markets.findByEventOrderByIdAsc(event).stream().filter(m -> m.getStatus() != MarketStatus.SETTLED).forEach(m -> m.setStatus(MarketStatus.CANCELLED));
        event.setStatus(EventStatus.CANCELLED);
        return refunds;
    }

    public PredictionResponse response(ArenaPrediction value) {
        return new PredictionResponse(value.getId(), value.getEvent().getId(), value.getEvent().getTitle(),
                value.getMarket().getId(), value.getMarket().getName(), value.getOption().getId(), value.getOption().getLabel(),
                value.getStakePoints(), value.getMultiplier(), value.getPotentialPoints(), value.getRewardedPoints(),
                value.getStatus(), value.getPool() == null ? null : value.getPool().getId(), value.getPlacedAt(), value.getResolvedAt(),
                value.getStatus() == PredictionStatus.ACTIVE && Instant.now().isBefore(value.getEvent().getPredictionClosesAt()));
    }

    private void validateOpen(ArenaEvent event, PredictionMarket market, MarketOption option, int stake) {
        if (event.getStatus() != EventStatus.OPEN_FOR_PREDICTIONS)
            throw new ArenaProblem.RuleViolation("Este evento não está aberto para palpites.");
        if (!Instant.now().isBefore(event.getPredictionClosesAt()))
            throw new ArenaProblem.RuleViolation("O prazo para palpites neste evento foi encerrado.");
        if (market.getStatus() != MarketStatus.OPEN) throw new ArenaProblem.RuleViolation("Este mercado não está disponível.");
        if (!option.isActive()) throw new ArenaProblem.RuleViolation("Esta opção está suspensa.");
        if (stake < market.getMinimumPoints())
            throw new ArenaProblem.RuleViolation("O mínimo para este mercado é " + market.getMinimumPoints() + " pontos.");
    }
    private void validatePool(ArenaPool pool, ArenaEvent event) {
        if (pool.getStatus() != PoolStatus.OPEN && pool.getStatus() != PoolStatus.IN_PROGRESS)
            throw new ArenaProblem.RuleViolation("Este bolão não aceita novos palpites.");
        Championship eventChampionship = event.getChampionship();
        if (pool.getSport() != null && !pool.getSport().getId().equals(eventChampionship.getSport().getId()))
            throw new ArenaProblem.RuleViolation("Este evento não pertence à modalidade do bolão.");
        if (pool.getChampionship() != null && !pool.getChampionship().getId().equals(eventChampionship.getId()))
            throw new ArenaProblem.RuleViolation("Este evento não pertence ao campeonato do bolão.");
        if (pool.getStartsAt() != null && event.getStartsAt().isBefore(pool.getStartsAt()))
            throw new ArenaProblem.RuleViolation("O evento acontece antes do início do bolão.");
        if (pool.getEndsAt() != null && event.getStartsAt().isAfter(pool.getEndsAt()))
            throw new ArenaProblem.RuleViolation("O evento acontece após o encerramento do bolão.");
    }
    private String firstNonBlank(String... values) {
        return Arrays.stream(values).filter(Objects::nonNull).filter(value -> !value.isBlank()).findFirst().orElseThrow();
    }
}
