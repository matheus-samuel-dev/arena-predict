package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.ArenaDtos.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
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

    public ArenaCatalogService(SportRepository sports, ChampionshipRepository championships,
                               CompetitorRepository competitors, ArenaEventRepository events,
                               PredictionMarketRepository markets, MarketOptionRepository options,
                               EventParticipantRepository eventParticipants) {
        this.sports = sports;
        this.championships = championships;
        this.competitors = competitors;
        this.events = events;
        this.markets = markets;
        this.options = options;
        this.eventParticipants = eventParticipants;
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
        sports.findByCodeIgnoreCase(code).filter(existing -> !Objects.equals(existing.getId(), id))
                .ifPresent(existing -> { throw new ArenaProblem.Conflict("Já existe uma modalidade com este código."); });
        sport.setCode(code);
        sport.setName(request.name().trim());
        sport.setCategory(request.category());
        sport.setIcon(request.icon());
        if (request.active() != null) sport.setActive(request.active());
        if (request.displayOrder() != null) sport.setDisplayOrder(request.displayOrder());
        return sportResponse(sports.save(sport));
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
        championship.setSport(sport(request.sportId()));
        championship.setName(request.name().trim());
        championship.setSlug(slug(request.slug()));
        championship.setSeason(request.season().trim());
        championship.setStatus(request.status() == null ? ChampionshipStatus.ACTIVE : request.status());
        championship.setImageUrl(request.imageUrl());
        championship.setStartsAt(request.startsAt());
        championship.setEndsAt(request.endsAt());
        return championshipResponse(championships.save(championship));
    }

    @Transactional(readOnly = true)
    public List<CompetitorResponse> listCompetitors(Long sportId) {
        List<Competitor> values = sportId == null ? competitors.findAll() : competitors.findBySportOrderByNameAsc(sport(sportId));
        return values.stream().map(this::competitorResponse).toList();
    }

    @Transactional
    public CompetitorResponse saveCompetitor(Long id, CompetitorRequest request) {
        Competitor competitor = id == null ? new Competitor() : competitor(id);
        competitor.setSport(sport(request.sportId()));
        competitor.setName(request.name().trim());
        competitor.setCode(normalizeCode(request.code()));
        competitor.setImageUrl(request.imageUrl());
        competitor.setCountry(request.country());
        if (request.active() != null) competitor.setActive(request.active());
        return competitorResponse(competitors.save(competitor));
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
        ArenaEvent event = id == null ? new ArenaEvent() : event(id);
        EventStatus requestedStatus = request.status() == null ? EventStatus.SCHEDULED : request.status();
        if (id != null && requestedStatus == EventStatus.CANCELLED && event.getStatus() != EventStatus.CANCELLED)
            throw new ArenaProblem.RuleViolation("Use a ação de cancelamento para reembolsar os palpites do evento.");
        Championship championship = championship(request.championshipId());
        event.setExternalKey(request.externalKey().trim());
        event.setChampionship(championship);
        event.setHomeCompetitor(request.homeCompetitorId() == null ? null : competitor(request.homeCompetitorId()));
        event.setAwayCompetitor(request.awayCompetitorId() == null ? null : competitor(request.awayCompetitorId()));
        validateCompetitorSport(event.getHomeCompetitor(), championship);
        validateCompetitorSport(event.getAwayCompetitor(), championship);
        if (!request.predictionClosesAt().isAfter(Instant.now().minusSeconds(365L * 24 * 3600))) {
            throw new ArenaProblem.RuleViolation("A data limite de palpites é inválida.");
        }
        if (request.predictionClosesAt().isAfter(request.startsAt())) {
            throw new ArenaProblem.RuleViolation("O prazo de palpites não pode ser posterior ao início do evento.");
        }
        event.setTitle(request.title().trim());
        event.setStage(request.stage());
        event.setVenue(request.venue());
        event.setBroadcast(request.broadcast());
        event.setImageUrl(request.imageUrl());
        event.setStartsAt(request.startsAt());
        event.setPredictionClosesAt(request.predictionClosesAt());
        event.setStatus(requestedStatus);
        event.setFormat(request.format() == null ? EventFormat.STANDARD : request.format());
        event.setBestOf(request.bestOf() == null ? 1 : request.bestOf());
        event.setFeatured(Boolean.TRUE.equals(request.featured()));
        event.setDemo(Boolean.TRUE.equals(request.demo()));
        event = events.save(event);
        if (request.participants() != null) syncParticipants(event, request.participants());
        else if (id == null) {
            List<EventParticipantRequest> defaults = new ArrayList<>();
            if (request.homeCompetitorId() != null) defaults.add(new EventParticipantRequest(request.homeCompetitorId(), 0, null, null));
            if (request.awayCompetitorId() != null) defaults.add(new EventParticipantRequest(request.awayCompetitorId(), 1, null, null));
            if (!defaults.isEmpty()) syncParticipants(event, defaults);
        }
        return eventResponse(event);
    }

    @Transactional
    public EventResponse recordResult(Long id, EventResultRequest request) {
        ArenaEvent event = event(id);
        event.setHomeScore(request.homeScore());
        event.setAwayScore(request.awayScore());
        if (Boolean.TRUE.equals(request.finishEvent())) event.setStatus(EventStatus.FINISHED);
        return eventResponse(event);
    }

    @Transactional
    public EventResponse recordClassification(Long id, EventClassificationRequest request) {
        ArenaEvent event = event(id);
        List<EventParticipant> existing = eventParticipants.findByEventOrderByDisplayOrderAsc(event);
        Map<Long, EventParticipant> byCompetitor = existing.stream().collect(java.util.stream.Collectors.toMap(
                value -> value.getCompetitor().getId(), value -> value));
        Set<Integer> positions = new HashSet<>();
        for (EventParticipantRequest input : request.participants()) {
            EventParticipant participant = byCompetitor.get(input.competitorId());
            if (participant == null) throw new ArenaProblem.RuleViolation("O competidor não pertence a este evento.");
            if (input.position() != null && !positions.add(input.position()))
                throw new ArenaProblem.RuleViolation("Não repita posições na classificação.");
            participant.setPosition(input.position());
            participant.setScoreLabel(input.scoreLabel());
        }
        if (Boolean.TRUE.equals(request.finishEvent())) event.setStatus(EventStatus.FINISHED);
        return eventResponse(event);
    }

    @Transactional
    public MarketResponse saveMarket(Long id, MarketRequest request) {
        PredictionMarket market = id == null ? new PredictionMarket() : market(id);
        if (id != null && market.getStatus() == MarketStatus.SETTLED)
            throw new ArenaProblem.Conflict("Mercado liquidado não pode ser alterado.");
        ArenaEvent event = event(request.eventId());
        market.setEvent(event);
        market.setCode(normalizeCode(request.code()));
        market.setName(request.name().trim());
        market.setStatus(request.status() == null ? MarketStatus.OPEN : request.status());
        market.setMinimumPoints(request.minimumPoints() == null ? 10 : request.minimumPoints());
        market = markets.save(market);
        List<MarketOption> existingOptions = id == null ? List.of() : options.findByMarketOrderByIdAsc(market);
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
        return marketResponse(market);
    }

    @Transactional
    public MarketResponse changeMarketStatus(Long id, MarketStatus status) {
        PredictionMarket market = market(id);
        if (market.getStatus() == MarketStatus.SETTLED)
            throw new ArenaProblem.Conflict("Mercado liquidado não pode ser reaberto.");
        market.setStatus(status);
        return marketResponse(market);
    }

    public Sport sport(Long id) { return sports.findById(id).orElseThrow(() -> new ArenaProblem.NotFound("Modalidade não encontrada.")); }
    public Sport sportByCode(String code) { return sports.findByCodeIgnoreCase(code).orElseThrow(() -> new ArenaProblem.NotFound("Modalidade não encontrada.")); }
    public Championship championship(Long id) { return championships.findById(id).orElseThrow(() -> new ArenaProblem.NotFound("Campeonato não encontrado.")); }
    public Competitor competitor(Long id) { return competitors.findById(id).orElseThrow(() -> new ArenaProblem.NotFound("Equipe ou participante não encontrado.")); }
    public ArenaEvent event(Long id) { return events.findById(id).orElseThrow(() -> new ArenaProblem.NotFound("Evento não encontrado.")); }
    public PredictionMarket market(Long id) { return markets.findById(id).orElseThrow(() -> new ArenaProblem.NotFound("Mercado não encontrado.")); }

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
        return new CompetitorResponse(value.getId(), value.getSport().getId(), value.getName(), value.getCode(), value.getImageUrl(), value.getCountry(), value.isActive());
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
    private String normalizeCode(String value) { return value.trim().toUpperCase(Locale.ROOT).replace(' ', '_'); }
    private String slug(String value) { return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""); }
}
