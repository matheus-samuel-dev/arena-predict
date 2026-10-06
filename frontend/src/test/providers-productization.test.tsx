import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { SportsSyncSummary } from "../components/SportsSyncSummary";
import { eventsApi } from "../services/api";
import { eventSourceLabel } from "../app/sportsData";
import { ParticipantList } from "../components/EventCard";
import type { ArenaEvent } from "../types";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
describe("providers reais preparados sem mascarar credenciais ausentes", () => {
  it("mostra diagnóstico por provider somente na área técnica", async () => {
    const status = { provider:"API-FOOTBALL", configured:false, enabled:false, status:"DISABLED", supportedSports:["FOOTBALL"], lastSuccessAt:null };
    vi.stubGlobal("fetch",vi.fn(async (url:RequestInfo | URL) => new Response(JSON.stringify(String(url).endsWith("/providers")
      ? [{id:"API_FOOTBALL",credentialVariable:"API_FOOTBALL_KEY",readiness:"READY_FOR_CREDENTIAL",capabilities:{FOOTBALL:["EVENTS"]},sync:status}]
      : {provider:"PandaScore",configured:false,enabled:true,status:"UNCONFIGURED"}),{headers:{"content-type":"application/json"}})));
    render(<SportsSyncSummary />);
    expect(await screen.findByText("Preparado · aguardando credencial")).toBeInTheDocument();
    expect(screen.getByText(/Configurar API_FOOTBALL_KEY no backend/)).toBeInTheDocument();
    expect(screen.queryByText("Online")).not.toBeInTheDocument();
  });
  it("preserva metadata de paginação e envia filtros ao backend", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({content:[],totalElements:77,totalPages:4,number:2}),{headers:{"content-type":"application/json"}}));
    vi.stubGlobal("fetch",fetch);
    const page = await eventsApi.page({page:2,size:24,source:"REAL",sport:"FOOTBALL",q:"River",championshipId:"12"});
    expect(page.totalElements).toBe(77);expect(page.totalPages).toBe(4);
    const url=new URL(String(fetch.mock.calls[0][0]),"http://localhost");
    expect(url.searchParams.get("page")).toBe("2");expect(url.searchParams.get("source")).toBe("REAL");
    expect(url.searchParams.get("q")).toBe("River");expect(url.searchParams.get("championshipId")).toBe("12");
  });
  it("identifica fornecedor sem confundir com Demo", () => {
    expect(eventSourceLabel({externalProvider:"API_TENNIS",demo:false})).toBe("API-Tennis");
    expect(eventSourceLabel({demo:true})).toBe("Demo");
  });
  it("não inventa dois pilotos em uma corrida real sem participantes", () => {
    render(<ParticipantList event={{id:7,startsAt:"2026-10-06T19:00:00Z",status:"LIVE",format:"RACE",externalProvider:"API_FORMULA1",participants:[],markets:[]} as ArenaEvent} />);
    expect(screen.getByText("Participantes ainda não informados pelo provedor.")).toBeInTheDocument();
    expect(screen.queryAllByRole("listitem")).toHaveLength(0);
  });
});
