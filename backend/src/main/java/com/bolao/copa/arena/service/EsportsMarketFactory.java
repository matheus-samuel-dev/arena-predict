package com.bolao.copa.arena.service;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.service.MarketDefinitionCatalog.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

/** ArenaPredict contracts; PandaScore supplies sporting results, never these markets or coefficients. */
@Component
public class EsportsMarketFactory {
    private interface SportMarketStrategy { List<Definition> definitions(ArenaEvent event); }
    private final Map<String,SportMarketStrategy> strategies=Map.of(
            "CS2",new CounterStrikeMarketStrategy(),"VALORANT",new ValorantMarketStrategy(),
            "LEAGUE_OF_LEGENDS",new LeagueOfLegendsMarketStrategy());
    public static boolean supports(ArenaEvent e) {
        return "PANDASCORE".equals(e.getExternalProvider()) && e.getChampionship()!=null
                && Set.of("CS2","VALORANT","LEAGUE_OF_LEGENDS").contains(e.getChampionship().getSport().getCode());
    }
    public List<Definition> definitions(ArenaEvent e) {
        if(!supports(e) || e.isDemo() || e.isResultReviewRequired() || e.getHomeCompetitor()==null || e.getAwayCompetitor()==null
                || e.getBestOf()==null || !List.of(1,3,5).contains(e.getBestOf())
                || (e.getSourceMetrics()!=null && !Arrays.asList(e.getSourceMetrics().split(",")).contains("score"))) return List.of();
        if(e.getStatus()!=EventStatus.SCHEDULED && e.getStatus()!=EventStatus.OPEN_FOR_PREDICTIONS && e.getStatus()!=EventStatus.LIVE) return List.of();
        if(e.getStatus()!=EventStatus.LIVE && !Instant.now().isBefore(e.getPredictionClosesAt())) return List.of();
        return strategies.get(e.getChampionship().getSport().getCode()).definitions(e);
    }
    private abstract static class SeriesStrategy implements SportMarketStrategy {
        abstract String unit();
        abstract boolean teamAtLeastOne();
        abstract boolean handicap();
        public List<Definition> definitions(ArenaEvent e) {
            boolean live=e.getStatus()==EventStatus.LIVE;
            if(live && e.isLiveScoreAvailable() && (SeriesOutcomeModel.scores(e).isEmpty()
                    || Math.max(e.getHomeScore(),e.getAwayScore())>=e.getBestOf()/2+1))return List.of();
            MarketTimingMode timing=live?MarketTimingMode.LIVE_ONLY:MarketTimingMode.PRE_MATCH_ONLY;
            String suffix=live?"_LIVE":"",home=e.getHomeCompetitor().getName(),away=e.getAwayCompetitor().getName();
            List<Definition> result=new ArrayList<>();
            result.add(d("SERIES_WINNER"+suffix,"Vencedor da série",Strategy.WINNER,null,timing,List.of(c("HOME",home),c("AWAY",away))));
            // With no current score, only the final winner contract is offered live.
            // Map-specific, totals and exact outcomes need the observed series state.
            if(e.getBestOf()==1 || (live && !e.isLiveScoreAvailable())) return result;
            if(live && SeriesOutcomeModel.scores(e).isEmpty()) return List.of();
            int target=e.getBestOf()/2+1;
            result.add(d("TOTAL_MAPS"+suffix,"Total de "+unit()+" · "+(e.getBestOf()==5?"4,5":"2,5"),Strategy.TOTAL,
                    new BigDecimal(e.getBestOf()==5?"4.5":"2.5"),timing,List.of(c("OVER","Acima"),c("UNDER","Abaixo"))));
            List<Choice> exact=new ArrayList<>();
            for(int losing=0;losing<target;losing++) {
                exact.add(c(target+"_"+losing,home+" "+target+" × "+losing+" "+away));
                exact.add(c(losing+"_"+target,home+" "+losing+" × "+target+" "+away));
            }
            result.add(d("SERIES_SCORE"+suffix,"Placar exato da série",Strategy.EXACT_SCORE,null,timing,exact));
            if(handicap() && !live) result.add(d("MAP_HANDICAP","Handicap de "+unit()+" · "+home+" −1,5",Strategy.HANDICAP,
                    new BigDecimal("-1.5"),timing,List.of(c("HOME",home),c("AWAY",away))));
            if(teamAtLeastOne()) {
                result.add(d("HOME_MAP"+suffix,home+" vence pelo menos um mapa",Strategy.HOME_TOTAL,new BigDecimal("0.5"),timing,List.of(c("OVER","Sim"),c("UNDER","Não"))));
                result.add(d("AWAY_MAP"+suffix,away+" vence pelo menos um mapa",Strategy.AWAY_TOTAL,new BigDecimal("0.5"),timing,List.of(c("OVER","Sim"),c("UNDER","Não"))));
            }
            return result;
        }
    }
    private static final class CounterStrikeMarketStrategy extends SeriesStrategy {
        String unit(){return "mapas";} boolean teamAtLeastOne(){return true;} boolean handicap(){return true;}
    }
    private static final class ValorantMarketStrategy extends SeriesStrategy {
        String unit(){return "mapas";} boolean teamAtLeastOne(){return true;} boolean handicap(){return false;}
    }
    private static final class LeagueOfLegendsMarketStrategy extends SeriesStrategy {
        String unit(){return "jogos";} boolean teamAtLeastOne(){return false;} boolean handicap(){return false;}
    }
    private static Choice c(String key,String label){return new Choice(key,label,new BigDecimal("2.00"));}
    private static Definition d(String code,String name,Strategy strategy,BigDecimal line,MarketTimingMode timing,List<Choice> choices) {
        return new Definition(code,name,"Série",strategy,"score",line,timing,List.copyOf(choices),List.of(),
                "Mercado de previsão do ArenaPredict, liquidado pelo resultado final oficial da série. Multiplicadores internos de pontos virtuais; não são odds da PandaScore. Em cancelamento, os pontos são devolvidos.");
    }
}
