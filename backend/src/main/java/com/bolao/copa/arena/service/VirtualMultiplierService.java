package com.bolao.copa.arena.service;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Evidence -> joint probability distribution -> independent virtual reward policy. */
@Service
public class VirtualMultiplierService {
    public static final BigDecimal MIN=VirtualRewardPolicy.MIN,MAX=VirtualRewardPolicy.MAX;
    public static final String VERSION="arena-strength-series-v2";
    private final MarketDefinitionCatalog definitions;
    private final Function<ArenaEvent,HistoricalTeamStrengthService.Strength> strength;
    private final PricingSnapshotCodec snapshots;
    public VirtualMultiplierService(MarketDefinitionCatalog definitions) {
        this(definitions,e->HistoricalTeamStrengthService.neutral("HISTORY_UNAVAILABLE",e.getLastSyncedAt()),
                new PricingSnapshotCodec(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()));
    }
    @Autowired
    public VirtualMultiplierService(MarketDefinitionCatalog definitions,HistoricalTeamStrengthService strength,PricingSnapshotCodec snapshots) {
        this(definitions,strength::estimate,snapshots);
    }
    private VirtualMultiplierService(MarketDefinitionCatalog definitions,Function<ArenaEvent,HistoricalTeamStrengthService.Strength> strength,PricingSnapshotCodec snapshots) {
        this.definitions=definitions;this.strength=strength;this.snapshots=snapshots;
    }
    public DemoProbabilityEngine.Quote quote(PredictionMarket market,List<MarketOption> options) {
        ArenaEvent event=market.getEvent();
        if(event.isResultReviewRequired())return unavailable(market,options,"OFFICIAL_DATA_UNDER_REVIEW");
        if(market.getStatus()==MarketStatus.SETTLED||market.getStatus()==MarketStatus.CANCELLED||market.getStatus()==MarketStatus.CLOSED
                || event.getStatus()==EventStatus.FINISHED||event.getStatus()==EventStatus.CANCELLED) {
            var assessment=snapshots.decode(market.getPricingSnapshot());
            var stored=new LinkedHashMap<String,BigDecimal>();options.forEach(o->stored.put(o.getKey(),o.getMultiplier()));
            return new DemoProbabilityEngine.Quote(stored,"STATIC","Últimos multiplicadores armazenados; os palpites mantêm o valor originalmente confirmado.",
                    assessment==null?"arena-series-v1":assessment.modelVersion(),assessment);
        }
        if(event.getStatus()==EventStatus.LIVE && (!event.isLiveScoreAvailable()||event.getHomeScore()==null||event.getAwayScore()==null))
            return unavailable(market,options,"LIVE_SCORE_UNAVAILABLE");
        if(event.getStatus()==EventStatus.LIVE && (event.getLastSyncedAt()==null||event.getLastSyncedAt().isBefore(Instant.now().minusSeconds(300))))
            return unavailable(market,options,"STALE_SPORTS_SNAPSHOT");
        var definition=definitions.definition(market,List.of()).orElse(null);
        if(definition==null||event.getBestOf()==null) return unavailable(market,options,"SERIES_FORMAT_UNAVAILABLE");
        if(!"score".equals(definition.metric())) return unavailable(market,options,"UNSUPPORTED_SPORTING_METRIC");
        var evidence=strength.apply(event);var estimate=evidence.estimate();
        var scores=SeriesOutcomeModel.scores(event,estimate.mapProbability());
        if(scores.isEmpty()) return unavailable(market,options,"INVALID_SERIES_STATE");
        String basis="SYMMETRIC_PRIOR".equals(estimate.source())?"referência equilibrada por histórico insuficiente":"resultados oficiais recentes e força relativa dos adversários";
        return ProbabilityQuoteFactory.create(market,options,definition,scores,new ProbabilityQuoteFactory.Evidence(VERSION,estimate.source(),estimate.confidence(),
                evidence.revision(),evidence.asOf(),estimate.homeSamples(),estimate.awaySamples(),estimate.headToHeadSamples(),estimate.limitations(),
                "Estimativa interna baseada em "+basis+(event.getStatus()==EventStatus.LIVE?" e placar confirmado":"")+
                ". Confiança "+("MODERATE".equals(estimate.confidence())?"moderada":"baixa")+"; recompensa virtual com limites próprios; não são odds da PandaScore nem probabilidades oficiais."));
    }
    private DemoProbabilityEngine.Quote unavailable(PredictionMarket market,List<MarketOption> options,String reason) {
        return ProbabilityQuoteFactory.unavailable(market,options,VERSION,reason);
    }
    public static BigDecimal multiplier(double p) { return VirtualRewardPolicy.convert(p,0).displayed(); }
    public boolean possible(PredictionMarket market,String key) {
        var d=definitions.definition(market,List.of()).orElseThrow();
        return SeriesOutcomeModel.probability(d,key,SeriesOutcomeModel.scores(market.getEvent()))>0;
    }
}
