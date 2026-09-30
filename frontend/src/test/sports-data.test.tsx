import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { dateTime } from "../app/format";
import { eventFormatLabel, eventScore, eventSourceLabel, isExternalEvent } from "../app/sportsData";
import { EventCard, FeaturedEventCard } from "../components/EventCard";
import { EventDataSource } from "../components/EventDataSource";
import { SportsSyncSummary } from "../components/SportsSyncSummary";
import { filterEventCatalog, LiveEventPanel } from "../pages/EventsPage";
import { eventsApi } from "../services/api";
import type { ArenaEvent } from "../types";

const real: ArenaEvent = {
  id: 42, status: "LIVE", externalProvider: "PANDASCORE", externalId: "938291", demo: false,
  startsAt: "2026-09-24T21:00:00Z", sport: "Counter-Strike 2", championship: "IEM Cologne",
  homeCompetitor: { name: "Team Liquid", logoUrl: "https://cdn.example.com/liquid.png" },
  awayCompetitor: { name: "Natus Vincere", logoUrl: "https://cdn.example.com/navi.png" },
  homeScore: null, awayScore: null, bestOf: null, liveScoreAvailable: false,
};

function renderCard(event: ArenaEvent) {
  return render(<MemoryRouter><EventCard event={event} /></MemoryRouter>);
}

