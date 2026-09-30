package com.bolao.copa.arena.api;

import static com.bolao.copa.arena.api.ArenaDtos.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class DemoScenarioDtos {
    private DemoScenarioDtos() { }
    public record Scenario(long generation, ChampionshipResponse championship, EventResponse event,
                           List<EventResponse> history, List<PredictionResponse> predictions,
                           List<RankingRow> ranking, boolean canManage, boolean canPredict,
                           Instant updatedAt, String notice) { }
    public record Result(@NotNull @Min(0) @Max(2) Integer homeScore, @NotNull @Min(0) @Max(2) Integer awayScore) { }
    public record Reset(@NotNull @Min(1) Long expectedGeneration) { }
}
