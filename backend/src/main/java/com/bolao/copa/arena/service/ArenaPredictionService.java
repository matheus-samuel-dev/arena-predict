package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.ArenaDtos.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.entity.User;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final AdminAuditService audit;
    private final MarketAvailabilityService availability;
    private final MarketDefinitionCatalog definitions;
    private final MarketSettlementEngine settlement;
    private final EventParticipantRepository participants;
    private final DemoProbabilityEngine pricing;

    public ArenaPredictionService(ArenaPredictionRepository predictions, ArenaEventRepository events,
                                  PredictionMarketRepository markets, MarketOptionRepository options,
                                  ArenaPoolRepository pools, ArenaPoolMemberRepository members,
                                  PointWalletService wallets, ArenaNotificationService notifications,
                                  ProgressionService progression, AdminAuditService audit,
                                  MarketAvailabilityService availability, MarketDefinitionCatalog definitions,
                                  MarketSettlementEngine settlement, EventParticipantRepository participants, DemoProbabilityEngine pricing) {
        this.pricing=pricing;
        this.predictions = predictions;
        this.events = events;
        this.markets = markets;
        this.options = options;
        this.pools = pools;
        this.members = members;
        this.wallets = wallets;
        this.notifications = notifications;
        this.progression = progression;
        this.audit = audit;
        this.availability=availability; this.definitions=definitions; this.settlement=settlement; this.participants=participants;
    }

    @Transactional
    public PredictionResponse place(PlacePredictionRequest request, String headerKey, User user) {
        String clientKey = clientIdempotencyKey(headerKey, request.idempotencyKey());
        String key = "prediction:user:" + user.getId() + ":" + clientKey;
        ArenaPrediction existing = predictions.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            return idempotentResponse(existing, request, user);
        }

        ArenaEvent event = events.findByIdForUpdate(request.eventId()).orElseThrow(() -> new ArenaProblem.NotFound("Evento não encontrado."));
        PredictionMarket market = markets.findByIdForUpdate(request.marketId()).orElseThrow(() -> new ArenaProblem.NotFound("Mercado não encontrado."));
        // A concurrent request for the same market releases this lock only after
        // committing its prediction, so re-read the idempotency record here.
        existing = predictions.findByIdempotencyKey(key).orElse(null);
        if (existing != null) return idempotentResponse(existing, request, user);
        if (!market.getEvent().getId().equals(event.getId())) throw new ArenaProblem.RuleViolation("O mercado não pertence ao evento informado.");
        MarketOption option = options.findByIdAndMarket(request.optionId(), market)
                .orElseThrow(() -> new ArenaProblem.NotFound("Opção de palpite não encontrada."));
        validateOpen(event, market, option, request.stakePoints());
        var confirmedMultiplier=pricing.quote(market,List.of(option)).multipliers().get(option.getKey());
        if(request.expectedMultiplier()!=null && request.expectedMultiplier().compareTo(confirmedMultiplier)!=0)
            throw new ArenaProblem.Conflict("O multiplicador foi atualizado. Atualize o evento e confira o novo valor antes de confirmar.");

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
        prediction.setMultiplier(confirmedMultiplier);
        prediction.setPotentialPoints(confirmedMultiplier.multiply(java.math.BigDecimal.valueOf(request.stakePoints()))
                .setScale(0, RoundingMode.DOWN).intValueExact());
        prediction.setStatus(PredictionStatus.ACTIVE);
        prediction.setIdempotencyKey(key);
        try {
            prediction = predictions.saveAndFlush(prediction);
        } catch (DataIntegrityViolationException duplicateKey) {
            throw new ArenaProblem.Conflict("A chave de idempotência já foi utilizada por outra requisição.");
        }

        wallets.apply(user, -request.stakePoints(), PointTransactionType.PREDICTION_PLACED,
                "ledger:" + key, "PREDICTION", prediction.getId().toString(),
                "Pontos utilizados no palpite: " + event.getTitle());
        progression.refresh(user);
        return response(prediction);
    }

    @Transactional(readOnly = true)
    public List<PredictionResponse> list(User user) {
        return predictions.findByUserOrderByPlacedAtDesc(user).stream().map(this::response).toList();
    }

    @Transactional
    public PredictionResponse cancel(Long id, User user) {
        var located = predictions.commandContext(id, user)
                .orElseThrow(() -> new ArenaProblem.NotFound("Palpite não encontrado."));
        events.findByIdForUpdate(located.getEventId()).orElseThrow();
        markets.findByIdForUpdate(located.getMarketId()).orElseThrow();
        // Serializing cancellation on the prediction row makes retries truly
        // idempotent: exactly one request changes the state and credits the
        // refund, while followers observe the committed terminal state.
        ArenaPrediction prediction = predictions.findByIdAndUserForUpdate(id, user)
                .orElseThrow(() -> new ArenaProblem.NotFound("Palpite não encontrado."));
        if (prediction.getStatus() == PredictionStatus.CANCELLED || prediction.getStatus() == PredictionStatus.REFUNDED) return response(prediction);
        if (prediction.getStatus() != PredictionStatus.ACTIVE) throw new ArenaProblem.Conflict("Somente palpites ativos podem ser cancelados.");
        if (!canCancel(prediction))
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
        lockMarketEvent(marketId);
        PredictionMarket market = markets.findByIdForUpdate(marketId).orElseThrow(() -> new ArenaProblem.NotFound("Mercado não encontrado."));
        if (market.getStatus() == MarketStatus.SETTLED) {
            return new SettlementResponse(marketId, market.getResultOptionKey(), 0, 0, 0, true);
        }
        if (market.getEvent().getStatus() != EventStatus.FINISHED)
            throw new ArenaProblem.RuleViolation("Finalize o evento antes de liquidar seus mercados.");
        if (market.getTemplateCode()!=null) throw new ArenaProblem.RuleViolation("Este mercado usa liquidação por resultado. Registre os dados da modalidade em Resultados.");
        if (market.getStatus() != MarketStatus.CLOSED)
            throw new ArenaProblem.RuleViolation("Feche o mercado antes de registrar o resultado correto.");
        MarketOption correct = options.findByMarketAndKey(market, correctOptionKey.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ArenaProblem.NotFound("Opção correta não encontrada neste mercado."));
        return applyOutcome(market, MarketSettlementEngine.Outcome.winner(correct.getKey()));
    }

    @Transactional
    public void settleDerived(ArenaEvent event) {
        events.findByIdForUpdate(event.getId()).orElseThrow();
        if (event.getStatus()!=EventStatus.FINISHED) throw new ArenaProblem.RuleViolation("Finalize o evento antes de liquidar.");
        List<EventParticipant> entries=participants.findByEventOrderByDisplayOrderAsc(event);
        settlement.validateEvent(event,entries,true);
        // Lock every affected wallet in one global order before processing multiple markets.
        // Per-market ordering alone can deadlock when two events share participants.
        orderedByUser(predictions.findByEventAndStatus(event, PredictionStatus.ACTIVE)).stream()
                .map(ArenaPrediction::getUser).distinct().forEach(wallets::lockParticipant);
        for (PredictionMarket market: markets.findByEventForUpdate(event)) {
            if (market.getTemplateCode()==null || market.getStatus()==MarketStatus.SETTLED || market.getStatus()==MarketStatus.CANCELLED) continue;
            var definition=definitions.definition(market,entries).orElseThrow(() -> new ArenaProblem.RuleViolation("Definição de mercado não encontrada."));
            applyOutcome(market,settlement.evaluate(definition,event,entries));
        }
    }

    private SettlementResponse applyOutcome(PredictionMarket market, MarketSettlementEngine.Outcome outcome) {
        List<ArenaPrediction> active = orderedByUser(predictions.findByMarketAndStatus(market, PredictionStatus.ACTIVE));
        int winners = 0;
        int losers = 0;
        long rewards = 0;
        Set<User> affectedUsers = new LinkedHashSet<>();
        for (ArenaPrediction prediction : active) {
            affectedUsers.add(prediction.getUser());
            prediction.setResolvedAt(Instant.now());
            if (outcome.refund()) {
                prediction.setStatus(PredictionStatus.REFUNDED);
                wallets.apply(prediction.getUser(), prediction.getStakePoints(), PointTransactionType.REFUND,
                        "settlement-refund:"+prediction.getId(), "PREDICTION", prediction.getId().toString(), "Pontos devolvidos por igualdade na linha ou empate anulado");
            } else if (outcome.winningKeys().contains(prediction.getOption().getKey())) {
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
        String resultKey=outcome.refund()? "REFUND" : String.join(",",new TreeSet<>(outcome.winningKeys()));
        market.setResultOptionKey(resultKey);
        market.setStatus(MarketStatus.SETTLED);
        market.setSettledAt(Instant.now());
        affectedUsers.forEach(progression::refresh);
        audit.record("MARKET_SETTLED", "MARKET", market.getId(),
                "Mercado " + market.getName() + " liquidado: "
                        + quantity(winners, "vencedor", "vencedores") + " e "
                        + quantity(rewards, "ponto virtual creditado", "pontos virtuais creditados"));
        return new SettlementResponse(market.getId(), resultKey, winners, losers, rewards, false);
    }

    @Transactional
    public int cancelEvent(Long eventId) {
        ArenaEvent event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ArenaProblem.NotFound("Evento não encontrado."));
        if (event.getStatus() == EventStatus.CANCELLED) return 0;
        if (event.getStatus() == EventStatus.FINISHED)
            throw new ArenaProblem.Conflict("Eventos finalizados não podem ser cancelados.");
        List<PredictionMarket> eventMarkets = markets.findByEventForUpdate(event);
        if (eventMarkets.stream().anyMatch(market -> market.getStatus() == MarketStatus.SETTLED))
            throw new ArenaProblem.Conflict("O evento possui mercado liquidado e não pode mais ser cancelado.");
        int refunds = 0;
        for (ArenaPrediction prediction : orderedByUser(
                predictions.findByEventAndStatus(event, PredictionStatus.ACTIVE))) {
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
        eventMarkets.forEach(market -> market.setStatus(MarketStatus.CANCELLED));
        event.setStatus(EventStatus.CANCELLED);
        audit.record("EVENT_CANCELLED", "EVENT", event.getId(),
                refundAuditSummary("evento", event.getTitle(), refunds));
        return refunds;
    }

    @Transactional
    public int cancelMarket(Long marketId) {
        lockMarketEvent(marketId);
        PredictionMarket market = markets.findByIdForUpdate(marketId)
                .orElseThrow(() -> new ArenaProblem.NotFound("Mercado não encontrado."));
        if (market.getStatus() == MarketStatus.CANCELLED) return 0;
        if (market.getStatus() == MarketStatus.SETTLED)
            throw new ArenaProblem.Conflict("Mercados liquidados não podem ser cancelados.");

        int refunds = 0;
        for (ArenaPrediction prediction : orderedByUser(
                predictions.findByMarketAndStatus(market, PredictionStatus.ACTIVE))) {
            prediction.setStatus(PredictionStatus.REFUNDED);
            prediction.setResolvedAt(Instant.now());
            wallets.apply(prediction.getUser(), prediction.getStakePoints(), PointTransactionType.REFUND,
                    "market-refund:prediction:" + prediction.getId(), "MARKET", market.getId().toString(),
                    "Reembolso por mercado cancelado: " + market.getName());
            notifications.create(prediction.getUser(), NotificationType.REFUND, "Palpite reembolsado",
                    "O mercado foi cancelado e " + prediction.getStakePoints() + " pontos retornaram à sua carteira.",
                    "/events/" + market.getEvent().getId());
            refunds++;
        }
        market.setStatus(MarketStatus.CANCELLED);
        audit.record("MARKET_CANCELLED", "MARKET", market.getId(),
                refundAuditSummary("mercado", market.getName(), refunds));
        return refunds;
    }

    public PredictionResponse response(ArenaPrediction value) {
        return new PredictionResponse(value.getId(), value.getEvent().getId(), value.getEvent().getTitle(),
                value.getMarket().getId(), value.getMarket().getName(), value.getOption().getId(), value.getOption().getLabel(),
                value.getStakePoints(), value.getMultiplier(), value.getPotentialPoints(), value.getRewardedPoints(),
                value.getStatus(), value.getPool() == null ? null : value.getPool().getId(), value.getPlacedAt(), value.getResolvedAt(),
                canCancel(value));
    }

    private boolean canCancel(ArenaPrediction prediction) {
        return prediction.getStatus() == PredictionStatus.ACTIVE
                && prediction.getEvent().getStatus() != EventStatus.LIVE
                && Instant.now().isBefore(prediction.getEvent().getStartsAt())
                && availability.evaluate(prediction.getMarket()).allowed();
    }

    private void validateOpen(ArenaEvent event, PredictionMarket market, MarketOption option, int stake) {
        MarketAvailability decision=availability.evaluate(market);
        if (!decision.allowed()) throw new ArenaProblem.RuleViolation(decision.label()+". "+decision.reason());
        if (!option.isActive()) throw new ArenaProblem.RuleViolation("Esta opção está suspensa.");
        if (stake < market.getMinimumPoints())
            throw new ArenaProblem.RuleViolation("O mínimo para este mercado é " + market.getMinimumPoints() + " pontos.");
        if (stake > MAX_PREDICTION_STAKE_POINTS)
            throw new ArenaProblem.RuleViolation("O máximo por palpite é " + MAX_PREDICTION_STAKE_POINTS + " pontos.");
    }
    private void lockMarketEvent(Long id) {
        Long eventId=markets.eventIdForMarket(id).orElseThrow(() -> new ArenaProblem.NotFound("Mercado não encontrado."));
        events.findByIdForUpdate(eventId).orElseThrow();
    }
    private List<ArenaPrediction> orderedByUser(List<ArenaPrediction> values) {
        return values.stream().sorted(Comparator
                .comparing((ArenaPrediction value) -> value.getUser().getId())
                .thenComparing(ArenaPrediction::getId)).toList();
    }
    private String refundAuditSummary(String resource, String name, int refunds) {
        String predictionLabel = refunds == 1 ? "palpite reembolsado" : "palpites reembolsados";
        return "Cancelamento do " + resource + " “" + name + "”: " + refunds + " " + predictionLabel + ".";
    }
    private String quantity(long value, String singular, String plural) {
        return value + " " + (value == 1 ? singular : plural);
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
    private PredictionResponse idempotentResponse(ArenaPrediction existing, PlacePredictionRequest request, User user) {
        if (!existing.getUser().getId().equals(user.getId()) || !samePayload(existing, request))
            throw new ArenaProblem.Conflict("A chave de idempotência já foi utilizada com dados diferentes.");
        return response(existing);
    }

    private boolean samePayload(ArenaPrediction existing, PlacePredictionRequest request) {
        Long existingPoolId = existing.getPool() == null ? null : existing.getPool().getId();
        return existing.getEvent().getId().equals(request.eventId())
                && existing.getMarket().getId().equals(request.marketId())
                && existing.getOption().getId().equals(request.optionId())
                && existing.getStakePoints() == request.stakePoints()
                && Objects.equals(existingPoolId, request.poolId());
    }

    private String clientIdempotencyKey(String headerKey, String bodyKey) {
        String value = firstNonBlank(headerKey, bodyKey, UUID.randomUUID().toString()).trim();
        if (value.length() > MAX_CLIENT_IDEMPOTENCY_KEY_LENGTH)
            throw new ArenaProblem.RuleViolation("A chave de idempotência deve ter no máximo "
                    + MAX_CLIENT_IDEMPOTENCY_KEY_LENGTH + " caracteres.");
        return value;
    }

    private String firstNonBlank(String... values) {
        return Arrays.stream(values).filter(Objects::nonNull).filter(value -> !value.isBlank())
                .findFirst().orElseThrow();
    }
}