describe("dados esportivos reais na interface", () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); });

  it.each([null, undefined, ""])("não transforma score ausente (%s) em zero", (score) => {
    const event = { ...real, liveScoreAvailable: true, homeScore: score, awayScore: score };
    expect(eventScore(event)).toBeNull();
    renderCard(event);
    expect(screen.getByText("Placar ao vivo indisponível pelo provedor.")).toBeVisible();
    expect(screen.queryByLabelText(/Placar \d/)).not.toBeInTheDocument();
    expect(screen.getByText("PandaScore")).toBeVisible();
  });

  it("oculta um placar persistido quando o provedor informa indisponibilidade live", () => {
    expect(eventScore({ ...real, homeScore: 0, awayScore: 0 })).toBeNull();
    expect(eventScore({ ...real, homeScore: 0, awayScore: 0, liveScoreAvailable: true })).toEqual([0, 0]);
    expect(eventScore({ ...real, status: "FINISHED", homeScore: 2, awayScore: 1 })).toEqual([2, 1]);
  });

  it("mantém ausência de BO e usa somente o formato que foi fornecido", () => {
    expect(eventFormatLabel(real)).toBeNull();
    expect(eventFormatLabel({ ...real, format: "BO3" })).toBe("BO3");
    expect(eventFormatLabel({ ...real, format: "DEFAULT" })).toBeNull();
    expect(eventFormatLabel({ ...real, bestOf: 5 })).toBe("BO5");
    renderCard(real);
    expect(screen.queryByText(/^BO\d/)).not.toBeInTheDocument();
    expect(screen.queryByText("Melhor de 3")).not.toBeInTheDocument();
  });

  it.each(["STANDARD", "INDIVIDUAL", "RACE"])("preserva o formato %s de outras modalidades com bestOf legado", (format) => {
    expect(eventFormatLabel({ format, bestOf: 1, externalProvider: "DEMO" })).toBe(format);
    expect(eventFormatLabel({ format, bestOf: 1 })).toBe(format);
  });

  it("acompanha agendamento, início e encerramento com resultado processado", () => {
    const view = renderCard({ ...real, status: "SCHEDULED", bestOf: 3 });
    expect(screen.getByText("Agendado")).toBeVisible();
    expect(screen.getByText(dateTime(real.startsAt))).toBeVisible();
    view.rerender(<MemoryRouter><EventCard event={{ ...real, homeScore: 1, awayScore: 1, liveScoreAvailable: true, bestOf: 3 }} /></MemoryRouter>);
    expect(screen.getAllByText("Ao vivo").length).toBeGreaterThan(0);
    expect(screen.getByLabelText("Placar 1 a 1")).toBeVisible();
    view.rerender(<MemoryRouter><EventCard event={{ ...real, status: "FINISHED", homeScore: 2, awayScore: 1, resultProcessedAt: "2026-09-24T23:00:00Z" }} /></MemoryRouter>);
    expect(screen.getByLabelText("Placar 2 a 1")).toBeVisible();
    expect(screen.getByText("Resultado processado")).toBeVisible();
  });

  it("também preserva ausência de score nas visões ao vivo e em destaque", () => {
    render(<MemoryRouter><LiveEventPanel event={real} onPredict={vi.fn()} /><FeaturedEventCard event={real} /></MemoryRouter>);
    expect(screen.getAllByText("Placar ao vivo indisponível pelo provedor.")).toHaveLength(2);
    expect(screen.queryByLabelText("Placar 0 a 0")).not.toBeInTheDocument();
  });

  it("prioriza logo real e substitui falha de carregamento pelo fallback local", () => {
    const { container } = renderCard(real);
    const logo = container.querySelector('img[src="https://cdn.example.com/liquid.png"]');
    expect(logo).not.toBeNull();
    fireEvent.error(logo!);
    expect(logo).toHaveAttribute("src", "/assets/teams/team-liquid.svg");
    expect(screen.getByText("Natus Vincere")).toBeVisible();
  });

  it("identifica Demo e provedor real sem confundir um flag legado com a origem", () => {
    expect(eventSourceLabel({ demo: true })).toBe("Demo");
    expect(eventSourceLabel({ externalProvider: "demo" })).toBe("Demo");
    expect(eventSourceLabel({ demo: true, externalProvider: "PANDASCORE" })).toBe("PandaScore");
    expect(isExternalEvent({ externalProvider: " MANUAL " })).toBe(false);
    render(<EventDataSource event={{ ...real, externalProvider: "DEMO", demo: true }} />);
    expect(screen.getByText("Demo")).toBeVisible();
    expect(screen.queryByText("PandaScore")).not.toBeInTheDocument();
  });

  it("filtra reais e Demo sem classificar registros manuais como oficiais", () => {
    const demo = { ...real, id: 43, externalProvider: undefined, demo: true };
    const manual = { ...real, id: 44, externalProvider: undefined };
    expect(filterEventCatalog([real, demo, manual], "", false, "REAL")).toEqual([real]);
    expect(filterEventCatalog([real, demo, manual], "", false, "DEMO")).toEqual([demo]);
    expect(filterEventCatalog([real, demo, manual], "Team Liquid", false)).toHaveLength(3);
  });

  it("informa revisão sem anunciar que o resultado corrigido já foi processado", () => {
    render(<EventDataSource event={{ ...real, resultProcessedAt: "2026-09-24T23:00:00Z", resultReviewRequired: true }} />);
    expect(screen.getByText("Resultado sob revisão")).toBeVisible();
    expect(screen.queryByText("Resultado processado")).not.toBeInTheDocument();
  });

  it("usa o fuso local do navegador e aceita offsets ISO equivalentes", () => {
    const expected = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }).format(new Date(real.startsAt));
    expect(dateTime(real.startsAt)).toBe(expected);
    expect(dateTime("2026-09-24T18:00:00-03:00")).toBe(expected);
  });

  it("consulta somente Arena API e preserva campos opcionais nulos", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(real), { headers: { "content-type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    const event = await eventsApi.get(42);
    expect(String(fetchMock.mock.calls[0][0])).toMatch(/\/api\/events\/42$/);
    expect(eventScore(event)).toBeNull();
    expect(eventFormatLabel(event)).toBeNull();
  });

  it.each([
    ["UNCONFIGURED", "Token não configurado"],
    ["UNAVAILABLE", "Provedor temporariamente indisponível"],
    ["RATE_LIMITED", "Aguardando quota do provedor"],
  ])("expõe status administrativo %s a partir do backend", async (status, label) => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ provider: "PANDASCORE", status, enabled: true, configured: status !== "UNCONFIGURED", lastSuccessAt: "2026-09-24T20:00:00Z" }), { headers: { "content-type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    render(<SportsSyncSummary />);
    expect(await screen.findByText(label)).toBeVisible();
    expect(screen.getByText(/Última sincronização:/)).toBeVisible();
    await waitFor(() => expect(String(fetchMock.mock.calls[0][0])).toMatch(/\/api\/admin\/sports-sync\/status$/));
  });
});
