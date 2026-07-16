package com.bolao.copa.arena.service.provider;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo.live-provider-enabled", havingValue = "true", matchIfMissing = true)
public class DemoSportsDataProvider implements SportsDataProvider, EsportsDataProvider {
    private final AtomicInteger tick = new AtomicInteger();
    @Override public String providerName() { return "ArenaPredict Internal Demo Provider"; }
    @Override public boolean demo() { return true; }
    @Override public List<LiveEventUpdate> liveUpdates() {
        int step = tick.getAndIncrement();
        int minute = 76 + (step % 14);
        int footballHome = Math.min(4, 2 + step / 4);
        int csRounds = 9 + (step % 4);
        return List.of(
                new LiveEventUpdate("demo-football-live", footballHome, 1, minute + ":24", "2º tempo", "{\"demo\":true,\"shots\":[" + (12 + step) + ",8],\"possession\":[54,46]}"),
                new LiveEventUpdate("demo-cs2-live", 1, 0, "Mapa 2", "Inferno · " + csRounds + "-7", "{\"demo\":true,\"maps\":[{\"name\":\"Mirage\",\"score\":\"13-8\"},{\"name\":\"Inferno\",\"score\":\"" + csRounds + "-7\"}]}"));
    }
}
