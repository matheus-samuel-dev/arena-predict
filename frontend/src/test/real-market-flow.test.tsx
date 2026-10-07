import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LiveEventPanel, LiveEventsPage } from "../pages/EventsPage";
import { ToastProvider } from "../contexts/ToastContext";
import type { ArenaEvent } from "../types";

vi.mock("../contexts/AuthContext",()=>({useAuth:()=>({user:{role:"PARTICIPANTE"}})}));
vi.mock("../contexts/AppDataContext",()=>({useAppData:()=>({wallet:null,refreshWallet:vi.fn(),refreshNotifications:vi.fn()})}));
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
const base={id:901,startsAt:"2026-10-07T12:00:00Z",status:"LIVE",externalProvider:"PANDASCORE",externalId:"123",demo:false,
  sportName:"CS2",championshipName:"Competição",homeCompetitor:{name:"Equipe A"},awayCompetitor:{name:"Equipe B"},markets:[],availableMarketCount:0} as ArenaEvent;
describe("mercados virtuais reais",()=>{
  it("apresenta ausência legítima informada pelo domínio sem prometer publicação manual",()=>{
    render(<MemoryRouter><LiveEventPanel event={{...base,predictionAvailabilityLabel:"Mercados ao vivo indisponíveis para este evento"}} onPredict={vi.fn()} /></MemoryRouter>);
    expect(screen.getAllByText("Mercados ao vivo indisponíveis para este evento")).toHaveLength(2);
    expect(screen.queryByText("Mercados ainda não publicados")).not.toBeInTheDocument();
    expect(screen.queryByText("Explorar todos (0)")).not.toBeInTheDocument();
    expect(screen.getByRole("link",{name:"Ver detalhes"})).toBeVisible();
  });
  it("placar indisponível não esconde mercado LIVE publicado pelo backend",()=>{
    const event={...base,availableMarketCount:1,predictionAvailabilityLabel:"Aberto para palpites · 1 mercado",markets:[{
      id:1,name:"Vencedor da série",status:"OPEN",templateCode:"SERIES_WINNER_LIVE",timingMode:"LIVE_ONLY",
      availability:{allowed:true,code:"OPEN",label:"Aberto ao vivo",reason:"Pontos virtuais"},
      pricingMode:"INTERNAL_MODEL",pricingReason:"Multiplicadores internos ArenaPredict; não são odds da PandaScore.",
      options:[{id:1,key:"HOME",label:"Equipe A",multiplier:2,active:true},{id:2,key:"AWAY",label:"Equipe B",multiplier:2,active:true}]}]} as ArenaEvent;
    render(<MemoryRouter><LiveEventPanel event={event} onPredict={vi.fn()} /></MemoryRouter>);
    expect(screen.getByText("Placar ao vivo indisponível pelo provedor.")).toBeVisible();
    expect(screen.getByRole("link",{name:"Explorar opções (1)"})).toBeVisible();
    expect(screen.getByRole("button",{name:"Equipe A 2,00×"})).toBeEnabled();
    expect(screen.getByText(/Multiplicadores internos ArenaPredict/)).toBeInTheDocument();
  });
  it("erro do backend continua sendo erro, não ausência legítima de mercado",async()=>{
    vi.stubGlobal("fetch",vi.fn(async()=>new Response(JSON.stringify({message:"Serviço temporariamente indisponível"}),{status:503,headers:{"content-type":"application/json"}})));
    render(<MemoryRouter><ToastProvider><LiveEventsPage /></ToastProvider></MemoryRouter>);
    expect(await screen.findByRole("button",{name:/Tentar novamente/})).toBeVisible();
    expect(screen.queryByText("Mercados ao vivo indisponíveis para este evento")).not.toBeInTheDocument();
    expect(screen.queryByText("A arena está em intervalo")).not.toBeInTheDocument();
  });
});
