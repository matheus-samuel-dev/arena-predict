package com.bolao.copa.arena.service;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Bounded virtual reward curve. No bookmaker odds, team strength claims or provider calls. */
@Service
public class VirtualMultiplierService {
    public static final BigDecimal MIN=new BigDecimal("1.10"),MAX=new BigDecimal("8.00");
    public static final String VERSION="arena-series-v1";
    private final MarketDefinitionCatalog definitions;
    public VirtualMultiplierService(MarketDefinitionCatalog definitions){this.definitions=definitions;}
    public DemoProbabilityEngine.Quote quote(PredictionMarket market,List<MarketOption> options) {
        var definition=definitions.definition(market,List.of()).orElseThrow();
        var scores=SeriesOutcomeModel.scores(market.getEvent());
        Map<String,BigDecimal> values=new LinkedHashMap<>();
        for(var option:options) values.put(option.getKey(),multiplier(SeriesOutcomeModel.probability(definition,option.getKey(),scores)));
        boolean live=market.getEvent().getStatus()==EventStatus.LIVE;
        String context=live&&market.getEvent().isLiveScoreAvailable()?"placar confirmado da série":live?"placar indisponível, referência equilibrada":"referência pré-jogo equilibrada";
        return new DemoProbabilityEngine.Quote(values,"INTERNAL_MODEL","Modelo interno ArenaPredict · "+context+"; cada mapa restante usa referência 50/50. Multiplicadores de pontos, não são odds da PandaScore.",VERSION);
    }
    public static BigDecimal multiplier(double p) {
        if(!Double.isFinite(p) || p<0 || p>1.000000001) throw new IllegalArgumentException("Probability must be finite and between zero and one");
        if(p<=0)return MAX; if(p>=1)return MIN;
        double surprise=-Math.log(p)/Math.log(2),weight=Math.sqrt(surprise)*(1+3*surprise)/4;
        double anchor=(MAX.doubleValue()-2)/(2-MIN.doubleValue());
        return BigDecimal.valueOf(MIN.doubleValue()+(MAX.doubleValue()-MIN.doubleValue())*weight/(anchor+weight)).setScale(2,RoundingMode.HALF_UP).max(MIN).min(MAX);
    }
    public boolean possible(PredictionMarket market,String key) {
        var d=definitions.definition(market,List.of()).orElseThrow();
        return SeriesOutcomeModel.probability(d,key,SeriesOutcomeModel.scores(market.getEvent()))>0;
    }
}
