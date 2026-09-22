package com.bolao.copa.arena.config;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.arena.service.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Upgrades only the named demo fixtures, preserving predictions and saved multipliers. */
@Component
@ConditionalOnProperty(name="app.demo.enabled", havingValue="true")
public class ArenaMarketDemoInitializer {
    private final ArenaEventRepository events;
    private final PredictionMarketRepository markets;
    private final EventParticipantRepository participants;
    private final MarketDefinitionCatalog definitions;
    private final MarketTemplateService templates;
    private final MarketOptionRepository options;
    private final MarketAvailabilityService availability;
    public ArenaMarketDemoInitializer(ArenaEventRepository events, PredictionMarketRepository markets,
            EventParticipantRepository participants, MarketDefinitionCatalog definitions, MarketTemplateService templates, MarketOptionRepository options, MarketAvailabilityService availability) {
        this.availability=availability;
        this.options=options;
        this.events=events; this.markets=markets; this.participants=participants; this.definitions=definitions; this.templates=templates;
    }
    @EventListener(ApplicationReadyEvent.class)
    @Order(300)
    @Transactional
    public void seed() {
        for (String key : List.of("football-live", "cs2-live", "nba-open", "vct-open", "tennis-open", "lol-open",
                "f1-open", "football-open", "cs2-open", "volleyball-open", "american-football-open", "dota2-open")) {
            ArenaEvent event = events.findByExternalKey("demo-"+key).orElse(null);
            if (event==null || event.getStatus()==EventStatus.FINISHED || event.getStatus()==EventStatus.CANCELLED) continue;
            var catalog = definitions.definitions(event,participants.findByEventOrderByDisplayOrderAsc(event));
            for (var market : markets.findByEventOrderByIdAsc(event)) {
                if (market.getTemplateCode()!=null || market.getStatus()==MarketStatus.SETTLED || market.getStatus()==MarketStatus.CANCELLED) continue;
                catalog.stream().filter(d -> d.code().equals(market.getCode())).findFirst().ifPresent(d -> {
                    market.setTemplateCode(d.code()); market.setCategory(d.category()); market.setName(d.name());
                    market.setTimingMode(d.timingMode());
                    market.setOpensAt(event.getStartsAt().minusSeconds(7*86400));
                    market.setClosesAt(d.timingMode()==MarketTimingMode.PRE_MATCH_ONLY ? event.getPredictionClosesAt() : event.getStartsAt().plusSeconds(8*3600));
                    if (event.getStatus()==EventStatus.LIVE) market.setStatus(MarketStatus.OPEN);
                    definitions.snapshot(market,d);
                });
            }
            var generated=templates.generate(event.getId());
            availability.closeDeterminedMarkets(generated);
            if (key.equals("football-live") && generated.stream().anyMatch(m -> "LIVE_RESULT".equals(m.getTemplateCode()) && m.getStatus()==MarketStatus.CANCELLED)
                    && markets.findByEventAndCode(event,"AUTO_LIVE_RESULT_CURRENT").isEmpty()) {
                var d=catalog.stream().filter(v -> v.code().equals("LIVE_RESULT")).findFirst().orElseThrow();
                var market=new PredictionMarket(); market.setEvent(event); market.setCode("AUTO_LIVE_RESULT_CURRENT");
                market.setTemplateCode("LIVE_RESULT_CURRENT"); market.setName(d.name()+" · edição atual"); market.setCategory(d.category());
                market.setTimingMode(d.timingMode()); market.setStatus(MarketStatus.OPEN);
                market.setOpensAt(event.getStartsAt()); market.setClosesAt(event.getStartsAt().plusSeconds(8*3600));
                definitions.snapshot(market,d); markets.save(market);
                for(var c:d.options()) { var o=new MarketOption(); o.setMarket(market); o.setKey(c.key()); o.setLabel(c.label()); o.setMultiplier(c.multiplier()); o.setActive(true); options.save(o); }
            }
        }
        seedTerminalExample("demo-football-awaiting-result", "Rodada finalizada · aguardando liquidação", EventStatus.FINISHED);
        seedLiveExample("nba", "nba-open", 110, 100, "00:30", "4º quarto", "{\"demo\":true,\"quarter\":4,\"quarterMinutes\":12}");
        seedLiveExample("tennis", "tennis-open", 1, 0, null, "2º set", "{\"demo\":true,\"currentGames\":[5,3]}");
        seedLiveExample("f1", "f1-open", null, null, null, "Corrida em andamento", "{\"demo\":true}");
    }

