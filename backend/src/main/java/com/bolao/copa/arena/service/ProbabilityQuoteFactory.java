package com.bolao.copa.arena.service;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.service.MarketDefinitionCatalog.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Common aggregation/reward conversion; sport strategies supply distinct distributions and evidence. */
public final class ProbabilityQuoteFactory {
    public record Evidence(String model,String source,String confidence,String revision,Instant asOf,int home,int away,int direct,List<String> limitations,String reason) { }
    public static DemoProbabilityEngine.Quote create(PredictionMarket market,List<MarketOption> options,Definition definition,List<SeriesOutcomeModel.Score> scores,Evidence evidence) {
        if(scores.isEmpty())return unavailable(market,options,evidence.model(),"INSUFFICIENT_SPORTING_STATE");
        double refund=SeriesOutcomeModel.refundProbability(definition,scores);
        var probabilities=new TreeMap<String,Double>();var values=new LinkedHashMap<String,BigDecimal>();
        var raw=new TreeMap<String,BigDecimal>();var limited=new TreeSet<String>();
        for(var option:definition.options()) {
            double p=SeriesOutcomeModel.probability(definition,option.key(),scores);probabilities.put(option.key(),p);
            var reward=VirtualRewardPolicy.convert(p,refund);values.put(option.key(),reward.displayed());
            if(reward.uncapped()!=null)raw.put(option.key(),reward.uncapped());if(reward.limited())limited.add(option.key());
        }
        double mass=probabilities.values().stream().mapToDouble(Double::doubleValue).sum()+refund;
        if(definition.strategy()!=Strategy.DOUBLE_CHANCE&&Math.abs(mass-1)>1e-10)
            return unavailable(market,options,evidence.model(),"INCOMPLETE_OUTCOME_PARTITION");
        var limitations=new ArrayList<>(evidence.limitations());if(definition.strategy()==Strategy.DOUBLE_CHANCE)limitations.add("OVERLAPPING_OPTIONS");
        var assessment=new PricingAssessment(true,probabilities,refund,evidence.model(),evidence.source(),evidence.confidence(),evidence.revision(),evidence.asOf(),
                evidence.home(),evidence.away(),evidence.direct(),limitations,VirtualRewardPolicy.VERSION,raw,limited);
        return new DemoProbabilityEngine.Quote(values,"INTERNAL_MODEL",evidence.reason(),evidence.model(),assessment);
    }
    public static DemoProbabilityEngine.Quote unavailable(PredictionMarket market,List<MarketOption> options,String model,String reason) {
        var stored=new LinkedHashMap<String,BigDecimal>();options.forEach(o->stored.put(o.getKey(),o.getMultiplier()));
        var assessment=new PricingAssessment(false,Map.of(),0,model,"INSUFFICIENT_DATA","NONE",reason,market.getEvent().getLastSyncedAt(),0,0,0,
                List.of(reason),VirtualRewardPolicy.VERSION,Map.of(),Set.of());
        return new DemoProbabilityEngine.Quote(stored,"UNAVAILABLE","Aguardando dados confiáveis para estimar este mercado. Valores armazenados não são uma nova estimativa.",model,assessment);
    }
}
