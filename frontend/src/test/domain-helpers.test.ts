import { describe, expect, it } from "vitest";
import { marketSettlementAvailability } from "../pages/AdminPages";
import { localDashboardLevelProgress, normalizeDashboardProgression } from "../pages/DashboardPage";
import { filterEventCatalog } from "../pages/EventsPage";
import { predictionMatchesFilter } from "../pages/PredictionsPage";
import { eventParticipantViews, isMultiParticipantEvent } from "../components/EventCard";
import { MAXIMUM_STAKE_POINTS, validatePredictionStake } from "../components/PredictionComposer";
import type { ArenaEvent } from "../types";

describe("filtros de palpites", () => {
  it.each(["ACTIVE", "ATIVO", "PENDING", "PENDENTE"])(
    "agrupa %s entre os palpites ativos",
    (status) => expect(predictionMatchesFilter(status, "ACTIVE")).toBe(true),
  );

  it.each(["WON", "VENCEDOR"])(
    "agrupa %s entre os palpites vencedores",
    (status) => expect(predictionMatchesFilter(status, "WON")).toBe(true),
  );

  it.each(["LOST", "PERDEDOR", "REFUNDED", "REEMBOLSADO"])(
    "agrupa %s entre os palpites encerrados sem vitória",
    (status) => expect(predictionMatchesFilter(status, "LOST")).toBe(true),
  );

  it("mantém cancelados separados e aceita o filtro geral", () => {
    expect(predictionMatchesFilter("CANCELADO", "CANCELLED")).toBe(true);
    expect(predictionMatchesFilter("ACTIVE", "CANCELLED")).toBe(false);
    expect(predictionMatchesFilter(undefined, "ACTIVE")).toBe(false);
    expect(predictionMatchesFilter(undefined, "")).toBe(true);
  });
});

describe("segurança operacional da liquidação", () => {
  const readyMarket = {
    id: 44,
    status: "CLOSED",
    eventStatus: "FINISHED",
    options: [{ key: "HOME", active: true }, { key: "AWAY", active: true }],
  };

  it("só libera um mercado fechado, com evento encerrado e opção ativa", () => {
    expect(marketSettlementAvailability(readyMarket)).toEqual({
      allowed: true,
      label: "Disponível",
      reason: "Mercado pronto para liquidação.",
    });
    expect(marketSettlementAvailability({
      ...readyMarket,
      eventStatus: undefined,
      event: { status: "FINISHED" },
    })).toMatchObject({ allowed: true });
  });

  it("impede uma segunda liquidação e explica o bloqueio", () => {
    expect(marketSettlementAvailability({ ...readyMarket, status: "SETTLED" })).toEqual({
      allowed: false,
      label: "Finalizado",
      reason: "Este mercado já foi liquidado.",
    });
  });

  it.each([
    [{ ...readyMarket, status: "OPEN" }, "Aguardando fechamento"],
    [{ ...readyMarket, eventStatus: "LIVE" }, "Evento em andamento"],
    [{ ...readyMarket, options: [{ key: "HOME", active: false }] }, "Sem opções"],
    [{ ...readyMarket, status: "CANCELLED" }, "Cancelado"],
  ])("bloqueia estados operacionais inválidos", (market, label) => {
    expect(marketSettlementAvailability(market)).toMatchObject({ allowed: false, label });
  });
});

describe("normalização da experiência do participante", () => {
  it("preserva nível legado e impede progresso negativo ou inválido", () => {
    expect(normalizeDashboardProgression({
      level: "Especialista",
      xp: -25,
      nextLevelXp: Number.NaN,
    })).toEqual({
      level: 1,
      levelTitle: "Especialista",
      xp: 0,
      nextLevelXp: 0,
    });
  });

  it("normaliza o nível numérico para um inteiro válido", () => {
    expect(normalizeDashboardProgression({
      level: 4.8,
      levelTitle: "Estrategista",
      xp: 760,
      nextLevelXp: 1_000,
    })).toEqual({
      level: 4,
      levelTitle: "Estrategista",
      xp: 760,
      nextLevelXp: 1_000,
    });
  });

  it("calcula o progresso dentro do nível usando limiares absolutos", () => {
    expect(localDashboardLevelProgress({ level: 2, levelTitle: "Competidor", xp: 6_000, nextLevelXp: 10_000 })).toEqual({
      currentXp: 1_000,
      neededXp: 5_000,
      remainingXp: 4_000,
    });
  });
});

describe("contrato de palpites virtuais", () => {
  it("aplica no cliente o mesmo teto de 20 mil pontos do backend", () => {
    expect(MAXIMUM_STAKE_POINTS).toBe(20_000);
    expect(validatePredictionStake(20_000, 10, 50_000)).toBeNull();
    expect(validatePredictionStake(20_001, 10, 50_000)).toContain("máximo por palpite");
    expect(validatePredictionStake(500, 10, 100)).toContain("saldo");
  });
});

describe("eventos com múltiplos participantes", () => {
  const race: ArenaEvent = {
    id: 90,
    title: "Final dos 100 metros",
    startsAt: "2026-08-25T20:00:00Z",
    status: "LIVE",
    format: "RACE",
    participants: [
      { id: 1, competitor: { id: 11, name: "Ana Silva", code: "ANA" }, displayOrder: 1, position: 2, scoreLabel: "10s12" },
      { id: 2, competitor: { id: 12, name: "Bia Souza", code: "BIA" }, displayOrder: 0, position: 1, scoreLabel: "10s05" },
      { id: 3, competitor: { id: 13, name: "Clara Lima", code: "CLA" }, displayOrder: 2, position: 3, scoreLabel: "10s20" },
    ],
  };

  it("detecta o formato e mantém a classificação completa ordenada", () => {
    expect(isMultiParticipantEvent(race)).toBe(true);
    expect(eventParticipantViews(race).map((item) => [item.position, item.competitor.name, item.scoreLabel])).toEqual([
      [1, "Bia Souza", "10s05"],
      [2, "Ana Silva", "10s12"],
      [3, "Clara Lima", "10s20"],
    ]);
  });
});

describe("busca do catálogo de eventos", () => {
  const events: ArenaEvent[] = [
    {
      id: 1,
      title: "FURIA vs NAVI",
      sport: { id: 1, name: "Counter-Strike 2" },
      championship: { id: 1, name: "Arena Masters" },
      homeCompetitor: { name: "FURIA", code: "FUR" },
      awayCompetitor: { name: "NAVI", code: "NAV" },
      startsAt: "2026-08-24T20:00:00Z",
      status: "OPEN_FOR_PREDICTIONS",
      featured: true,
    },
    {
      id: 2,
      title: "Time Azul vs Time Verde",
      sport: { id: 2, name: "Futebol" },
      championship: { id: 2, name: "Copa Nacional" },
      startsAt: "2026-08-25T20:00:00Z",
      status: "SCHEDULED",
      featured: false,
    },
  ];

  it("encontra por participante, modalidade e campeonato sem diferenciar caixa", () => {
    expect(filterEventCatalog(events, "furia", false).map((event) => event.id)).toEqual([1]);
    expect(filterEventCatalog(events, "COUNTER-STRIKE", false).map((event) => event.id)).toEqual([1]);
    expect(filterEventCatalog(events, "copa nacional", false).map((event) => event.id)).toEqual([2]);
  });

  it("combina busca textual e destaque sem alterar a coleção original", () => {
    expect(filterEventCatalog(events, "", true).map((event) => event.id)).toEqual([1]);
    expect(filterEventCatalog(events, "time azul", true)).toEqual([]);
    expect(events).toHaveLength(2);
  });
});
