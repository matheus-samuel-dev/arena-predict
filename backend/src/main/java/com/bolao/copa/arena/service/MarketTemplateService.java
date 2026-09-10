package com.bolao.copa.arena.service;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketTemplateService {
    private final ArenaEventRepository events;
    private final PredictionMarketRepository markets;
    private final MarketOptionRepository options;
    private final EventParticipantRepository participants;
    private final MarketDefinitionCatalog definitions;
    public MarketTemplateService(ArenaEventRepository events, PredictionMarketRepository markets,
            MarketOptionRepository options, EventParticipantRepository participants, MarketDefinitionCatalog definitions) {
        this.events=events; this.markets=markets; this.options=options; this.participants=participants; this.definitions=definitions;
    }
    @Transactional
    public List<PredictionMarket> generate(Long eventId) {
        ArenaEvent event = events.findByIdForUpdate(eventId).orElseThrow(() -> new ArenaProblem.NotFound("Evento não encontrado."));
        if (event.getStatus()==EventStatus.FINISHED || event.getStatus()==EventStatus.CANCELLED)
            throw new ArenaProblem.RuleViolation("Eventos encerrados não recebem novos mercados.");
        List<PredictionMarket> result = new ArrayList<>();
        List<PredictionMarket> published = markets.findByEventOrderByIdAsc(event);
        for (var definition : definitions.definitions(event,participants.findByEventOrderByDisplayOrderAsc(event))) {
            var sameTemplate = published.stream().filter(m -> definition.code().equals(m.getTemplateCode())).findFirst();
            if (sameTemplate.isPresent()) { result.add(sameTemplate.get()); continue; }
            String code = "AUTO_" + definition.code();
            PredictionMarket existing = markets.findByEventAndCode(event,code).orElse(null);
            if (existing!=null) { result.add(existing); continue; }
            PredictionMarket market = new PredictionMarket();
            market.setEvent(event); market.setCode(code); market.setTemplateCode(definition.code());
            market.setName(definition.name()); market.setCategory(definition.category());
            market.setTimingMode(definition.timingMode()); market.setStatus(MarketStatus.OPEN);
            market.setOpensAt(event.getStartsAt().minusSeconds(7*86400));
            market.setClosesAt(definition.timingMode()==MarketTimingMode.PRE_MATCH_ONLY ? event.getPredictionClosesAt() : event.getStartsAt().plusSeconds(8*3600));
            definitions.snapshot(market,definition); markets.save(market);
            for (var choice : definition.options()) {
                MarketOption option = new MarketOption(); option.setMarket(market); option.setKey(choice.key());
                option.setLabel(choice.label()); option.setMultiplier(choice.multiplier()); option.setActive(true); options.save(option);
            }
            result.add(market);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<MarketDefinitionCatalog.Definition> templates(Long eventId) {
        ArenaEvent event = events.findById(eventId).orElseThrow(() -> new ArenaProblem.NotFound("Evento não encontrado."));
        return definitions.definitions(event, participants.findByEventOrderByDisplayOrderAsc(event));
    }
}
