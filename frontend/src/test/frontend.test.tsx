import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { eventStatusLabel, eventTeams, getWalletBalance, predictionStatusLabel } from "../app/format";
import { Brand } from "../components/Brand";
import { isPredictionOpen } from "../components/EventCard";
import { adminApi, ApiError, authApi, poolsApi, sessionStorage } from "../services/api";

function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ "content-type": "application/json" }),
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as Response;
}

describe("ArenaPredict frontend", () => {
  beforeEach(() => {
    localStorage.clear();
    window.sessionStorage.clear();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("renderiza a marca desacoplada da configuração do produto", () => {
    render(<Brand />);
    expect(screen.getByLabelText("ArenaPredict")).toBeInTheDocument();
  });

  it("persiste e recupera a sessão autenticada", () => {
    sessionStorage.save({
      token: "jwt-demo",
      userId: 7,
      name: "Jogador Arena",
      email: "jogador@arenapredict.com",
      role: "PARTICIPANTE",
    });
    expect(sessionStorage.read()).toMatchObject({ token: "jwt-demo", role: "PARTICIPANTE", userId: 7 });
  });

  it("normaliza eventos no formato atual do backend", () => {
    const [home, away] = eventTeams({
      homeCompetitor: { name: "FURIA", code: "FUR" },
      awayCompetitor: { name: "NAVI", code: "NAV" },
    });
    expect(home.name).toBe("FURIA");
    expect(away.code).toBe("NAV");
  });

  it("traduz estados e calcula saldo virtual sem valor monetário", () => {
    expect(eventStatusLabel("LIVE")).toBe("Ao vivo");
    expect(predictionStatusLabel("WON")).toBe("Vencedor");
    expect(getWalletBalance({ balance: 12_450 })).toBe(12_450);
  });

  it("consulta cada recurso administrativo no endpoint protegido correspondente", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal("fetch", fetchMock);

    const resources = ["users", "pools", "scoring-rules", "reports", "audit", "settings", "moderation", "achievements", "challenges", "notifications"];
    for (const resource of resources) await adminApi.list(resource, { page: 0, size: 25 });

    const requestedPaths = fetchMock.mock.calls.map(([url]) => String(url).replace(/^https?:\/\/[^/]+/, ""));
    expect(requestedPaths).toEqual([
      "/api/admin/users?page=0&size=25",
      "/api/admin/pools?page=0&size=25",
      "/api/admin/scoring-rules?page=0&size=25",
      "/api/admin/reports?page=0&size=25",
      "/api/admin/audit?page=0&size=25",
      "/api/admin/settings?page=0&size=25",
      "/api/admin/moderation?page=0&size=25",
      "/api/admin/achievements?page=0&size=25",
      "/api/admin/challenges?page=0&size=25",
      "/api/admin/notifications?page=0&size=25",
    ]);
  });

  it("solicita acesso demo por perfil sem enviar credenciais ou o token antigo", async () => {
    sessionStorage.save({
      token: "old-session-token",
      userId: 99,
      name: "Sessão anterior",
      email: "anterior@arenapredict.com",
      role: "PARTICIPANTE",
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ token: "admin-token", userId: 1, name: "Admin", email: "admin@arenapredict.com", role: "ADMIN" }))
      .mockResolvedValueOnce(jsonResponse({ token: "player-token", userId: 2, name: "Jogador", email: "jogador@arenapredict.com", role: "PARTICIPANTE" }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(authApi.demo("ADMIN")).resolves.toMatchObject({ role: "ADMIN" });
    await expect(authApi.demo("PARTICIPANT")).resolves.toMatchObject({ role: "PARTICIPANTE" });
    expect(fetchMock.mock.calls.map(([url]) => String(url).replace(/^https?:\/\/[^/]+/, ""))).toEqual([
      "/api/auth/demo",
      "/api/auth/demo",
    ]);
    expect(fetchMock.mock.calls.map((call) => JSON.parse(String(call[1]?.body)))).toEqual([
      { profile: "ADMIN" },
      { profile: "PARTICIPANT" },
    ]);
    expect(fetchMock.mock.calls.every((call) => !(call[1]?.headers as Headers).has("Authorization"))).toBe(true);
  });

  it("mantém falha 403 do endpoint demo local ao formulário", async () => {
    const forbidden = vi.fn();
    window.addEventListener("arena:forbidden", forbidden);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({ message: "negado" }, 403)));

    try {
      await expect(authApi.demo("ADMIN")).rejects.toMatchObject({ status: 403 } satisfies Partial<ApiError>);
      expect(forbidden).not.toHaveBeenCalled();
    } finally {
      window.removeEventListener("arena:forbidden", forbidden);
    }
  });

  it("rejeita perfil inesperado retornado pela autenticação", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({
      token: "unexpected-token",
      userId: 3,
      name: "Perfil inválido",
      email: "invalido@arenapredict.com",
      role: "SUPER_ADMIN",
    })));

    await expect(authApi.demo("ADMIN")).rejects.toMatchObject({
      status: 502,
      code: "INVALID_AUTH_ROLE",
      message: "O servidor retornou um perfil de acesso inválido.",
    } satisfies Partial<ApiError>);
  });

  it("distingue sessão inválida de falta de permissão", async () => {
    const unauthorized = vi.fn();
    const forbidden = vi.fn();
    window.addEventListener("arena:unauthorized", unauthorized);
    window.addEventListener("arena:forbidden", forbidden);
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ message: "token inválido" }, 401))
      .mockResolvedValueOnce(jsonResponse({ message: "negado" }, 403));
    vi.stubGlobal("fetch", fetchMock);

    await expect(adminApi.dashboard()).rejects.toMatchObject({ status: 401 } satisfies Partial<ApiError>);
    await expect(adminApi.dashboard()).rejects.toMatchObject({ status: 403 } satisfies Partial<ApiError>);
    expect(unauthorized).toHaveBeenCalledTimes(1);
    expect(forbidden).toHaveBeenCalledTimes(1);
    window.removeEventListener("arena:unauthorized", unauthorized);
    window.removeEventListener("arena:forbidden", forbidden);
  });

  it("substitui mensagens técnicas ou validações em inglês por copy segura", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      message: "Validation failed",
      fieldErrors: { name: "must not be blank" },
    }, 400));
    vi.stubGlobal("fetch", fetchMock);

    await expect(adminApi.create("sports", {})).rejects.toMatchObject({
      message: "Revise os dados informados.",
      fieldErrors: { name: "Valor inválido." },
    } satisfies Partial<ApiError>);
  });

  it("não exibe enums internos recebidos em mensagens de erro", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      message: "O evento está OPEN_FOR_PREDICTIONS.",
    }, 400));
    vi.stubGlobal("fetch", fetchMock);

    await expect(adminApi.create("sports", {})).rejects.toMatchObject({
      message: "Revise os dados informados.",
    } satisfies Partial<ApiError>);
  });

  it("cria liga recorrente com regras e pontos exclusivamente virtuais", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 8, name: "Liga Teste", poolType: "LEAGUE", recurring: true }));
    vi.stubGlobal("fetch", fetchMock);
    await poolsApi.create({ name: "Liga Teste", privacy: "PUBLIC", poolType: "LEAGUE", recurring: true, rules: "Pontos por acerto", virtualPrizePoints: 500 });
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toMatchObject({
      poolType: "LEAGUE", recurring: true, publicPool: true, rules: "Pontos por acerto", virtualPrizePoints: 500,
    });
  });

  it("só habilita palpites em evento aberto e antes do limite", () => {
    expect(isPredictionOpen({ id: 1, startsAt: "2099-01-01T00:00:00Z", predictionClosesAt: "2098-12-31T23:00:00Z", status: "OPEN_FOR_PREDICTIONS" })).toBe(true);
    expect(isPredictionOpen({ id: 2, startsAt: "2099-01-01T00:00:00Z", predictionClosesAt: "2098-12-31T23:00:00Z", status: "LIVE" })).toBe(false);
    expect(isPredictionOpen({ id: 3, startsAt: "2020-01-01T00:00:00Z", predictionClosesAt: "2020-01-01T00:00:00Z", status: "OPEN_FOR_PREDICTIONS" })).toBe(false);
  });

  it("mantém placar, classificação e liquidação como operações separadas", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ id: 9, homeScore: 2, awayScore: 1 }))
      .mockResolvedValueOnce(jsonResponse({ id: 10, status: "FINISHED" }))
      .mockResolvedValueOnce(jsonResponse({
        marketId: 44,
        correctOptionKey: "HOME",
        winners: 2,
        losers: 1,
        rewardedPoints: 360,
        alreadySettled: false,
      }));
    vi.stubGlobal("fetch", fetchMock);

    await adminApi.recordEventResult(9, { homeScore: 2, awayScore: 1, finishEvent: true }, "result-operation-key");
    await adminApi.recordEventClassification(10, {
      participants: [
        { competitorId: 7, displayOrder: 0, position: 1, scoreLabel: "1h32min" },
        { competitorId: 8, displayOrder: 1, position: 2, scoreLabel: "+4s" },
      ],
      finishEvent: true,
    }, "classification-operation-key");
    await adminApi.settleMarket(44, "HOME");

    expect(String(fetchMock.mock.calls[0][0])).toMatch(/\/api\/admin\/events\/9\/result$/);
    expect(fetchMock.mock.calls[0][1]).toMatchObject({
      method: "PUT",
      body: JSON.stringify({ homeScore: 2, awayScore: 1, finishEvent: true }),
    });
    expect((fetchMock.mock.calls[0][1]?.headers as Headers).get("Idempotency-Key")).toBe("result-operation-key");
    expect(String(fetchMock.mock.calls[1][0])).toMatch(/\/api\/admin\/events\/10\/classification$/);
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method: "PUT",
      body: JSON.stringify({
        participants: [
          { competitorId: 7, displayOrder: 0, position: 1, scoreLabel: "1h32min" },
          { competitorId: 8, displayOrder: 1, position: 2, scoreLabel: "+4s" },
        ],
        finishEvent: true,
      }),
    });
    expect((fetchMock.mock.calls[1][1]?.headers as Headers).get("Idempotency-Key")).toBe("classification-operation-key");
    expect(String(fetchMock.mock.calls[2][0])).toMatch(/\/api\/admin\/markets\/44\/settle$/);
    expect(fetchMock.mock.calls[2][1]).toMatchObject({
      method: "POST",
      body: JSON.stringify({ correctOptionKey: "HOME" }),
    });
  });
});
