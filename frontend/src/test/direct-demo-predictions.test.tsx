import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { useState } from "react";
import { predictionsApi, privateTrainingApi, sessionStorage } from "../services/api";
import { LiveEventPanel } from "../pages/EventsPage";
import { EventCard } from "../components/EventCard";
import { PredictionComposer } from "../components/PredictionComposer";
import type { ArenaEvent, PredictionDraft } from "../types";

const shared = { token:"showcase-token", userId:2, name:"Jogador Demo", email:"demo@example.test", role:"PARTICIPANTE" as const, demoProfile:"PARTICIPANT" as const };
const signed = { ...shared, token:"private-signed-token", demoTraining:true };
const walletState = vi.hoisted(() => ({ balance:5000 }));
const mocks = vi.hoisted(() => ({ notify:vi.fn(), refreshTrainingWallet:vi.fn(), refreshWallet:vi.fn(), refreshNotifications:vi.fn() }));
vi.mock("../contexts/AppDataContext", () => ({ useAppData:() => ({
  wallet:{balance:9401}, trainingWallet:walletState, trainingWalletError:null,
  ...mocks,
}) }));
vi.mock("../contexts/ToastContext", () => ({ useToast:() => ({notify:mocks.notify}) }));

const live = { id:901,status:"LIVE",externalProvider:"PANDASCORE",demo:false,sport:{code:"CS2",name:"CS2"},
  homeCompetitor:{name:"Equipe A"},awayCompetitor:{name:"Equipe B"},availableMarketCount:1,
  markets:[{id:9,name:"Vencedor da série",status:"OPEN",timingMode:"LIVE_ONLY",minimumPoints:10,
    availability:{allowed:true,code:"OPEN",label:"Aberto ao vivo",reason:""},
    options:[{id:91,key:"HOME",label:"Equipe A",multiplier:1.6,active:true}]}]} as ArenaEvent;
const payload = {eventId:901,marketId:9,optionId:91,stakePoints:10,expectedMultiplier:1.6,idempotencyKey:"one-intent"};
const response = (value:unknown, status=200) => new Response(JSON.stringify(value),{status,headers:{"content-type":"application/json"}});

beforeEach(() => {
  window.sessionStorage.clear(); sessionStorage.save(shared); walletState.balance=5000;
  Object.values(mocks).forEach(mock => mock.mockReset());
  mocks.refreshWallet.mockResolvedValue(undefined); mocks.refreshNotifications.mockResolvedValue(undefined);
  mocks.refreshTrainingWallet.mockImplementation(async () => {walletState.balance=4990;});
});
afterEach(() => {cleanup();window.sessionStorage.clear();vi.unstubAllGlobals();});

