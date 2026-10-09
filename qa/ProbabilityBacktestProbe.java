import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.MarketDefinitionCatalog.*;
import com.bolao.copa.arena.domain.ArenaEnums.MarketTimingMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Manual, offline temporal evaluation of normalized official results. Not a production/CI provider call. */
public class ProbabilityBacktestProbe {
    public static void main(String[] args) throws Exception {
        var json=new ObjectMapper().findAndRegisterModules();
        var rows=Arrays.asList(json.readValue(Files.readString(Path.of(args[0])),BradleyTerryStrengthModel.Result[].class));
        var reports=new ArrayList<Map<String,Object>>();
        var winner=new Definition("WINNER","Winner","Series",Strategy.WINNER,"score",null,MarketTimingMode.PRE_MATCH_ONLY,
                List.of(new Choice("HOME","Home",java.math.BigDecimal.valueOf(2)),new Choice("AWAY","Away",java.math.BigDecimal.valueOf(2))),List.of(),"test");
        for(String sport:List.of("CS2","VALORANT","LEAGUE_OF_LEGENDS")) {
            var matches=rows.stream().filter(r->sport.equals(r.sport())&&BradleyTerryStrengthModel.valid(r)&&r.startsAt()!=null&&!r.startsAt().isAfter(r.finishedAt()))
                    .sorted(Comparator.comparing(BradleyTerryStrengthModel.Result::startsAt).thenComparing(BradleyTerryStrengthModel.Result::id)).toList();
            int from=Math.max(1,matches.size()/2),rated=0,observed=0,n=0;double brier=0,loss=0,base=0;
            for(int i=from;i<matches.size();i++) {
                var target=matches.get(i);Instant at=target.startsAt();
                var prior=matches.stream().filter(r->!r.id().equals(target.id())&&r.finishedAt().isBefore(at)&&!r.finishedAt().isBefore(at.minusSeconds(30*86400L))).toList();
                var model=new BradleyTerryStrengthModel(prior,at,4,14);var estimate=model.estimate(target.home(),target.away(),5);
                if(!"SYMMETRIC_PRIOR".equals(estimate.source()))rated++;
                if(prior.stream().anyMatch(r->r.confirmedAt()!=null&&!r.confirmedAt().isAfter(at)))observed++;
                double p=SeriesOutcomeModel.probability(winner,"HOME",SeriesOutcomeModel.scores(target.bestOf(),0,0,estimate.mapProbability(),estimate.mapProbability()));
                double truth=target.homeScore()>target.awayScore()?1:0;
                brier+=(p-truth)*(p-truth);base+=.25;loss-=truth*Math.log(Math.max(1e-12,p))+(1-truth)*Math.log(Math.max(1e-12,1-p));n++;
            }
            var report=new LinkedHashMap<String,Object>();report.put("sport",sport);report.put("archivedMatches",matches.size());report.put("holdoutMatches",n);
            report.put("historyBasedPredictions",rated);report.put("targetsWithAtLeastOneResultObservedByOurSystem",observed);
            report.put("brier",n==0?null:brier/n);report.put("neutralBrier",n==0?null:base/n);report.put("logLoss",n==0?null:loss/n);
            report.put("calibrated",false);report.put("note","Retrospective archive; only earlier finished matches train each target. No claim these forecasts were issued then or that historical lineups were verified.");reports.add(report);
        }
        Files.writeString(Path.of(args[1]),json.writerWithDefaultPrettyPrinter().writeValueAsString(reports));
    }
}
