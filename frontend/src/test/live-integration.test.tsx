import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LiveEventsPage } from "../pages/EventsPage";
import { ToastProvider } from "../contexts/ToastContext";

const state=vi.hoisted(() => ({ admin:false }));
vi.mock("../contexts/AuthContext",() => ({ useAuth:() => ({ user:{ role:state.admin?"ADMIN":"PARTICIPANTE" } }) }));
vi.mock("../contexts/AppDataContext",() => ({ useAppData:() => ({ wallet:null,refreshWallet:vi.fn(),refreshNotifications:vi.fn() }) }));

const reply=(body:unknown) => new Response(JSON.stringify(body),{headers:{"content-type":"application/json"}});
const start=() => render(<MemoryRouter><ToastProvider><LiveEventsPage /></ToastProvider></MemoryRouter>);

describe("integração da central ao vivo",() => {
  beforeEach(() => {
    state.admin=false;
    Object.defineProperty(document,"visibilityState",{configurable:true,value:"visible"});
    Object.defineProperty(document,"hidden",{configurable:true,value:false});
  });
  afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals(); });

  it("distingue leitura do backend da última sincronização real confirmada",async () => {
    const fetchMock=vi.fn(async (url:RequestInfo | URL) => reply(String(url).endsWith("/sports-sync/status")
      ? {healthy:true,lastSuccessAt:"2026-10-05T12:00:00Z"}:[]));
    vi.stubGlobal("fetch",fetchMock);
    start();
    expect(await screen.findByText("A arena está em intervalo")).toBeVisible();
    expect(screen.getByText(/dados esportivos foram sincronizados com sucesso/)).toBeVisible();
    expect(screen.getByText(/Última leitura/)).toBeVisible();
    expect(fetchMock.mock.calls.every(([url]) => /\/api\/(events\/live|sports-sync\/status)$/.test(String(url)))).toBe(true);
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes("admin/sports-sync"))).toBe(false);
  });

  it("participante não recebe diagnóstico nem afirma sucesso quando o provider não está saudável",async () => {
    vi.stubGlobal("fetch",vi.fn(async (url:RequestInfo | URL) => reply(String(url).endsWith("/sports-sync/status")
      ? {healthy:false,lastSuccessAt:null}:[])));
    start();
    expect(await screen.findByText("A arena está em intervalo")).toBeVisible();
    expect(screen.queryByText(/sincronizados com sucesso|Token não configurado|UNCONFIGURED/)).not.toBeInTheDocument();
  });

  it("administrador vê o diagnóstico separado do empty state",async () => {
    state.admin=true;
    vi.stubGlobal("fetch",vi.fn(async (url:RequestInfo | URL) => {
      if(String(url).includes("admin/sports-sync/status")) return reply({provider:"PandaScore",status:"UNCONFIGURED",configured:false,enabled:true});
      return reply(String(url).endsWith("/sports-sync/status")?{healthy:false,lastSuccessAt:null}:[]);
    }));
    start();
    expect(await screen.findByText("Token não configurado")).toBeVisible();
    expect(screen.getByText("A arena está em intervalo")).toBeVisible();
  });

  it("evento LIVE real continua visível sem placar e sem mercados",async () => {
    const event={id:501,title:"Real fixture",externalProvider:"PANDASCORE",externalId:"901001",status:"LIVE",sportName:"CS2",championshipName:"Campeonato",
      homeCompetitor:{name:"Time A"},awayCompetitor:{name:"Time B"},homeScore:null,awayScore:null,markets:[],participants:[],demo:false};
    vi.stubGlobal("fetch",vi.fn(async (url:RequestInfo | URL) => reply(String(url).endsWith("/events/live")?[event]:{healthy:false,lastSuccessAt:null})));
    start();
    expect(await screen.findByText("Time A")).toBeVisible();
    expect(screen.getByText("Time B")).toBeVisible();
    expect(screen.getByText("Placar ao vivo indisponível pelo provedor.")).toBeVisible();
    expect(screen.queryByText("A arena está em intervalo")).not.toBeInTheDocument();
  });

  it("atualização manual e timer de 30 segundos não sobrepõem requests",async () => {
    let resolveRefresh:((response:Response) => void) | undefined;
    let calls=0;
    const fetchMock=vi.fn((url:RequestInfo | URL) => {
      if(String(url).endsWith("/events/live")) {
        calls++;
        if(calls===2) return new Promise<Response>(resolve => { resolveRefresh=resolve; });
        return Promise.resolve(reply([]));
      }
      return Promise.resolve(reply({healthy:false,lastSuccessAt:null}));
    });
    vi.stubGlobal("fetch",fetchMock);
    vi.useFakeTimers();
    start();
    await act(async () => { await vi.advanceTimersByTimeAsync(1); });
    expect(screen.getByText("A arena está em intervalo")).toBeVisible();
    fireEvent.click(screen.getByRole("button",{name:/Atualizar agora/}));
    expect(calls).toBe(2);
    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });
    expect(calls).toBe(2);
    await act(async () => { resolveRefresh?.(reply([])); await vi.advanceTimersByTimeAsync(0); });
    expect(screen.getByRole("button",{name:/Atualizar agora/})).toBeEnabled();
    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });
    expect(calls).toBe(3);
  });

  it("identifica as duas origens quando Real e Demo estão ao vivo",async () => {
    const base={status:"LIVE",sportName:"CS2",championshipName:"Campeonato",homeCompetitor:{name:"Time A"},awayCompetitor:{name:"Time B"},markets:[],participants:[]};
    const events=[{...base,id:701,title:"Evento real",externalProvider:"PANDASCORE",externalId:"901001",demo:false},
      {...base,id:702,title:"Evento Demo",demo:true,demoManaged:true}];
    vi.stubGlobal("fetch",vi.fn(async (url:RequestInfo | URL) => reply(String(url).endsWith("/events/live")?events:{healthy:false,lastSuccessAt:null})));
    start();
    expect(await screen.findByText("Demonstração")).toBeVisible();
    expect(screen.getByText("PandaScore")).toBeVisible();
    expect(screen.queryByText("A arena está em intervalo")).not.toBeInTheDocument();
  });
});
