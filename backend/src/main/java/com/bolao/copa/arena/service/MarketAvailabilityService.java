package com.bolao.copa.arena.service;

import com.bolao.copa.arena.api.ArenaDtos.MarketAvailability;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** The same decision is projected to clients and enforced while holding command locks. */
@Service
public class MarketAvailabilityService {
    private final MarketDefinitionCatalog definitions;
    private final DemoProbabilityEngine pricing;
    public MarketAvailabilityService(MarketDefinitionCatalog definitions, DemoProbabilityEngine pricing) { this.definitions = definitions; this.pricing=pricing; }
    public MarketAvailability evaluate(PredictionMarket market) { return evaluate(market, Instant.now()); }
    public MarketAvailability evaluate(PredictionMarket market, Instant now) {
        ArenaEvent event = market.getEvent();
        if (market.getStatus() == MarketStatus.SETTLED) return no("SETTLED", "Mercado liquidado", "O resultado já foi processado.");
        if (event.getStatus() == EventStatus.CANCELLED || market.getStatus() == MarketStatus.CANCELLED)
            return no("CANCELLED", "Cancelado · pontos devolvidos", "Palpites ativos são reembolsados no cancelamento.");
        if (event.getStatus() == EventStatus.FINISHED) return no("EVENT_FINISHED", "Evento encerrado", "Aguardando processamento dos resultados.");
        if (event.getStatus() == EventStatus.POSTPONED) return no("POSTPONED", "Evento adiado", "Aguarde a atualização do calendário.");
        if (market.getStatus() == MarketStatus.CLOSED) return no("CLOSED", "Palpites encerrados", market.getStatusReason()==null?"Este mercado foi encerrado definitivamente para novos palpites.":market.getStatusReason());
        if (market.getStatus() == MarketStatus.DRAFT) return no("DRAFT", "Aguardando abertura", "As seleções ainda não foram publicadas.");
        boolean started = event.getStatus() == EventStatus.LIVE || !now.isBefore(event.getStartsAt());
        if (market.getTimingMode() == MarketTimingMode.PRE_MATCH_ONLY && started)
            return no("EVENT_STARTED", "Encerrado após o início", "Este mercado aceita palpites somente no pré-jogo.");
        if (market.getOpensAt() != null && now.isBefore(market.getOpensAt()))
            return no("NOT_YET_OPEN", "Aguardando abertura", "A janela de palpites deste mercado ainda não começou.");
        if (market.getClosesAt() != null && !now.isBefore(market.getClosesAt()))
            return no("DEADLINE", "Palpites encerrados", "O horário limite deste mercado foi atingido.");
        if (market.getTimingMode() == MarketTimingMode.PRE_MATCH_ONLY && market.getClosesAt() == null && !now.isBefore(event.getPredictionClosesAt()))
            return no("DEADLINE", "Palpites encerrados", "O prazo de palpites pré-jogo foi atingido.");
        if (event.getStatus() == EventStatus.LIVE && outcomeAlreadyKnown(market))
            return no("OUTCOME_DETERMINED", "Palpites encerrados neste mercado", "O placar ao vivo já atingiu a condição deste mercado. Escolha outro mercado do evento.");
        if (market.getStatus() == MarketStatus.SUSPENDED) return no("SUSPENDED", "Mercado temporariamente suspenso",
                market.getStatusReason()==null?"Suspensão legada sem motivo registrado; aguarde revisão da organização.":market.getStatusReason());
        if (market.getTimingMode() == MarketTimingMode.LIVE_ONLY && event.getStatus() != EventStatus.LIVE)
            return no("WAITING_LIVE", "Aguardando início ao vivo", "Este mercado será habilitado quando o evento estiver ao vivo.");
        // A scheduled event whose clock expired requires an explicit live update.
        if (started && event.getStatus() != EventStatus.LIVE)
            return no("WAITING_LIVE", "Aguardando atualização ao vivo", "O início previsto passou; aguarde a confirmação do evento ao vivo.");
        return new MarketAvailability(true, "OPEN", started ? "Aberto ao vivo" : "Aberto para palpites", "Multiplicadores demonstrativos; pontos exclusivamente virtuais.");
    }
    private boolean outcomeAlreadyKnown(PredictionMarket market) {
        ArenaEvent event = market.getEvent();
        return definitions.definition(market, List.of()).map(d -> {
            int[] current=pricing.current(event,d.metric());
            if(current==null) return false;
            return (d.metric().equals("score") && seriesOutcomeKnown(event, d)) || switch (d.strategy()) {
            case TOTAL -> java.math.BigDecimal.valueOf((long) current[0] + current[1]).compareTo(d.line()) > 0;
            case HOME_TOTAL -> java.math.BigDecimal.valueOf(current[0]).compareTo(d.line()) > 0;
            case BOTH_SCORE -> current[0] > 0 && current[1] > 0;
            default -> false;
        }; }).orElse(false);
    }
    private boolean seriesOutcomeKnown(ArenaEvent event, MarketDefinitionCatalog.Definition definition) {
        String sport=event.getChampionship().getSport().getCode();
        if (!List.of("TENNIS","VOLLEYBALL","CS2","VALORANT","LEAGUE_OF_LEGENDS","DOTA2").contains(sport)) return false;
        int target = sport.equals("VOLLEYBALL")?3:event.getBestOf() / 2 + 1;
        int home = event.getHomeScore(), away = event.getAwayScore();
        if (home >= target || away >= target) return true;
        if (definition.strategy() != MarketDefinitionCatalog.Strategy.HANDICAP) return false;
        // Enumerate possible final series scores. If every path yields the same
        // handicap outcome, accepting further predictions would reveal the answer.
        java.util.Set<Integer> outcomes = new java.util.HashSet<>();
        for (int losingScore=away; losingScore<target; losingScore++)
            outcomes.add(java.math.BigDecimal.valueOf(target-losingScore).add(definition.line()).signum());
        for (int losingScore=home; losingScore<target; losingScore++)
            outcomes.add(java.math.BigDecimal.valueOf(losingScore-target).add(definition.line()).signum());
        return outcomes.size()==1;
    }
    public MarketAvailability withOptions(PredictionMarket market, List<MarketOption> options) {
        MarketAvailability result = evaluate(market);
        return result.allowed() && options.stream().noneMatch(MarketOption::isActive)
                ? no("OPTIONS_SUSPENDED", "Seleções temporariamente suspensas", "Nenhuma opção deste mercado está ativa.") : result;
    }
    public MarketStatus effectiveStatus(PredictionMarket market,List<MarketOption> options) {
        var decision=withOptions(market,options);
        if(decision.allowed()) return MarketStatus.OPEN;
        return switch(decision.code()) {
            case "CANCELLED" -> MarketStatus.CANCELLED;
            case "SETTLED" -> MarketStatus.SETTLED;
            case "EVENT_FINISHED", "EVENT_STARTED", "DEADLINE", "OUTCOME_DETERMINED", "CLOSED" -> MarketStatus.CLOSED;
            case "DRAFT", "NOT_YET_OPEN", "WAITING_LIVE" -> MarketStatus.DRAFT;
            default -> MarketStatus.SUSPENDED;
        };
    }
    /** Called only from event update transactions, never from a GET. */
    public void closeDeterminedMarkets(List<PredictionMarket> markets) {
        for(var market:markets) {
            if(market.getStatus()!=MarketStatus.OPEN && market.getStatus()!=MarketStatus.SUSPENDED) continue;
            var decision=evaluate(market);
            if(List.of("EVENT_STARTED","DEADLINE","OUTCOME_DETERMINED","EVENT_FINISHED").contains(decision.code())) {
                market.setStatus(MarketStatus.CLOSED); market.setStatusReason(decision.label()+". "+decision.reason());
            }
        }
    }
    public static String eventLabel(List<com.bolao.copa.arena.api.ArenaDtos.MarketResponse> markets) {
        long count = markets.stream().filter(m -> m.availability().allowed()).count();
        if (count > 0) return "Aberto para palpites · " + count + (count == 1 ? " mercado" : " mercados");
        return markets.stream().filter(m -> "SUSPENDED".equals(m.availability().code())).findFirst()
                .or(() -> markets.stream().findFirst()).map(m -> m.availability().label()).orElse("Aguardando publicação de mercados");
    }
    private MarketAvailability no(String code, String label, String reason) { return new MarketAvailability(false, code, label, reason); }
}
