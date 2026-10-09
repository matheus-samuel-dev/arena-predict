package com.bolao.copa.arena.service;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.service.MarketDefinitionCatalog.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/** Canonical real state only: never reads Demo liveData. Missing sport-specific state suspends pricing. */
@Service
public class RealSportProbabilityService {
    public static final String VERSION="real-sport-state-v2";
    private final MarketDefinitionCatalog definitions;
    private final ObjectMapper json;
    private final PricingSnapshotCodec snapshots;
    public RealSportProbabilityService(MarketDefinitionCatalog definitions,ObjectMapper json,PricingSnapshotCodec snapshots) { this.definitions=definitions;this.json=json;this.snapshots=snapshots; }
    public DemoProbabilityEngine.Quote quote(PredictionMarket market,List<MarketOption> options) {
        var event=market.getEvent();var definition=definitions.definition(market,List.of()).orElse(null);
        if(definition==null) {
            var values=new LinkedHashMap<String,java.math.BigDecimal>();options.forEach(o->values.put(o.getKey(),o.getMultiplier()));
            return new DemoProbabilityEngine.Quote(values,"STATIC","Multiplicador definido pela organização; não representa uma probabilidade estimada.");
        }
        if(market.getStatus()==MarketStatus.CLOSED||market.getStatus()==MarketStatus.SETTLED||market.getStatus()==MarketStatus.CANCELLED
                ||event.getStatus()==EventStatus.FINISHED||event.getStatus()==EventStatus.CANCELLED) {
            var values=new LinkedHashMap<String,java.math.BigDecimal>();options.forEach(o->values.put(o.getKey(),o.getMultiplier()));
            var previous=snapshots.decode(market.getPricingSnapshot());
            return new DemoProbabilityEngine.Quote(values,"STATIC","Multiplicadores armazenados; palpites confirmados não são recalculados.",previous==null?null:previous.modelVersion(),previous);
        }
        if(!"score".equals(definition.metric()))return no(market,options,"UNSUPPORTED_METRIC");
        boolean live=event.getStatus()==EventStatus.LIVE;
        if(event.isResultReviewRequired()||(live&&(!event.isLiveScoreAvailable()||event.getHomeScore()==null||event.getAwayScore()==null||event.getLastSyncedAt()==null||event.getLastSyncedAt().isBefore(Instant.now().minusSeconds(300)))))
            return no(market,options,"MISSING_OR_STALE_LIVE_STATE");
        var data=data(event);var limitations=new ArrayList<String>(List.of("MODEL_NOT_CALIBRATED","TEAM_STRENGTH_DATA_UNAVAILABLE"));
        List<SeriesOutcomeModel.Score> scores;
        String sport=event.getChampionship().getSport().getCode();int h=live?event.getHomeScore():0,a=live?event.getAwayScore():0;
        switch(sport) {
            case "FOOTBALL" -> {
                double remaining=90;
                if(live) {
                    if(!Set.of("1H","HT","2H").contains(Objects.toString(event.getSourceStatus(),"")))return no(market,options,"FOOTBALL_REGULATION_SCOPE_UNVERIFIED");
                    double elapsed=minutes(event.getClock());if(elapsed<0||elapsed>=90)return no(market,options,"FOOTBALL_REMAINING_TIME_UNVERIFIED");
                    remaining=90-elapsed;limitations.add("ADDED_TIME_NOT_ESTIMATED");
                }
                limitations.add("GOAL_RATE_PRIOR_1_3_PER_TEAM_90_MIN");
                scores=poisson(h,a,1.3*remaining/90,1.3*remaining/90);
            }
            case "BASKETBALL" -> {
                if(!live) {
                    if(definition.strategy()!=Strategy.WINNER)return no(market,options,"PREMATCH_SCORING_RATE_UNAVAILABLE");
                    scores=List.of(new SeriesOutcomeModel.Score(1,0,0.5),new SeriesOutcomeModel.Score(0,1,0.5));
                } else {
                    Integer duration=integer(data,"quarterMinutes");
                    if(duration==null&&"NBA".equalsIgnoreCase(event.getChampionship().getName())) { duration=12;limitations.add("REGULATION_LENGTH_FROM_NBA_RULES"); }
                    String status=Objects.toString(event.getSourceStatus(),"");
                    if(duration==null||!Set.of(10,12).contains(duration)||!status.matches("Q[1-4]"))return no(market,options,"BASKETBALL_PERIOD_DURATION_UNAVAILABLE");
                    int quarter=Integer.parseInt(status.substring(1));double clock=minutes(event.getClock());
                    if(clock<=0||clock>duration)return no(market,options,"BASKETBALL_CLOCK_UNAVAILABLE");
                    double remaining=(4-quarter)*duration+clock,elapsed=4*duration-remaining;
                    scores=basketball(h,a,elapsed,remaining);limitations.add("NORMAL_SCORING_INCREMENT_APPROXIMATION");limitations.add("SCORING_PRIOR_18_POINTS_OVER_8_MINUTES");
                }
            }
            case "TENNIS" -> {
                if(event.getBestOf()==null||!Set.of(3,5).contains(event.getBestOf())) {
                    if(live||definition.strategy()!=Strategy.WINNER)return no(market,options,"TENNIS_FORMAT_UNAVAILABLE");
                    scores=List.of(new SeriesOutcomeModel.Score(1,0,0.5),new SeriesOutcomeModel.Score(0,1,0.5));
                } else {
                    double next=0.5;
                    if(live) {
                        int index=h+a+1;Integer gh=integer(data,"set"+index+"Home"),ga=integer(data,"set"+index+"Away");
                        if(gh!=null&&ga!=null)next=tennisSet(gh,ga,new HashMap<>());
                    }
                    scores=SeriesOutcomeModel.scores(event.getBestOf(),h,a,next,0.5);
                    limitations.add("SERVE_AND_POINT_STATE_UNAVAILABLE");
                }
            }
            default -> { return no(market,options,"UNSUPPORTED_SPORT_MODEL"); }
        }
        return ProbabilityQuoteFactory.create(market,options,definition,scores,new ProbabilityQuoteFactory.Evidence(VERSION,"CANONICAL_SPORT_STATE_WITH_PRIORS","LOW",
                sport+":"+h+":"+a+":"+event.getClock()+":"+event.getPeriod(),event.getLastSyncedAt(),0,0,0,limitations,
                "Estimativa interna específica da modalidade, com hipóteses de referência e confiança baixa. Recompensas virtuais têm limites próprios; não são odds oficiais."));
    }
    public static List<SeriesOutcomeModel.Score> poisson(int h,int a,double lh,double la) {
        double[] ph=poissonMass(lh),pa=poissonMass(la);var result=new ArrayList<SeriesOutcomeModel.Score>();
        for(int x=0;x<ph.length;x++)for(int y=0;y<pa.length;y++)result.add(new SeriesOutcomeModel.Score(h+x,a+y,ph[x]*pa[y]));return result;
    }
    private static double[] poissonMass(double lambda) {
        if(!Double.isFinite(lambda)||lambda<0||lambda>10)throw new IllegalArgumentException("Unsupported goal intensity");
        double[] p=new double[40];p[0]=Math.exp(-lambda);for(int n=1;n<p.length;n++)p[n]=p[n-1]*lambda/n;
        double sum=Arrays.stream(p).sum();for(int n=0;n<p.length;n++)p[n]/=sum;return p;
    }
    public static List<SeriesOutcomeModel.Score> basketball(int h,int a,double elapsed,double remaining) {
        if(!Double.isFinite(elapsed)||!Double.isFinite(remaining)||elapsed<0||remaining<=0)throw new IllegalArgumentException("Invalid period state");
        // Explicit weak scoring prior, updated by the observed current game, never team popularity.
        double mh=(h+18)*remaining/(elapsed+8),ma=(a+18)*remaining/(elapsed+8),vh=Math.max(0.05,2*mh),va=Math.max(0.05,2*ma);
        int upper=(int)Math.ceil(Math.max(mh+8*Math.sqrt(vh),ma+8*Math.sqrt(va)));
        if(upper>400)return List.of();
        var result=new ArrayList<SeriesOutcomeModel.Score>();double total=0;
        for(int x=0;x<=upper;x++)for(int y=0;y<=upper;y++) {
            double p=Math.exp(-((x-mh)*(x-mh))/(2*vh)-((y-ma)*(y-ma))/(2*va));total+=p;
            if(h+x==a+y) {result.add(new SeriesOutcomeModel.Score(h+x+1,a+y,p/2));result.add(new SeriesOutcomeModel.Score(h+x,a+y+1,p/2));}
            else result.add(new SeriesOutcomeModel.Score(h+x,a+y,p));
        }
        final double normalization=total;return result.stream().map(s->new SeriesOutcomeModel.Score(s.home(),s.away(),s.probability()/normalization)).toList();
    }
    public static double tennisSet(int h,int a,Map<String,Double> cache) {
        if(h<0||a<0||h>30||a>30)throw new IllegalArgumentException("Invalid game state");
        if(h>=6&&h-a>=2)return 1;if(a>=6&&a-h>=2)return 0;
        if(h>=5&&a>=5)return h==a?0.5:h>a?0.75:0.25;
        String key=h+":"+a;if(cache.containsKey(key))return cache.get(key);
        double value=(tennisSet(h+1,a,cache)+tennisSet(h,a+1,cache))/2;cache.put(key,value);return value;
    }
    private DemoProbabilityEngine.Quote no(PredictionMarket m,List<MarketOption> o,String reason) { return ProbabilityQuoteFactory.unavailable(m,o,VERSION,reason); }
    private Map<String,String> data(ArenaEvent e) { try{return e.getResultData()==null?Map.of():json.readValue(e.getResultData(),new TypeReference<Map<String,String>>(){});}catch(Exception invalid){return Map.of();} }
    private static Integer integer(Map<String,String> data,String key) { try{return data.containsKey(key)?Integer.valueOf(data.get(key)):null;}catch(NumberFormatException invalid){return null;} }
    private static double minutes(String value) { try {String[] p=value.split(":");if(p.length>2)return -1;double m=Double.parseDouble(p[0]),s=p.length==2?Double.parseDouble(p[1]):0;return Double.isFinite(m)&&Double.isFinite(s)&&m>=0&&s>=0&&s<60?m+s/60:-1;}catch(Exception invalid){return -1;} }
}
