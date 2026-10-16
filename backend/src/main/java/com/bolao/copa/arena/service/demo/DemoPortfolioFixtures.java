package com.bolao.copa.arena.service.demo;

import java.util.List;
import java.util.Map;

/** Explicit fictional results, never imported into the official sports archive. */
final class DemoPortfolioFixtures {
    private DemoPortfolioFixtures() { }
    record Selection(String template, String winningKey) { }
    record Fixture(String source, int daysAgo, int home, int away, Map<String, String> data, List<Selection> selections) { }
    static final List<Fixture> HISTORY = List.of(
            new Fixture("demo-football-open", 29, 2, 1, Map.of("cornersHome", "7", "cornersAway", "4"),
                    List.of(new Selection("WINNER", "HOME"), new Selection("TOTAL_GOALS", "OVER"), new Selection("TOTAL_CORNERS", "OVER"))),
            new Fixture("demo-nba-open", 20, 108, 103, Map.of(),
                    List.of(new Selection("WINNER", "HOME"), new Selection("TOTAL_POINTS", "UNDER"))),
            new Fixture("demo-tennis-open", 13, 2, 1, Map.of(),
                    List.of(new Selection("MATCH_WINNER", "HOME"), new Selection("TOTAL_SETS", "OVER"), new Selection("SET_SCORE", "2_1"))),
            new Fixture("demo-lol-open", 8, 1, 2, Map.of(),
                    List.of(new Selection("SERIES_WINNER", "AWAY"), new Selection("TOTAL_MAPS", "OVER"), new Selection("SERIES_SCORE", "1_2"))),
            new Fixture("demo-vct-open", 4, 2, 0, Map.of(),
                    List.of(new Selection("SERIES_WINNER", "HOME"), new Selection("TOTAL_MAPS", "UNDER"), new Selection("SERIES_SCORE", "2_0"))),
            new Fixture("demo-cs2-open", 2, 2, 1, Map.of(),
                    List.of(new Selection("SERIES_WINNER", "HOME"), new Selection("TOTAL_MAPS", "OVER"), new Selection("SERIES_SCORE", "2_1"))));
}
