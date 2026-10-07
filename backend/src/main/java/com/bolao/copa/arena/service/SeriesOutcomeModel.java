package com.bolao.copa.arena.service;

import com.bolao.copa.arena.domain.ArenaEvent;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.service.MarketDefinitionCatalog.*;
import java.util.*;

/** Feasible final series scores, with a transparent 50/50 prior for each remaining map. */
public final class SeriesOutcomeModel {
    private SeriesOutcomeModel() { }
    public record Score(int home, int away, double probability) { }
    public static List<Score> scores(ArenaEvent event) {
        if (event.getBestOf()==null || !List.of(1,3,5).contains(event.getBestOf())) return List.of();
        int home=0,away=0,target=event.getBestOf()/2+1;
        if (event.getStatus()==EventStatus.LIVE && event.isLiveScoreAvailable()) {
            if(event.getHomeScore()==null || event.getAwayScore()==null) return List.of();
            home=event.getHomeScore(); away=event.getAwayScore();
            if(home<0 || away<0 || home>target || away>target || (home==target && away==target)) return List.of();
        }
        List<Score> result=new ArrayList<>(); remaining(home,away,target,1,result); return result;
    }
    private static void remaining(int h,int a,int target,double p,List<Score> result) {
        if(h==target || a==target) {result.add(new Score(h,a,p));return;}
        remaining(h+1,a,target,p/2,result); remaining(h,a+1,target,p/2,result);
    }
    public static boolean wins(Definition d,String key,Score s) {
        return switch(d.strategy()) {
            case WINNER -> key.equals(s.home()>s.away()?"HOME":"AWAY");
            case EXACT_SCORE -> key.equals(s.home()+"_"+s.away());
            case TOTAL -> key.equals("OVER")== (s.home()+s.away()>d.line().doubleValue());
            case HOME_TOTAL -> key.equals("OVER")== (s.home()>d.line().doubleValue());
            case AWAY_TOTAL -> key.equals("OVER")== (s.away()>d.line().doubleValue());
            case HANDICAP -> key.equals("HOME")== (s.home()+d.line().doubleValue()>s.away());
            default -> false;
        };
    }
    public static double probability(Definition d,String key,List<Score> scores) {
        return scores.stream().filter(s->wins(d,key,s)).mapToDouble(Score::probability).sum();
    }
}
