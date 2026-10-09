import { describe, expect, it } from "vitest";
import { seriesProgressSummary } from "../app/pricingPresentation";
import type { ArenaEvent } from "../types";

const live = { id: 1, status: "LIVE", liveScoreAvailable: true, bestOf: 3, homeScore: 1, awayScore: 0,
  homeCompetitor: { name: "Equipe A" }, awayCompetitor: { name: "Equipe B" } } as ArenaEvent;

describe("explicação do estado oficial da série", () => {
  it("explica a vantagem confirmada respeitando o formato", () => {
    expect(seriesProgressSummary(live)).toBe("Equipe A lidera a série e precisa de 1 vitória para encerrá-la.");
    expect(seriesProgressSummary({ ...live, bestOf: 5, homeScore: 0, awayScore: 1 })).toBe("Equipe B lidera a série e precisa de 2 vitórias para encerrá-la.");
  });
  it("não inventa vantagem com estado ausente, empatado, terminado ou formato desconhecido", () => {
    expect(seriesProgressSummary({ ...live, liveScoreAvailable: false })).toBeUndefined();
    expect(seriesProgressSummary({ ...live, awayScore: 1 })).toBeUndefined();
    expect(seriesProgressSummary({ ...live, homeScore: 2 })).toBeUndefined();
    expect(seriesProgressSummary({ ...live, bestOf: undefined })).toBeUndefined();
    expect(seriesProgressSummary({ ...live, status: "FINISHED" })).toBeUndefined();
  });
});
