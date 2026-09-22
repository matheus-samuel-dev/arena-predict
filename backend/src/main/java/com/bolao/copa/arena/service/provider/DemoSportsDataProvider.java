package com.bolao.copa.arena.service.provider;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = {"app.demo.enabled", "app.demo.live-provider-enabled"}, havingValue = "true")
public class DemoSportsDataProvider implements SportsDataProvider, EsportsDataProvider {
    private static final int MAX_LIVE_STEP = 6;
    private final AtomicInteger tick = new AtomicInteger();
    @Override public String providerName() { return "ArenaPredict Internal Demo Provider"; }
    @Override public boolean demo() { return true; }
    @Override public List<LiveEventUpdate> liveUpdates() {
        // A demo process can stay online for days. Saturating the simulation
        // prevents impossible counters and also avoids scores going backwards,
        // which would happen with a simple modulo-based cycle.
        int step = tick.getAndUpdate(value -> value >= MAX_LIVE_STEP ? MAX_LIVE_STEP : value + 1);
        int minute = 83 + step;
        int footballHome = 3;
        int csRounds = 9 + Math.min(3, step / 2);
        return List.of(
                new LiveEventUpdate("demo-football-live", footballHome, 1, minute + ":24", "2º tempo", "{\"demo\":true,\"shots\":[" + (12 + step) + ",8],\"possession\":[54,46],\"corners\":[5,4],\"addedTimeMinutes\":5}"),
                new LiveEventUpdate("demo-cs2-live", 1, 0, "Mapa 2", "Inferno · " + csRounds + "-7", "{\"demo\":true,\"maps\":[{\"name\":\"Mirage\",\"score\":\"13-8\"},{\"name\":\"Inferno\",\"score\":\"" + csRounds + "-7\"}]}"));
    }
}
