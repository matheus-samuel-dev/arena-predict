package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.ArenaDtos.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArenaCatalogService {
    private final SportRepository sports;
    private final ChampionshipRepository championships;
    private final CompetitorRepository competitors;
    private final ArenaEventRepository events;
    private final PredictionMarketRepository markets;
    private final MarketOptionRepository options;
    private final EventParticipantRepository eventParticipants;
    private final ArenaPredictionRepository predictions;
    private final AdminAuditService audit;

    public ArenaCatalogService(SportRepository sports, ChampionshipRepository championships,
                               CompetitorRepository competitors, ArenaEventRepository events,
                               PredictionMarketRepository markets, MarketOptionRepository options,
                               EventParticipantRepository eventParticipants,
                               ArenaPredictionRepository predictions, AdminAuditService audit) {
        this.sports = sports;
        this.championships = championships;
        this.competitors = competitors;
        this.events = events;
        this.markets = markets;
        this.options = options;
        this.eventParticipants = eventParticipants;
        this.predictions = predictions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<SportResponse> listSports(boolean includeInactive) {
        return (includeInactive ? sports.findAll() : sports.findByActiveTrueOrderByDisplayOrderAscNameAsc())
                .stream().sorted(Comparator.comparingInt(Sport::getDisplayOrder).thenComparing(Sport::getName))
                .map(this::sportResponse).toList();
    }

    @Transactional
    public SportResponse saveSport(Long id, SportRequest request) {
        Sport sport = id == null ? new Sport() : sport(id);
        String code = normalizeCode(request.code());
        if (code.isBlank())
            throw new ArenaProblem.RuleViolation("O código da modalidade precisa conter letras ou números.");
        sports.findByCodeIgnoreCase(code).filter(existing -> !Objects.equals(existing.getId(), id))
                .ifPresent(existing -> { throw new ArenaProblem.Conflict("Já existe uma modalidade com este código."); });
        sport.setCode(code);
        sport.setName(request.name().trim());
        sport.setCategory(request.category());
        sport.setIcon(optionalText(request.icon()));
        if (request.active() != null) sport.setActive(request.active());
        if (request.displayOrder() != null) sport.setDisplayOrder(request.displayOrder());
        sport = sports.save(sport);
        audit.record(id == null ? "SPORT_CREATED" : "SPORT_UPDATED", "SPORT", sport.getId(),
                "Modalidade " + sport.getName() + " salva");
        return sportResponse(sport);
    }

    @Transactional(readOnly = true)
    public List<ChampionshipResponse> listChampionships(String sportCode) {
        List<Championship> result = sportCode == null || sportCode.isBlank()
                ? championships.findAllByOrderByNameAsc()
                : championships.findBySportOrderByNameAsc(sportByCode(sportCode));
        return result.stream().map(this::championshipResponse).toList();
    }

    @Transactional
    public ChampionshipResponse saveChampionship(Long id, ChampionshipRequest request) {
        Championship championship = id == null ? new Championship() : championship(id);
        Sport selectedSport = sport(request.sportId());
        String normalizedSlug = slug(request.slug());
        String season = request.season().trim();
        if (normalizedSlug.isBlank())
            throw new ArenaProblem.RuleViolation("O identificador do campeonato precisa conter letras ou números.");
        if (request.startsAt() != null && request.endsAt() != null && !request.endsAt().isAfter(request.startsAt()))
            throw new ArenaProblem.RuleViolation("O fim do campeonato deve ser posterior ao início.");
        championships.findBySportAndSlugAndSeason(selectedSport, normalizedSlug, season)
                .filter(existing -> !Objects.equals(existing.getId(), id))
                .ifPresent(existing -> { throw new ArenaProblem.Conflict("Já existe este campeonato na mesma modalidade e temporada."); });
        if (id != null && !championship.getSport().getId().equals(selectedSport.getId())
                && events.existsByChampionship(championship))
            throw new ArenaProblem.Conflict("A modalidade do campeonato não pode mudar enquanto houver eventos vinculados.");
        championship.setSport(selectedSport);
        championship.setName(request.name().trim());
        championship.setSlug(normalizedSlug);
        championship.setSeason(season);
        championship.setStatus(request.status() == null ? ChampionshipStatus.ACTIVE : request.status());
        championship.setImageUrl(optionalText(request.imageUrl()));
        championship.setStartsAt(request.startsAt());
        championship.setEndsAt(request.endsAt());
        championship = championships.save(championship);
        audit.record(id == null ? "CHAMPIONSHIP_CREATED" : "CHAMPIONSHIP_UPDATED", "CHAMPIONSHIP",
                championship.getId(), "Campeonato " + championship.getName() + " salvo");
        return championshipResponse(championship);
    }

    @Transactional(readOnly = true)
    public List<CompetitorResponse> listCompetitors(Long sportId) {
        List<Competitor> values = sportId == null ? competitors.findAll() : competitors.findBySportOrderByNameAsc(sport(sportId));
        return values.stream().map(this::competitorResponse).toList();
    }

    @Transactional
    public CompetitorResponse saveCompetitor(Long id, CompetitorRequest request) {
        Competitor competitor = id == null ? new Competitor() : competitor(id);
        Sport selectedSport = sport(request.sportId());
        String code = normalizeCode(request.code());
        if (code.isBlank())
            throw new ArenaProblem.RuleViolation("O código da equipe ou participante precisa conter letras ou números.");
        competitors.findBySportAndCodeIgnoreCase(selectedSport, code)
                .filter(existing -> !Objects.equals(existing.getId(), id))
                .ifPresent(existing -> { throw new ArenaProblem.Conflict("Já existe uma equipe ou participante com este código na modalidade."); });
        if (id != null && !competitor.getSport().getId().equals(selectedSport.getId())
                && (events.existsByHomeCompetitorOrAwayCompetitor(competitor, competitor)
                    || eventParticipants.existsByCompetitor(competitor)))
            throw new ArenaProblem.Conflict("A modalidade não pode mudar enquanto a equipe ou participante estiver vinculada a eventos.");
        competitor.setSport(selectedSport);
        competitor.setName(request.name().trim());
        competitor.setCode(code);
        competitor.setImageUrl(optionalText(request.imageUrl()));
        competitor.setCountry(optionalText(request.country()));
        if (request.active() != null) competitor.setActive(request.active());
        competitor = competitors.save(competitor);
        audit.record(id == null ? "COMPETITOR_CREATED" : "COMPETITOR_UPDATED", "COMPETITOR",
                competitor.getId(), "Participante " + competitor.getName() + " salvo");
        return competitorResponse(competitor);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents(EventStatus status, String sportCode, Boolean featured) {
        return events.findAll().stream()
                .filter(event -> status == null || event.getStatus() == status)
                .filter(event -> sportCode == null || sportCode.isBlank()
                        || event.getChampionship().getSport().getCode().equalsIgnoreCase(sportCode))
                .filter(event -> featured == null || event.isFeatured() == featured)
                .sorted(Comparator.comparing(ArenaEvent::getStartsAt))
                .map(this::eventResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EventResponse> liveEvents() {
        return events.findByStatusOrderByStartsAtAsc(EventStatus.LIVE).stream().map(this::eventResponse).toList();
    }

    @Transactional(readOnly = true)
    public EventResponse eventResponse(Long id) { return eventResponse(event(id)); }

    @Transactional
    public EventResponse saveEvent(Long id, EventRequest request) {
        ArenaEvent event = id == null ? new ArenaEvent() : eventForUpdate(id);
        Long previousHomeId = event.getHomeCompetitor() == null ? null : event.getHomeCompetitor().getId();
        Long previousAwayId = event.getAwayCompetitor() == null ? null : event.getAwayCompetitor().getId();
        EventFormat previousFormat = event.getFormat();
        String externalKey = request.externalKey().trim();
        events.findByExternalKey(externalKey).filter(existing -> !Objects.equals(existing.getId(), id))
                .ifPresent(existing -> { throw new ArenaProblem.Conflict("Já existe um evento com esta chave externa."); });
        EventStatus requestedStatus = request.status() == null
                ? (id == null ? EventStatus.SCHEDULED : event.getStatus())
                : request.status();
        if (id != null && (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.FINISHED))
            throw new ArenaProblem.Conflict("Eventos em estado terminal não podem ser alterados pelo cadastro genérico.");
        if (requestedStatus == EventStatus.CANCELLED)
            throw new ArenaProblem.RuleViolation("Use a ação de cancelamento para reembolsar os palpites do evento.");
        if (requestedStatus == EventStatus.FINISHED)
            throw new ArenaProblem.RuleViolation("Use a ação de resultado para finalizar o evento.");
        if (id != null) validateEventTransition(event.getStatus(), requestedStatus);
        Championship championship = championship(request.championshipId());
        event.setExternalKey(externalKey);
        event.setChampionship(championship);
        event.setHomeCompetitor(request.homeCompetitorId() == null ? null : competitor(request.homeCompetitorId()));
        event.setAwayCompetitor(request.awayCompetitorId() == null ? null : competitor(request.awayCompetitorId()));
        validateCompetitorSport(event.getHomeCompetitor(), championship);
        validateCompetitorSport(event.getAwayCompetitor(), championship);
        EventFormat format = request.format() == null ? EventFormat.STANDARD : request.format();
        boolean participantIdentityChanged = id != null
                && (!Objects.equals(previousHomeId, request.homeCompetitorId())
                || !Objects.equals(previousAwayId, request.awayCompetitorId())
                || previousFormat != format);
        validateEventCompetitors(format, event.getHomeCompetitor(), event.getAwayCompetitor(), request.participants());
        if (!request.predictionClosesAt().isAfter(Instant.now().minusSeconds(365L * 24 * 3600))) {
            throw new ArenaProblem.RuleViolation("A data limite de palpites é inválida.");
        }
        if (!request.predictionClosesAt().isBefore(request.startsAt())) {
            throw new ArenaProblem.RuleViolation("O prazo de palpites deve ser anterior ao início do evento.");
        }
        if (requestedStatus == EventStatus.OPEN_FOR_PREDICTIONS
                && !Instant.now().isBefore(request.predictionClosesAt()))
            throw new ArenaProblem.RuleViolation("Um evento aberto precisa ter prazo futuro para palpites.");
        event.setTitle(request.title().trim());
        event.setStage(optionalText(request.stage()));
        event.setVenue(optionalText(request.venue()));
        event.setBroadcast(optionalText(request.broadcast()));
        event.setImageUrl(optionalText(request.imageUrl()));
        event.setStartsAt(request.startsAt());
        event.setPredictionClosesAt(request.predictionClosesAt());
        event.setStatus(requestedStatus);
        event.setFormat(format);
        int bestOf = request.bestOf() == null ? 1 : request.bestOf();
        if (bestOf != 1 && bestOf != 3 && bestOf != 5)
            throw new ArenaProblem.RuleViolation("A série deve usar melhor de 1, 3 ou 5.");
        event.setBestOf(bestOf);
        event.setFeatured(Boolean.TRUE.equals(request.featured()));
        event.setDemo(Boolean.TRUE.equals(request.demo()));
        event = events.save(event);
        if (request.participants() != null && !request.participants().isEmpty()) syncParticipants(event, request.participants());
        else if (id == null || (isHeadToHead(format) && participantIdentityChanged)) {
            List<EventParticipantRequest> defaults = new ArrayList<>();
            if (request.homeCompetitorId() != null) defaults.add(new EventParticipantRequest(request.homeCompetitorId(), 0, null, null));
            if (request.awayCompetitorId() != null) defaults.add(new EventParticipantRequest(request.awayCompetitorId(), 1, null, null));
            if (!defaults.isEmpty()) syncParticipants(event, defaults);
        }
        audit.record(id == null ? "EVENT_CREATED" : "EVENT_UPDATED", "EVENT", event.getId(),
                "Evento " + event.getTitle() + " salvo com status " + event.getStatus());
        return eventResponse(event);
    }

    @Transactional
    public EventResponse recordResult(Long id, EventResultRequest request) {
        ArenaEvent event = eventForUpdate(id);
        ensureResultMutable(event);
        event.setHomeScore(request.homeScore());
        event.setAwayScore(request.awayScore());
        if (Boolean.TRUE.equals(request.finishEvent())) event.setStatus(EventStatus.FINISHED);
        audit.record("EVENT_RESULT_RECORDED", "EVENT", event.getId(),
                "Resultado registrado para " + event.getTitle() + ": " + event.getHomeScore() + " x " + event.getAwayScore());
        return eventResponse(event);
    }

    @Transactional
    public EventResponse recordClassification(Long id, EventClassificationRequest request) {
        ArenaEvent event = eventForUpdate(id);
        ensureResultMutable(event);
        if (event.getFormat() != EventFormat.INDIVIDUAL && event.getFormat() != EventFormat.RACE)
            throw new ArenaProblem.RuleViolation("Use o placar tradicional para este formato de evento.");
        List<EventParticipant> existing = eventParticipants.findByEventOrderByDisplayOrderAsc(event);
        if (request.participants().size() != existing.size())
            throw new ArenaProblem.RuleViolation("Informe a classificação de todos os participantes do evento.");
        Map<Long, EventParticipant> byCompetitor = existing.stream().collect(java.util.stream.Collectors.toMap(
                value -> value.getCompetitor().getId(), value -> value));
        Set<Integer> positions = new HashSet<>();
        Set<Long> receivedCompetitors = new HashSet<>();
        for (EventParticipantRequest input : request.participants()) {
            if (!receivedCompetitors.add(input.competitorId()))
                throw new ArenaProblem.RuleViolation("Não repita participantes na classificação.");
            EventParticipant participant = byCompetitor.get(input.competitorId());
            if (participant == null) throw new ArenaProblem.RuleViolation("O competidor não pertence a este evento.");
            if (Boolean.TRUE.equals(request.finishEvent()) && input.position() == null)
                throw new ArenaProblem.RuleViolation("Informe a posição final de todos os participantes.");
            if (input.position() != null && !positions.add(input.position()))
                throw new ArenaProblem.RuleViolation("Não repita posições na classificação.");
            participant.setPosition(input.position());
            participant.setScoreLabel(input.scoreLabel());
        }
        if (Boolean.TRUE.equals(request.finishEvent())) event.setStatus(EventStatus.FINISHED);
        audit.record("EVENT_CLASSIFICATION_RECORDED", "EVENT", event.getId(),
                "Classificação registrada para " + event.getTitle());
        return eventResponse(event);
    }

    @Transactional
    public MarketResponse saveMarket(Long id, MarketRequest request) {
        PredictionMarket market = id == null ? new PredictionMarket() : marketForUpdate(id);
        if (id != null && isTerminal(market.getStatus()))
            throw new ArenaProblem.Conflict("Mercado liquidado ou cancelado não pode ser alterado.");
        ArenaEvent event = event(request.eventId());
        String code = normalizeCode(request.code());
        if (code.isBlank()) throw new ArenaProblem.RuleViolation("O código do mercado precisa conter letras ou números.");
        markets.findByEventAndCode(event, code).filter(existing -> !Objects.equals(existing.getId(), id))
                .ifPresent(existing -> { throw new ArenaProblem.Conflict("Já existe um mercado com este código no evento."); });
        MarketStatus requestedStatus = request.status() == null
                ? (id == null ? MarketStatus.OPEN : market.getStatus())
                : request.status();
        validateGenericMarketStatus(requestedStatus);
        validateMarketEvent(event, requestedStatus, id == null);
        if (id != null) validateMarketTransition(market, requestedStatus);
        List<MarketOption> existingOptions = id == null ? List.of() : options.findByMarketOrderByIdAsc(market);
        if (id != null && predictions.existsByMarket(market))
            validateFrozenMarketStructure(market, event, request, existingOptions);
        market.setEvent(event);
        market.setCode(code);
        market.setName(request.name().trim());
        market.setStatus(requestedStatus);
        market.setMinimumPoints(request.minimumPoints() == null ? 10 : request.minimumPoints());
        market = markets.save(market);
        Set<String> receivedKeys = new HashSet<>();
        for (MarketOptionRequest input : request.options()) {
            String key = normalizeCode(input.key());
            if (!receivedKeys.add(key)) throw new ArenaProblem.RuleViolation("Não repita opções com a mesma chave no mercado.");
            MarketOption option = existingOptions.stream().filter(value -> value.getKey().equals(key)).findFirst().orElseGet(MarketOption::new);
            option.setMarket(market);
            option.setKey(key);
            option.setLabel(input.label().trim());
            option.setMultiplier(input.multiplier());
            option.setActive(input.active() == null || input.active());
            options.save(option);
        }
        existingOptions.stream().filter(value -> !receivedKeys.contains(value.getKey())).forEach(value -> value.setActive(false));
        audit.record(id == null ? "MARKET_CREATED" : "MARKET_UPDATED", "MARKET", market.getId(),
                "Mercado " + market.getName() + " salvo com status " + market.getStatus());
        return marketResponse(market);
    }

    @Transactional
    public MarketResponse changeMarketStatus(Long id, MarketStatus status) {
        PredictionMarket market = marketForUpdate(id);
        if (isTerminal(market.getStatus()))
            throw new ArenaProblem.Conflict("Mercado liquidado ou cancelado não pode ser reaberto.");
        validateGenericMarketStatus(status);
        validateMarketEvent(market.getEvent(), status, false);
        validateMarketTransition(market, status);
        market.setStatus(status);
        audit.record("MARKET_STATUS_CHANGED", "MARKET", market.getId(),
                "Status do mercado " + market.getName() + " alterado para " + status);
        return marketResponse(market);
    }

    public Sport sport(Long id) { return sports.findById(id).orElseThrow(() -> new ArenaProblem.NotFound("Modalidade não encontrada.")); }
    public Sport sportByCode(String code) { return sports.findByCodeIgnoreCase(code).orElseThrow(() -> new ArenaProblem.NotFound("Modalidade não encontrada.")); }
    public Championship championship(Long id) { return championships.findById(id).orElseThrow(() -> new ArenaProblem.NotFound("Campeonato não encontrado.")); }
    public Competitor competitor(Long id) { return competitors.findById(id).orElseThrow(() -> new ArenaProblem.NotFound("Equipe ou participante não encontrado.")); }
    public ArenaEvent event(Long id) { return events.findById(id).orElseThrow(() -> new ArenaProblem.NotFound("Evento não encontrado.")); }
    private ArenaEvent eventForUpdate(Long id) { return events.findByIdForUpdate(id).orElseThrow(() -> new ArenaProblem.NotFound("Evento não encontrado.")); }
    public PredictionMarket market(Long id) { return markets.findById(id).orElseThrow(() -> new ArenaProblem.NotFound("Mercado não encontrado.")); }
    private PredictionMarket marketForUpdate(Long id) { return markets.findByIdForUpdate(id).orElseThrow(() -> new ArenaProblem.NotFound("Mercado não encontrado.")); }

    public SportResponse sportResponse(Sport value) {
        if (value == null) return null;
        return new SportResponse(value.getId(), value.getCode(), value.getName(), value.getCategory(), value.getIcon(), value.isActive(), value.getDisplayOrder());
    }
    public ChampionshipResponse championshipResponse(Championship value) {
        if (value == null) return null;
        return new ChampionshipResponse(value.getId(), value.getSport().getId(), value.getSport().getCode(), value.getName(),
                value.getSlug(), value.getSeason(), value.getStatus(), value.getImageUrl(), value.getStartsAt(), value.getEndsAt());
    }
    public CompetitorResponse competitorResponse(Competitor value) {
        return new CompetitorResponse(value.getId(), value.getSport().getId(), value.getSport().getCode(),
                value.getSport().getName(), value.getName(), value.getCode(), value.getImageUrl(),
                value.getCountry(), value.isActive());
    }
    public EventResponse eventResponse(ArenaEvent value) {
        List<MarketResponse> eventMarkets = markets.findByEventOrderByIdAsc(value).stream().map(this::marketResponse).toList();
        return new EventResponse(value.getId(), value.getExternalKey(), value.getChampionship().getId(), value.getChampionship().getName(),
                sportResponse(value.getChampionship().getSport()), value.getTitle(), value.getStage(), value.getVenue(), value.getBroadcast(),
                value.getImageUrl(), competitorSummary(value.getHomeCompetitor()), competitorSummary(value.getAwayCompetitor()), value.getStartsAt(),
                value.getPredictionClosesAt(), value.getStatus(), value.getFormat(), value.getBestOf(), value.getHomeScore(), value.getAwayScore(),
                value.getClock(), value.getPeriod(), value.getLiveData(), value.isFeatured(), value.isDemo(),
                eventParticipants.findByEventOrderByDisplayOrderAsc(value).stream().map(this::eventParticipantResponse).toList(), eventMarkets);
    }
    public MarketResponse marketResponse(PredictionMarket value) {
        return new MarketResponse(value.getId(), value.getCode(), value.getName(), value.getStatus(), value.getMinimumPoints(),
                value.getResultOptionKey(), options.findByMarketOrderByIdAsc(value).stream().map(this::optionResponse).toList());
    }
    private MarketOptionResponse optionResponse(MarketOption value) {
        return new MarketOptionResponse(value.getId(), value.getKey(), value.getLabel(), value.getMultiplier(), value.isActive());
    }
    private CompetitorSummary competitorSummary(Competitor value) {
        return value == null ? null : new CompetitorSummary(value.getId(), value.getName(), value.getCode(), value.getImageUrl());
    }
    private EventParticipantResponse eventParticipantResponse(EventParticipant value) {
        return new EventParticipantResponse(value.getId(), competitorSummary(value.getCompetitor()), value.getDisplayOrder(),
                value.getPosition(), value.getScoreLabel());
    }
    private void syncParticipants(ArenaEvent event, List<EventParticipantRequest> inputs) {
        Map<Long, EventParticipant> existing = eventParticipants.findByEventOrderByDisplayOrderAsc(event).stream()
                .collect(java.util.stream.Collectors.toMap(value -> value.getCompetitor().getId(), value -> value));
        Set<Long> received = new HashSet<>();
        List<EventParticipant> saved = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            EventParticipantRequest input = inputs.get(index);
            if (!received.add(input.competitorId())) throw new ArenaProblem.RuleViolation("Não repita competidores no evento.");
            Competitor competitor = competitor(input.competitorId());
            validateCompetitorSport(competitor, event.getChampionship());
            EventParticipant value = existing.getOrDefault(input.competitorId(), new EventParticipant());
            value.setEvent(event); value.setCompetitor(competitor);
            value.setDisplayOrder(input.displayOrder() == null ? index : input.displayOrder());
            value.setPosition(input.position()); value.setScoreLabel(input.scoreLabel()); saved.add(value);
        }
        eventParticipants.deleteAll(existing.entrySet().stream().filter(entry -> !received.contains(entry.getKey())).map(Map.Entry::getValue).toList());
        eventParticipants.saveAll(saved);
    }
    private void validateCompetitorSport(Competitor competitor, Championship championship) {
        if (competitor != null && !competitor.getSport().getId().equals(championship.getSport().getId()))
            throw new ArenaProblem.RuleViolation("A equipe ou participante não pertence à modalidade do campeonato.");
    }
    private void validateEventCompetitors(EventFormat format, Competitor home, Competitor away,
                                          List<EventParticipantRequest> participants) {
        if (home != null && away != null && home.getId().equals(away.getId()))
            throw new ArenaProblem.RuleViolation("Selecione participantes diferentes para o evento.");
        if (isHeadToHead(format)) {
            if (home == null || away == null)
                throw new ArenaProblem.RuleViolation("Eventos frente a frente exigem dois participantes.");
            if (participants != null && !participants.isEmpty()) {
                Set<Long> ids = participants.stream().map(EventParticipantRequest::competitorId)
                        .collect(java.util.stream.Collectors.toSet());
                if (participants.size() != 2 || !ids.contains(home.getId()) || !ids.contains(away.getId()))
                    throw new ArenaProblem.RuleViolation("A lista do evento deve conter exatamente os dois participantes selecionados.");
            }
            return;
        }
        if (participants == null || participants.size() < 2)
            throw new ArenaProblem.RuleViolation("Eventos individuais ou de corrida exigem ao menos dois participantes.");
    }
    private boolean isHeadToHead(EventFormat format) {
        return format == EventFormat.STANDARD || format == EventFormat.BO1
                || format == EventFormat.BO3 || format == EventFormat.BO5;
    }
    private void ensureResultMutable(ArenaEvent event) {
        if (event.getStatus() == EventStatus.CANCELLED)
            throw new ArenaProblem.Conflict("Eventos cancelados não podem receber resultados.");
        if (event.getStatus() == EventStatus.POSTPONED)
            throw new ArenaProblem.Conflict("Eventos adiados não podem receber resultados.");
        if ((event.getStatus() == EventStatus.SCHEDULED || event.getStatus() == EventStatus.OPEN_FOR_PREDICTIONS)
                && Instant.now().isBefore(event.getStartsAt()))
            throw new ArenaProblem.RuleViolation("O evento precisa começar antes do registro do resultado.");
        if (markets.findByEventForUpdate(event).stream().anyMatch(value -> value.getStatus() == MarketStatus.SETTLED))
            throw new ArenaProblem.Conflict("O resultado não pode ser alterado após a liquidação de um mercado.");
    }
    private void validateEventTransition(EventStatus current, EventStatus target) {
        if (current == target) return;
        boolean allowed = switch (current) {
            case SCHEDULED -> target == EventStatus.OPEN_FOR_PREDICTIONS
                    || target == EventStatus.LIVE || target == EventStatus.POSTPONED;
            case OPEN_FOR_PREDICTIONS -> target == EventStatus.LIVE || target == EventStatus.POSTPONED;
            case LIVE -> target == EventStatus.POSTPONED;
            case POSTPONED -> target == EventStatus.SCHEDULED || target == EventStatus.OPEN_FOR_PREDICTIONS;
            case FINISHED, CANCELLED -> false;
        };
        if (!allowed)
            throw new ArenaProblem.RuleViolation("Transição de status do evento não permitida: "
                    + current + " → " + target + ".");
    }
    private void validateMarketEvent(ArenaEvent event, MarketStatus target, boolean creating) {
        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.FINISHED)
            throw new ArenaProblem.Conflict("Eventos encerrados ou cancelados não aceitam alterações de mercado.");
        if (creating && event.getStatus() == EventStatus.LIVE)
            throw new ArenaProblem.Conflict("Não é possível criar um mercado depois que o evento começou.");
        if (target == MarketStatus.OPEN && (event.getStatus() != EventStatus.OPEN_FOR_PREDICTIONS
                || !Instant.now().isBefore(event.getPredictionClosesAt())))
            throw new ArenaProblem.RuleViolation("O mercado só pode abrir enquanto o evento aceita palpites.");
    }
    private void validateFrozenMarketStructure(PredictionMarket market, ArenaEvent event, MarketRequest request,
                                               List<MarketOption> existingOptions) {
        int requestedMinimum = request.minimumPoints() == null ? 10 : request.minimumPoints();
        if (!market.getEvent().getId().equals(event.getId())
                || !market.getCode().equals(normalizeCode(request.code()))
                || market.getMinimumPoints() != requestedMinimum)
            throw new ArenaProblem.Conflict("A estrutura do mercado não pode mudar depois do primeiro palpite.");

        Map<String, MarketOptionRequest> requestedOptions = new HashMap<>();
        for (MarketOptionRequest input : request.options()) {
            String key = normalizeCode(input.key());
            if (requestedOptions.put(key, input) != null)
                throw new ArenaProblem.RuleViolation("Não repita opções com a mesma chave no mercado.");
        }
        if (requestedOptions.size() != existingOptions.size())
            throw new ArenaProblem.Conflict("As opções do mercado não podem mudar depois do primeiro palpite.");
        for (MarketOption existing : existingOptions) {
            MarketOptionRequest input = requestedOptions.get(existing.getKey());
            boolean requestedActive = input != null && (input.active() == null || input.active());
            if (input == null || !existing.getLabel().equals(input.label().trim())
                    || existing.getMultiplier().compareTo(input.multiplier()) != 0
                    || existing.isActive() != requestedActive)
                throw new ArenaProblem.Conflict("As opções do mercado não podem mudar depois do primeiro palpite.");
        }
    }
    private boolean isTerminal(MarketStatus status) {
        return status == MarketStatus.SETTLED || status == MarketStatus.CANCELLED;
    }
    private void validateGenericMarketStatus(MarketStatus status) {
        if (isTerminal(status))
            throw new ArenaProblem.RuleViolation("Use a ação específica para liquidar ou cancelar o mercado.");
    }
    private void validateMarketTransition(PredictionMarket market, MarketStatus target) {
        MarketStatus current = market.getStatus();
        if (current == target) return;
        boolean allowed = switch (current) {
            case DRAFT -> target == MarketStatus.OPEN || target == MarketStatus.CLOSED;
            case OPEN -> target == MarketStatus.SUSPENDED || target == MarketStatus.CLOSED;
            case SUSPENDED -> target == MarketStatus.OPEN || target == MarketStatus.CLOSED;
            case CLOSED -> target == MarketStatus.OPEN
                    && market.getEvent().getStatus() == EventStatus.OPEN_FOR_PREDICTIONS
                    && Instant.now().isBefore(market.getEvent().getPredictionClosesAt());
            case SETTLED, CANCELLED -> false;
        };
        if (!allowed)
            throw new ArenaProblem.RuleViolation("Transição de status do mercado não permitida: "
                    + current + " → " + target + ".");
    }
    private String normalizeCode(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("(^_+|_+$)", "");
    }
    private String slug(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    }
    private String optionalText(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