describe("palpite direto do Demo com a capacidade privada existente", () => {
  it("prepara uma única carteira sem substituir a identidade do cenário e retoma após novo login", async () => {
    const fetchMock=vi.fn(async (url:string) => response(url.endsWith("/auth/demo") ? signed : {balance:5000}));
    vi.stubGlobal("fetch",fetchMock);
    await Promise.all([privateTrainingApi.wallet(),privateTrainingApi.wallet()]);
    expect(fetchMock.mock.calls.filter(call => call[0].endsWith("/auth/demo"))).toHaveLength(1);
    expect(sessionStorage.read()?.token).toBe(shared.token);
    sessionStorage.clear(); sessionStorage.save({...shared,token:"showcase-after-login"});
    await privateTrainingApi.wallet();
    expect(fetchMock.mock.calls.filter(call => call[0].endsWith("/auth/demo"))).toHaveLength(1);
    expect(sessionStorage.read()?.token).toBe("showcase-after-login");
  });

  it("preserva histórico compartilhado e próprio sem colisão entre IDs de tabelas distintas", async () => {
    const fetchMock=vi.fn(async (url:string) => {
      if(url.endsWith("/auth/demo")) return response(signed);
      if(url.endsWith("/training/wallet")) return response({balance:4990});
      return response([{id:7,eventId:901,status:url.includes("/training/")?"ACTIVE":"WON",placedAt:"2026-10-16T12:00:00Z"}]);
    });
    vi.stubGlobal("fetch",fetchMock);
    await privateTrainingApi.wallet();
    const values=await predictionsApi.history();
    expect(values.predictions).toEqual(expect.arrayContaining([
      expect.objectContaining({id:7,status:"WON"}),
      expect.objectContaining({id:"training:7",status:"ACTIVE",privateTraining:true,canCancel:false}),
    ]));
    const privateCall=fetchMock.mock.calls.find(call => call[0].endsWith("/training/predictions"))!;
    expect(new Headers((privateCall as unknown as [string,RequestInit])[1].headers).get("Authorization")).toBe("Bearer private-signed-token");
    expect(sessionStorage.read()?.demoTraining).not.toBe(true);
  });

  it("repete a mesma intenção após falha de rede sem criar outra sessão ou trocar a chave", async () => {
    let placements=0;
    const fetchMock=vi.fn(async (url:string) => {
      if(url.endsWith("/auth/demo")) return response(signed);
      if(++placements===1) throw new TypeError("lost response");
      return response({id:18,...payload,status:"ACTIVE"},201);
    });
    vi.stubGlobal("fetch",fetchMock);
    await expect(privateTrainingApi.create(payload)).rejects.toMatchObject({code:"NETWORK_ERROR"});
    await expect(privateTrainingApi.create(payload)).resolves.toMatchObject({id:18});
    const writes=fetchMock.mock.calls.filter(call => call[0].endsWith("/training/predictions"));
    expect(writes).toHaveLength(2);
    for(const call of writes) expect(new Headers((call as unknown as [string,RequestInit])[1].headers).get("Idempotency-Key")).toBe("one-intent");
    expect(fetchMock.mock.calls.filter(call => call[0].endsWith("/auth/demo"))).toHaveLength(1);
  });

  it("não usa a credencial de treino quando uma conta normal entra na mesma aba", async () => {
    const fetchMock=vi.fn(async (url:string) => response(url.endsWith("/auth/demo")?signed:{balance:5000}));
    vi.stubGlobal("fetch",fetchMock); await privateTrainingApi.wallet();
    sessionStorage.save({...shared,token:"normal-token",demoProfile:null}); fetchMock.mockClear();
    await expect(privateTrainingApi.wallet()).rejects.toMatchObject({status:403});
    expect(fetchMock).not.toHaveBeenCalled();
    await predictionsApi.create(payload);
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/api\/predictions$/);
    expect(new Headers((fetchMock.mock.calls[0] as unknown as [string,RequestInit])[1].headers).get("Authorization")).toBe("Bearer normal-token");
  });

  it("não encerra o perfil principal nem renova saldo silenciosamente quando o treino expira", async () => {
    const expired=vi.fn();window.addEventListener("arena:unauthorized",expired);
    const fetchMock=vi.fn(async (url:string) => url.endsWith("/auth/demo")?response(signed):response({message:"Este treino expirou."},401));
    vi.stubGlobal("fetch",fetchMock);
    await expect(privateTrainingApi.wallet()).rejects.toMatchObject({status:401});
    expect(expired).not.toHaveBeenCalled();expect(sessionStorage.read()?.token).toBe(shared.token);
    expect(fetchMock.mock.calls.filter(call => call[0].endsWith("/auth/demo"))).toHaveLength(1);
    window.removeEventListener("arena:unauthorized",expired);
  });

  it("uma falha no treino não apaga o histórico demonstrativo já carregado", async () => {
    const fetchMock=vi.fn(async (url:string) => {
      if(url.endsWith("/auth/demo")) return response(signed);
      if(url.endsWith("/training/wallet")) return response({balance:5000});
      if(url.includes("/training/")) return response({message:"Treino temporariamente indisponível"},503);
      return response([{id:7,eventId:901,status:"WON"}]);
    });
    vi.stubGlobal("fetch",fetchMock); await privateTrainingApi.wallet();
    const history=await predictionsApi.history();
    expect(history.predictions).toHaveLength(1);expect(history.predictions[0].id).toBe(7);
    expect(history.trainingError).toBeTruthy();
  });

  it("não usa o saldo maior do cenário para autorizar um palpite privado sem saldo", () => {
    render(<MemoryRouter><PredictionComposer draft={{event:live,market:live.markets![0],option:live.markets![0].options[0]}} onClose={vi.fn()}/></MemoryRouter>);
    fireEvent.change(screen.getByRole("spinbutton"),{target:{value:"6000"}});
    expect(screen.getByRole("button",{name:"Confirmar palpite"})).toBeDisabled();
    expect(screen.getByRole("alert")).toHaveTextContent("saldo de pontos é insuficiente");
  });

  it("cancela a preparação pendente se outra identidade entrar na aba", async () => {
    let finish!:(value:Response)=>void;
    const fetchMock=vi.fn(()=>new Promise<Response>(resolve=>{finish=resolve;}));vi.stubGlobal("fetch",fetchMock);
    const pending=privateTrainingApi.wallet();
    sessionStorage.save({...shared,token:"normal-token",demoProfile:null});
    finish(response(signed));
    await expect(pending).rejects.toMatchObject({code:"REQUEST_CANCELLED"});
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("seleciona no Ao vivo, confirma no mesmo formulário e atualiza o saldo privado", async () => {
    const fetchMock=vi.fn(async (url:string) => url.endsWith("/auth/demo")?response(signed):response({id:18,...payload,status:"ACTIVE",optionLabel:"Equipe A",potentialPoints:16},201));
    vi.stubGlobal("fetch",fetchMock);
    function Journey() {
      const [draft,setDraft]=useState<PredictionDraft|null>(null);
      return <MemoryRouter><LiveEventPanel event={live} onPredict={setDraft}/><PredictionComposer draft={draft} onClose={()=>setDraft(null)}/></MemoryRouter>;
    }
    render(<Journey/>);
    expect(screen.getByRole("link",{name:"Fazer palpite"})).toHaveAttribute("href","/events/901");
    fireEvent.click(screen.getByRole("button",{name:"Equipe A 1,60×"}));
    expect(screen.getByRole("button",{name:/Equipe A 1,60/})).toHaveAttribute("aria-pressed","true");
    expect(screen.getByRole("dialog")).toHaveTextContent("carteira privada de treino");
    fireEvent.change(screen.getByRole("spinbutton"),{target:{value:"10"}});
    fireEvent.click(screen.getByRole("button",{name:"Confirmar palpite"}));
    expect(await screen.findByText("Sua leitura está registrada")).toBeVisible();
    await waitFor(()=>expect(screen.getByRole("dialog")).toHaveTextContent("4.990 pts"));
    expect(mocks.refreshTrainingWallet).toHaveBeenCalledTimes(1);expect(mocks.refreshWallet).not.toHaveBeenCalled();
    expect(fetchMock.mock.calls.some(call => call[0].endsWith("/api/predictions"))).toBe(false);
  });

  it("o card da Visão geral mantém o contexto e estados suspensos continuam bloqueados", () => {
    render(<MemoryRouter><EventCard event={live}/><LiveEventPanel event={{...live,availableMarketCount:0,markets:[{...live.markets![0],availability:{allowed:false,code:"SUSPENDED",label:"Suspenso",reason:"Aguardando dados"}}]}} onPredict={vi.fn()}/></MemoryRouter>);
    expect(screen.getByRole("link",{name:"Fazer palpite"})).toHaveAttribute("href","/events/901");
    expect(screen.getByRole("button",{name:"Equipe A 1,60×"})).toBeDisabled();
    expect(screen.queryByRole("link",{name:"Fazer um palpite Demo"})).not.toBeInTheDocument();
  });
});
