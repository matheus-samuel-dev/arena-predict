package com.bolao.copa.arena.service.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DemoSportsDataProviderTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void longRunningDemoKeepsStatisticsBoundedAndMonotonic() throws Exception {
        DemoSportsDataProvider provider = new DemoSportsDataProvider();
        int previousShots = 0;
        int previousHomeScore = 0;
        int previousRounds = 0;

        for (int refresh = 0; refresh < 250; refresh++) {
            var updates = provider.liveUpdates();
            var football = updates.getFirst();
            var esports = updates.getLast();
            int shots = json.readTree(football.structuredData()).get("shots").get(0).asInt();
            int rounds = json.readTree(esports.structuredData()).get("maps").get(1).get("score")
                    .asText().transform(value -> Integer.parseInt(value.substring(0, value.indexOf('-'))));

            assertThat(shots).isBetween(previousShots, 25);
            assertThat(football.homeScore()).isBetween(previousHomeScore, 4);
            assertThat(rounds).isBetween(previousRounds, 13);
            previousShots = shots;
            previousHomeScore = football.homeScore();
            previousRounds = rounds;
        }

        var stable = provider.liveUpdates();
        assertThat(stable.getFirst().clock()).isEqualTo("89:24");
        assertThat(stable.getFirst().structuredData()).contains("\"shots\":[25,8]");
        assertThat(provider.liveUpdates()).isEqualTo(stable);
    }
}