    private void seedLiveExample(String key,String sourceKey,Integer home,Integer away,String clock,String period,String data) {
        if(events.findByExternalKey("demo-"+key+"-live").isPresent()) return;
        var source=events.findByExternalKey("demo-"+sourceKey).orElseThrow();
        var event=new ArenaEvent();event.setExternalKey("demo-"+key+"-live");event.setTitle(source.getTitle()+" · demonstração ao vivo");
        event.setChampionship(source.getChampionship());event.setHomeCompetitor(source.getHomeCompetitor());event.setAwayCompetitor(source.getAwayCompetitor());
        event.setFormat(source.getFormat());event.setBestOf(source.getBestOf());event.setDemo(true);event.setStatus(EventStatus.LIVE);
        event.setStartsAt(java.time.Instant.now().minusSeconds(1800));event.setPredictionClosesAt(event.getStartsAt().minusSeconds(300));
        event.setHomeScore(home);event.setAwayScore(away);event.setClock(clock);event.setPeriod(period);event.setLiveData(data);events.saveAndFlush(event);
        for(var p:participants.findByEventOrderByDisplayOrderAsc(source)) { var entry=new EventParticipant();entry.setEvent(event);entry.setCompetitor(p.getCompetitor());entry.setDisplayOrder(p.getDisplayOrder());participants.save(entry); }
        for(var m:templates.generate(event.getId())) {
            // This race demo explicitly offers a stable winner market without a live position feed.
            if(key.equals("f1") && "RACE_WINNER".equals(m.getTemplateCode())) {
                var d=definitions.definition(m,List.of()).orElseThrow();
                m.setTimingMode(MarketTimingMode.LIVE_ENABLED);m.setClosesAt(event.getStartsAt().plusSeconds(8*3600));
                definitions.snapshot(m,new MarketDefinitionCatalog.Definition(d.code(),d.name(),d.category(),d.strategy(),d.metric(),d.line(),MarketTimingMode.LIVE_ENABLED,d.options(),d.fields(),d.settlementDescription()));
            } else if(m.getTimingMode()==MarketTimingMode.PRE_MATCH_ONLY) {
                m.setStatus(MarketStatus.CLOSED);m.setStatusReason("Mercado encerrado após o início; exclusivo de pré-jogo.");
            }
        }
    }

    private void seedTerminalExample(String key, String title, EventStatus status) {
        var existing = events.findByExternalKey(key);
        if (existing.isPresent()) {
            existing.get().setTitle(title);
            existing.get().setStage("Validação dos estados de mercado");
            return;
        }
        ArenaEvent source=events.findByExternalKey("demo-football-open").orElseThrow();
        ArenaEvent event=new ArenaEvent();
        event.setExternalKey(key); event.setTitle(title); event.setChampionship(source.getChampionship());
        event.setHomeCompetitor(source.getHomeCompetitor()); event.setAwayCompetitor(source.getAwayCompetitor());
        event.setFormat(EventFormat.STANDARD); event.setBestOf(1); event.setDemo(true);
        event.setStage("Validação dos estados de mercado"); event.setVenue("Arena digital");
        event.setStartsAt(java.time.Instant.now().minusSeconds(10800));
        event.setPredictionClosesAt(event.getStartsAt().minusSeconds(300));
        event.setStatus(EventStatus.SCHEDULED);
        event=events.saveAndFlush(event);
        int order=0;
        for (var competitor : List.of(source.getHomeCompetitor(), source.getAwayCompetitor())) {
            EventParticipant participant=new EventParticipant();
            participant.setEvent(event); participant.setCompetitor(competitor); participant.setDisplayOrder(order++);
            participants.save(participant);
        }
        // No predictions exist on these new fixtures. Generate the same persisted
        // definitions as an operational event, then demonstrate its terminal state.
        for (var market : templates.generate(event.getId()))
            market.setStatus(status==EventStatus.CANCELLED ? MarketStatus.CANCELLED : MarketStatus.CLOSED);
        event.setStatus(status);
        if (status==EventStatus.FINISHED) { event.setHomeScore(3); event.setAwayScore(1); }
    }
}
