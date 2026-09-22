package com.bolao.copa.arena.service;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.fasterxml.jackson.databind.*;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Pure virtual-points model v2. No market margin, random inputs, IO or entity writes. */
@Service
public class DemoProbabilityEngine {
    public static final BigDecimal MIN = new BigDecimal("1.05"), MAX = new BigDecimal("15.00");
    private static final double CURVE_ANCHOR = (MAX.doubleValue() - 2) / (2 - MIN.doubleValue());
    private final MarketDefinitionCatalog definitions;
    private final ObjectMapper json;
    public DemoProbabilityEngine(MarketDefinitionCatalog definitions, ObjectMapper json) { this.definitions=definitions; this.json=json; }
    public record Quote(Map<String, BigDecimal> multipliers, String mode, String reason) { }
    private record FinalScore(int home, int away, double probability) { }

    public Quote quote(PredictionMarket market, List<MarketOption> options) {
        Map<String,BigDecimal> values=new LinkedHashMap<>();
        options.forEach(o -> values.put(o.getKey(), o.getMultiplier().max(MIN).min(MAX).setScale(2,RoundingMode.HALF_UP)));
        var definition=definitions.definition(market,List.of()).orElse(null);
        ArenaEvent event=market.getEvent();
        if (event.getStatus()!=EventStatus.LIVE || definition==null || market.getStatus()!=MarketStatus.OPEN || market.getTimingMode()==MarketTimingMode.PRE_MATCH_ONLY)
            return new Quote(values,"STATIC","Multiplicadores demonstrativos estáticos.");
        List<FinalScore> scores=distribution(event,definition.metric());
        if (scores.isEmpty()) return new Quote(values,"STATIC","Multiplicadores demonstrativos estáticos: dados ao vivo insuficientes para este mercado.");
        for (var option:options) {
            double probability=0;
            for (var score:scores) {
                Boolean wins=matches(definition,option.getKey(),score);
                if (wins==null) return new Quote(values,"STATIC","Multiplicadores demonstrativos estáticos: contexto não modelado.");
                if (wins) probability+=score.probability();
            }
            values.put(option.getKey(),multiplier(probability));
        }
        return new Quote(values,"DYNAMIC","Estimativa demonstrativa v2 calculada pelo placar e contexto esportivo disponíveis.");
    }
    public static BigDecimal multiplier(double probability) {
        if (!Double.isFinite(probability)) throw new IllegalArgumentException("Probability must be finite");
        if (probability <= 0) return MAX;
        if (probability >= 1) return MIN;
        // Surprise in bits. The square root separates strong favorites; the cubic
        // term distinguishes rare outcomes. Rational compression approaches the
        // ceiling smoothly, with p=0.5 anchored at 2x. No inverse-price hard cut.
        double surprise = -Math.log(probability) / Math.log(2);
        double weight = Math.sqrt(surprise) * (1 + 3 * surprise) / 4;
        double value = MIN.doubleValue() + (MAX.doubleValue() - MIN.doubleValue())
                * weight / (CURVE_ANCHOR + weight);
        return BigDecimal.valueOf(value).setScale(2,RoundingMode.HALF_UP);
    }
    private Boolean matches(MarketDefinitionCatalog.Definition d,String key,FinalScore s) {
        double line=d.line()==null?0:d.line().doubleValue();
        return switch(d.strategy()) {
            case WINNER -> key.equals("HOME")?s.home()>s.away():key.equals("AWAY")?s.away()>s.home():s.home()==s.away();
            case TOTAL -> key.equals("OVER") ? s.home()+s.away()>line : s.home()+s.away()<line;
            case HOME_TOTAL -> key.equals("OVER") ? s.home()>line : s.home()<line;
            case HANDICAP -> key.equals("HOME") ? s.home()+line>s.away() : s.home()+line<s.away();
            case BOTH_SCORE -> key.equals("YES")== (s.home()>0 && s.away()>0);
            case DOUBLE_CHANCE -> switch(key) { case "HOME_DRAW" -> s.home()>=s.away(); case "HOME_AWAY" -> s.home()!=s.away(); default -> s.home()<=s.away(); };
            default -> null;
        };
    }
    private List<FinalScore> distribution(ArenaEvent event,String metric) {
        JsonNode data=data(event);
        int[] current=current(event,metric);
        if (current==null) return List.of();
        String sport=event.getChampionship().getSport().getCode();
        if (sport.equals("FOOTBALL")) {
            double remaining=footballRemaining(event,data);
            if (remaining<0) return List.of();
            double rate=switch(metric) { case "score" -> 2.6; case "corners" -> 10; case "cards" -> 4.5; default -> -1; };
            return rate<0?List.of():poissonScores(current[0],current[1],rate*remaining/180);
        }
        if (!metric.equals("score")) return List.of();
        if (sport.equals("BASKETBALL")) {
            if (!data.has("quarter") || !data.has("quarterMinutes")) return List.of();
            double clock=clockMinutes(event.getClock());
            int quarter=data.path("quarter").asInt(), duration=data.path("quarterMinutes").asInt();
            if (clock<0 || quarter<1 || quarter>20 || (duration!=10 && duration!=12) || clock>duration) return List.of();
            double seconds=(Math.max(0,4-quarter)*duration+clock)*60;
            // Two teams, roughly one possession each per 24 seconds, high scoring variance.
            double possessions=seconds/24;
            double variance=Math.max(0.05,possessions*1.4);
            // Independent remaining team scores keep totals uncertain too.
            List<FinalScore> result=new ArrayList<>();
            double expected=possessions*1.1;
            double sum=0;
            // Retain rare comeback tails: five standard deviations excluded even
            // a tie when +10 with 30 seconds left, producing an artificial p=0.
            int lower=Math.max(0,(int)Math.floor(expected-8*Math.sqrt(variance)));
            int upper=(int)Math.ceil(expected+8*Math.sqrt(variance));
            for(int h=lower;h<=upper;h++) for(int a=lower;a<=upper;a++) {
                double weight=Math.exp(-((h-expected)*(h-expected)+(a-expected)*(a-expected))/(2*variance));sum+=weight;
                result.add(new FinalScore(current[0]+h,current[1]+a,weight));
            }
            final double normalization=sum;
            // Winner: explicitly include overtime as a fair tiebreak, never offer a draw.
            List<FinalScore> normalized=new ArrayList<>();
            for(var s:result) {
                if(s.home()==s.away()) { normalized.add(new FinalScore(s.home()+1,s.away(),s.probability()/normalization/2)); normalized.add(new FinalScore(s.home(),s.away()+1,s.probability()/normalization/2)); }
                else normalized.add(new FinalScore(s.home(),s.away(),s.probability()/normalization));
            }
            return normalized;
        }
        if (List.of("TENNIS","VOLLEYBALL","CS2","VALORANT","LEAGUE_OF_LEGENDS","DOTA2").contains(sport)) {
            int bestOf=event.getBestOf();
            if (sport.equals("VOLLEYBALL")) bestOf=5;
            if (bestOf!=1 && bestOf!=3 && bestOf!=5) return List.of();
            double segment=0.5;
            int[] progress=pair(data,sport.equals("TENNIS")?"currentGames":sport.equals("VOLLEYBALL")?"currentPoints":"currentRounds");
            if ((sport.equals("CS2") || sport.equals("VALORANT")) && progress==null && data.path("maps").isArray()) {
                JsonNode map=data.path("maps").path(current[0]+current[1]);
                String[] parts=map.path("score").asText().split("-");
                if(parts.length==2) try { progress=new int[]{Integer.parseInt(parts[0]),Integer.parseInt(parts[1])}; } catch(NumberFormatException ignored) { }
            }
            if(progress!=null) {
                int target=sport.equals("TENNIS")?6:sport.equals("VOLLEYBALL")?(current[0]+current[1]==4?15:25):13;
                // CS2 MR3 overtime and Valorant win-by-two use distinct boundary states.
                if (sport.equals("CS2") && Math.min(progress[0],progress[1])>=12) target=16+3*((Math.min(progress[0],progress[1])-12)/3);
                segment=segmentWin(progress[0],progress[1],target,new HashMap<>());
            }
            List<FinalScore> result=new ArrayList<>();
            series(current[0],current[1],bestOf/2+1,segment,1,result,true);
            return result;
        }
        return List.of(); // Motorsport: no invented positions, lap counts or precision.
    }
    private void series(int h,int a,int target,double next,double weight,List<FinalScore> result,boolean first) {
        if(h>=target || a>=target) { result.add(new FinalScore(h,a,weight)); return; }
        double p=first?next:0.5;
        series(h+1,a,target,next,weight*p,result,false); series(h,a+1,target,next,weight*(1-p),result,false);
    }
    private double segmentWin(int h,int a,int target,Map<String,Double> memo) {
        if(h>=target && h-a>=2) return 1;
        if(a>=target && a-h>=2) return 0;
        if(h>=target-1 && a>=target-1) return h==a?0.5:h>a?0.75:0.25;
        String key=h+":"+a;
        if(memo.containsKey(key)) return memo.get(key);
        double value=(segmentWin(h+1,a,target,memo)+segmentWin(h,a+1,target,memo))/2;
        memo.put(key,value); return value;
    }
    private List<FinalScore> poissonScores(int home,int away,double lambda) {
        List<FinalScore> result=new ArrayList<>();
        double[] p=new double[40]; p[0]=Math.exp(-lambda);
        for(int i=1;i<p.length;i++) p[i]=p[i-1]*lambda/i;
        double normalization=Arrays.stream(p).sum();
        for(int h=0;h<p.length;h++) for(int a=0;a<p.length;a++) result.add(new FinalScore(home+h,away+a,p[h]*p[a]/(normalization*normalization)));
        return result;
    }
    public int[] current(ArenaEvent event,String metric) {
        if(metric.equals("score")) return event.getHomeScore()==null || event.getAwayScore()==null?null:new int[]{event.getHomeScore(),event.getAwayScore()};
        return pair(data(event),metric);
    }
    private int[] pair(JsonNode data,String key) {
        JsonNode array=data.path(key);
        if(array.isArray() && array.size()==2 && array.get(0).isIntegralNumber() && array.get(1).isIntegralNumber() && array.get(0).canConvertToInt() && array.get(1).canConvertToInt() && array.get(0).asInt()>=0 && array.get(1).asInt()>=0)
            return new int[]{array.get(0).asInt(),array.get(1).asInt()};
        return null;
    }
    private JsonNode data(ArenaEvent event) {
        try { return event.getLiveData()==null?json.createObjectNode():json.readTree(event.getLiveData()); }
        catch(Exception ignored) { return json.createObjectNode(); }
    }
    private double footballRemaining(ArenaEvent event,JsonNode data) {
        String clock=event.getClock();
        double elapsed;
        if(clock!=null && clock.contains("+")) {
            String[] parts=clock.replace("'","").split("\\+");
            elapsed=parts.length==2?clockMinutes(parts[0])+clockMinutes(parts[1]):-1;
        } else elapsed=clockMinutes(clock);
        if(elapsed<0 || elapsed>130) return -1;
        // Five minutes is an explicit model assumption when added time is unknown.
        double end=90+Math.max(0,Math.min(30,data.path("addedTimeMinutes").asDouble(5)));
        // The LIVE flag is authoritative: a nominal clock limit is not a final whistle.
        return Double.isFinite(end)?Math.max(0.25,end-elapsed):-1;
    }
    private double clockMinutes(String clock) {
        if(clock==null) return -1;
        try {
            String[] parts=clock.replace("'","").trim().split(":");
            if(parts.length>2) return -1;
            double minutes=Double.parseDouble(parts[0]), seconds=parts.length==2?Double.parseDouble(parts[1]):0;
            return Double.isFinite(minutes) && Double.isFinite(seconds) && minutes>=0 && seconds>=0 && seconds<60 ? minutes+seconds/60:-1;
        }
        catch(NumberFormatException ignored) { return -1; }
    }
}
