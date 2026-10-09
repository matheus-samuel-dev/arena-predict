import com.bolao.copa.arena.service.*;
import com.bolao.copa.arena.service.MarketDefinitionCatalog.*;
import com.bolao.copa.arena.domain.ArenaEnums.MarketTimingMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Offline retrospective comparison only. No credentials, network, database or production writes. */
public class ProbabilityAuditProbe {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final Definition WINNER = new Definition("WINNER", "Winner", "Series", Strategy.WINNER, "score", null,
            MarketTimingMode.PRE_MATCH_ONLY, List.of(), List.of(), "audit");

    public static void main(String[] args) throws Exception {
        var persisted = read(args[0]);
        var expanded = read(args[1]);
        var output = new ArrayList<Map<String, Object>>();
        for (String sport : List.of("CS2", "LEAGUE_OF_LEGENDS", "VALORANT")) {
            var sorted = persisted.stream().filter(r -> sport.equals(r.sport()) && validTimeline(r))
                    .sorted(Comparator.comparing(BradleyTerryStrengthModel.Result::startsAt)).toList();
            var targets = sorted.subList(Math.max(sorted.size() / 2, sorted.size() - 50), sorted.size());
            output.add(evaluate(sport, "v1-neutral", targets, List.of(), 30));
            output.add(evaluate(sport, "v2-persisted-30d", targets, persisted, 30));
            output.add(evaluate(sport, "research-expanded-30d", targets, expanded, 30));
            output.add(evaluate(sport, "research-expanded-90d", targets, expanded, 90));
        }
        Files.writeString(Path.of(args[2]), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(output));
    }

    static List<BradleyTerryStrengthModel.Result> read(String file) throws Exception {
        return Arrays.asList(JSON.readValue(Files.readString(Path.of(file)).replace("\uFEFF", ""), BradleyTerryStrengthModel.Result[].class));
    }

    static boolean validTimeline(BradleyTerryStrengthModel.Result r) {
        return BradleyTerryStrengthModel.valid(r) && r.startsAt() != null && !r.startsAt().isAfter(r.finishedAt());
    }

    static Map<String, Object> evaluate(String sport, String variant, List<BradleyTerryStrengthModel.Result> targets,
            List<BradleyTerryStrengthModel.Result> history, int days) {
        double brier = 0, loss = 0;
        int rated = 0, observed = 0;
        int[] bins = new int[5], wins = new int[5];
        double[] predicted = new double[5];
        for (var target : targets) {
            Instant at = target.startsAt();
            var prior = history.stream().filter(r -> sport.equals(r.sport()) && validTimeline(r)
                    && !r.id().equals(target.id()) && r.finishedAt().isBefore(at)
                    && !r.finishedAt().isBefore(at.minusSeconds(days * 86400L))).toList();
            var sameFormat = prior.stream().filter(r -> r.bestOf() == target.bestOf()).toList();
            var estimate = new BradleyTerryStrengthModel(sameFormat, at, 4, 14).estimate(target.home(), target.away(), 5);
            if ("SYMMETRIC_PRIOR".equals(estimate.source())) {
                estimate = new BradleyTerryStrengthModel(prior, at, 4, 14).estimate(target.home(), target.away(), 5);
            }
            if (!"SYMMETRIC_PRIOR".equals(estimate.source())) rated++;
            if (prior.stream().anyMatch(r -> r.confirmedAt() != null && !r.confirmedAt().isAfter(at))) observed++;
            double p = SeriesOutcomeModel.probability(WINNER, "HOME", SeriesOutcomeModel.scores(target.bestOf(), 0, 0,
                    estimate.mapProbability(), estimate.mapProbability()));
            int truth = target.homeScore() > target.awayScore() ? 1 : 0;
            brier += (p - truth) * (p - truth);
            loss -= truth * Math.log(Math.max(1e-12, p)) + (1 - truth) * Math.log(Math.max(1e-12, 1 - p));
            int bin = Math.min(4, (int) (p * 5));
            bins[bin]++; wins[bin] += truth; predicted[bin] += p;
        }
        var reliability = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < bins.length; i++) if (bins[i] > 0) {
            reliability.add(Map.of("bin", i, "n", bins[i], "meanProbability", predicted[i] / bins[i], "winFrequency", (double) wins[i] / bins[i]));
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("sport", sport); report.put("variant", variant); report.put("n", targets.size());
        report.put("historyBased", rated); report.put("coverage", targets.isEmpty() ? 0 : (double) rated / targets.size());
        report.put("brier", targets.isEmpty() ? null : brier / targets.size());
        report.put("logLoss", targets.isEmpty() ? null : loss / targets.size());
        report.put("reliabilityBins", reliability); report.put("targetsWithSomeLocallyObservedPrior", observed);
        report.put("scope", "RETROSPECTIVE_ONLY: earlier official finishes, same target set; historical availability/rosters unverified; not calibrated or independent prospective validation.");
        return report;
    }
}
