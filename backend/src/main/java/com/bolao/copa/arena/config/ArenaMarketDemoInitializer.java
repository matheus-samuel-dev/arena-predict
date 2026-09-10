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
    public ArenaMarketDemoInitializer(ArenaEventRepository events, PredictionMarketRepository markets,
            EventParticipantRepository participants, MarketDefinitionCatalog definitions, MarketTemplateService templates) {
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
            boolean firstUpgrade = markets.findByEventOrderByIdAsc(event).stream().noneMatch(m -> m.getTemplateCode()!=null);
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
            if (firstUpgrade) {
                if (key.equals("football-live")) state(generated,"TOTAL_CORNERS",MarketStatus.SUSPENDED);
                if (key.equals("vct-open")) state(generated,"PISTOL1",MarketStatus.SUSPENDED);
                if (key.equals("tennis-open")) state(generated,"TIEBREAK",MarketStatus.CLOSED);
                if (key.equals("lol-open")) state(generated,"FIRST_BARON",MarketStatus.CANCELLED);
            }
        }
        seedTerminalExample("demo-football-awaiting-result", "Rodada demonstrativa · aguardando liquidação", EventStatus.FINISHED);
    }

    private void seedTerminalExample(String key, String title, EventStatus status) {
        if (events.findByExternalKey(key).isPresent()) return;
        ArenaEvent source=events.findByExternalKey("demo-football-open").orElseThrow();
        ArenaEvent event=new ArenaEvent();
        event.setExternalKey(key); event.setTitle(title); event.setChampionship(source.getChampionship());
        event.setHomeCompetitor(source.getHomeCompetitor()); event.setAwayCompetitor(source.getAwayCompetitor());
        event.setFormat(EventFormat.STANDARD); event.setBestOf(1); event.setDemo(true);
        event.setStage("Demonstração dos estados de mercado"); event.setVenue("Arena digital");
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
    private void state(List<PredictionMarket> values,String code,MarketStatus state) {
        values.stream().filter(m -> code.equals(m.getTemplateCode())).findFirst().ifPresent(m -> m.setStatus(state));
    }
}
