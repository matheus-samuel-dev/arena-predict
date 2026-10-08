package com.bolao.copa.arena.service;

import com.bolao.copa.arena.api.ArenaDtos;
import com.bolao.copa.arena.domain.*;
import java.util.List;
import org.springframework.stereotype.Component;

/** Shared command validation and quote: normal and isolated training use identical rules. */
@Component
public class PredictionSelectionRules {
    private final MarketAvailabilityService availability;
    private final DemoProbabilityEngine pricing;
    public PredictionSelectionRules(MarketAvailabilityService availability, DemoProbabilityEngine pricing) {
        this.availability=availability; this.pricing=pricing;
    }
    public DemoProbabilityEngine.Quote confirm(PredictionMarket market, MarketOption option, ArenaDtos.PlacePredictionRequest request) {
        var decision=availability.evaluate(market);
        if(!decision.allowed()) throw new ArenaProblem.RuleViolation(decision.label()+". "+decision.reason());
        if(!option.isActive()) throw new ArenaProblem.RuleViolation("Esta opção está suspensa.");
        if(request.stakePoints()<market.getMinimumPoints()) throw new ArenaProblem.RuleViolation("O mínimo para este mercado é "+market.getMinimumPoints()+" pontos.");
        if(request.stakePoints()>ArenaDtos.MAX_PREDICTION_STAKE_POINTS) throw new ArenaProblem.RuleViolation("O máximo por palpite é "+ArenaDtos.MAX_PREDICTION_STAKE_POINTS+" pontos.");
        var quote=pricing.quote(market,List.of(option));
        if(request.expectedMultiplier()!=null && request.expectedMultiplier().compareTo(quote.multipliers().get(option.getKey()))!=0)
            throw new ArenaProblem.Conflict("O multiplicador foi atualizado. Atualize o evento e confira o novo valor antes de confirmar.");
        return quote;
    }
}
