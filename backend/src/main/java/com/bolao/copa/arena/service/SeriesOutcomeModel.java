package com.bolao.copa.arena.service;

import com.bolao.copa.arena.domain.ArenaEvent;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.service.MarketDefinitionCatalog.*;
import java.util.*;

/** Joint distribution of final scores; sport estimators supply probabilities, never reward coefficients. */
public final class SeriesOutcomeModel {
    private SeriesOutcomeModel() { }
    public record Score(int home, int away, double probability) { }
    public static List<Score> scores(ArenaEvent event) {
        return scores(event,0.5);
    }
    public static List<Score> scores(ArenaEvent event,double mapProbability) {
        if (event.getBestOf()==null || !List.of(1,3,5).contains(event.getBestOf())) return List.of();
        int home=0,away=0,target=event.getBestOf()/2+1;
        if (event.getStatus()==EventStatus.LIVE && event.isLiveScoreAvailable()) {
            if(event.getHomeScore()==null || event.getAwayScore()==null) return List.of();
            home=event.getHomeScore(); away=event.getAwayScore();
            if(home<0 || away<0 || home>target || away>target || (home==target && away==target)) return List.of();
        }
        return scores(event.getBestOf(),home,away,mapProbability,mapProbability);
    }
    public static List<Score> scores(int bestOf,int home,int away,double firstProbability,double followingProbability) {
        if(!List.of(1,3,5).contains(bestOf)||home<0||away<0) return List.of();
        for(double p:new double[]{firstProbability,followingProbability})
            if(!Double.isFinite(p)||p<0||p>1) throw new IllegalArgumentException("Probability must be in [0,1]");
        int target=bestOf/2+1;
        if(home>target||away>target||(home==target&&away==target)) return List.of();
        var outcomes=new TreeMap<String,Score>();
        remaining(home,away,target,firstProbability,followingProbability,1,true,outcomes);
        return List.copyOf(outcomes.values());
    }
    private static void remaining(int h,int a,int target,double first,double next,double mass,boolean initial,Map<String,Score> result) {
        if(mass==0) return;
        if(h==target || a==target) {
            String key=h+":"+a;var old=result.get(key);
            result.put(key,new Score(h,a,mass+(old==null?0:old.probability())));return;
        }
        double p=initial?first:next;
        remaining(h+1,a,target,first,next,mass*p,false,result);
        remaining(h,a+1,target,first,next,mass*(1-p),false,result);
    }
    public static boolean wins(Definition d,String key,Score s) {
        return switch(d.strategy()) {
            case WINNER -> key.equals(s.home()>s.away()?"HOME":s.away()>s.home()?"AWAY":"DRAW");
            case EXACT_SCORE -> key.equals("OTHER")?d.options().stream().noneMatch(o->o.key().equals(s.home()+"_"+s.away())):key.equals(s.home()+"_"+s.away());
            case TOTAL -> side(key,s.home()+s.away()-d.line().doubleValue(),"OVER","UNDER");
            case HOME_TOTAL -> side(key,s.home()-d.line().doubleValue(),"OVER","UNDER");
            case AWAY_TOTAL -> side(key,s.away()-d.line().doubleValue(),"OVER","UNDER");
            case HANDICAP -> side(key,s.home()+d.line().doubleValue()-s.away(),"HOME","AWAY");
            case BOTH_SCORE -> key.equals(s.home()>0&&s.away()>0?"YES":"NO");
            case DOUBLE_CHANCE -> switch(key) {case "HOME_DRAW" -> s.home()>=s.away();case "HOME_AWAY" -> s.home()!=s.away();case "AWAY_DRAW" -> s.away()>=s.home();default -> false;};
            case MARGIN -> key.equals(s.home()==s.away()?"DRAW":(s.home()>s.away()?"HOME":"AWAY")+(Math.abs(s.home()-s.away())<=d.line().doubleValue()?"_SMALL":"_LARGE"));
            default -> false;
        };
    }
    private static boolean side(String key,double difference,String positive,String negative) {
        return difference>0?key.equals(positive):difference<0&&key.equals(negative);
    }
    public static double refundProbability(Definition d,List<Score> scores) {
        return scores.stream().filter(s->switch(d.strategy()) {
            case TOTAL -> s.home()+s.away()==d.line().doubleValue();
            case HOME_TOTAL -> s.home()==d.line().doubleValue();
            case AWAY_TOTAL -> s.away()==d.line().doubleValue();
            case HANDICAP -> s.home()+d.line().doubleValue()==s.away();
            default -> false;
        }).mapToDouble(Score::probability).sum();
    }
    public static double probability(Definition d,String key,List<Score> scores) {
        return scores.stream().filter(s->wins(d,key,s)).mapToDouble(Score::probability).sum();
    }
}
