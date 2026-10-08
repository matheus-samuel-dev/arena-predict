import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { authApi, predictionsApi, sessionStorage, walletApi } from "../services/api";
import { predictionReadOnly } from "../app/predictionAccess";
import { eventScore } from "../app/sportsData";
import { LiveEventPanel } from "../pages/EventsPage";
import type { ArenaEvent } from "../types";

const session={token:"signed-training-test",userId:2,name:"Jogador Demo",email:"demo@example.test",role:"PARTICIPANTE" as const,demoProfile:"PARTICIPANT" as const,demoTraining:true};
const live={id:100,status:"LIVE",externalProvider:"PANDASCORE",demo:false,sport:{code:"CS2",name:"Counter-Strike 2"},
  homeCompetitor:{name:"Equipe A"},awayCompetitor:{name:"Equipe B"},homeScore:1,awayScore:0,liveScoreAvailable:true,lastSyncedAt:new Date().toISOString(),bestOf:3,
  markets:[{id:1,name:"Vencedor da série",status:"OPEN",timingMode:"LIVE_ONLY",availability:{allowed:true,code:"OPEN",label:"Aberto ao vivo",reason:""},options:[{id:2,key:"HOME",label:"Equipe A",multiplier:1.5,active:true}]}]} as unknown as ArenaEvent;
afterEach(()=>{cleanup();sessionStorage.clear();vi.unstubAllGlobals();});
describe("treino ao vivo com contratos e carteira separados",()=>{
  it("preserva o contexto assinado na sessão e usa somente endpoints de treino",async()=>{
    const fetchMock=vi.fn().mockImplementation(()=>Promise.resolve(new Response(JSON.stringify(session),{headers:{"content-type":"application/json"}})));
    vi.stubGlobal("fetch",fetchMock);
    const authenticated=await authApi.demo("PARTICIPANT",true);sessionStorage.save(authenticated);
    expect(sessionStorage.read()?.demoTraining).toBe(true);
    await walletApi.get();await predictionsApi.list();await predictionsApi.create({eventId:100,marketId:1,optionId:2,stakePoints:25,idempotencyKey:"one"});
    expect(fetchMock.mock.calls.map(c=>String(c[0]))).toEqual([expect.stringContaining("/auth/demo"),expect.stringContaining("/training/wallet"),expect.stringContaining("/training/predictions"),expect.stringContaining("/training/predictions")]);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({profile:"PARTICIPANT",training:true});
  });
  it("permite selecionar mercado real LIVE no treino e preserva bloqueio fora desse modo",()=>{
    sessionStorage.save(session);expect(predictionReadOnly(live)).toBe(false);
    render(<MemoryRouter><LiveEventPanel event={live} onPredict={vi.fn()} /></MemoryRouter>);
    expect(screen.getByRole("button",{name:/Equipe A 1,50/})).toBeEnabled();
    expect(predictionReadOnly({...live,demo:true,externalProvider:undefined})).toBe(true);
    sessionStorage.save({...session,demoTraining:false});expect(predictionReadOnly(live)).toBe(true);
  });
  it("não apresenta score ausente ou velho como zero ou atual e recupera snapshot novo",()=>{
    expect(eventScore({...live,homeScore:null,awayScore:null})).toBeNull();
    expect(eventScore({...live,lastSyncedAt:new Date(Date.now()-301_000).toISOString()})).toBeNull();
    expect(eventScore({...live,lastSyncedAt:new Date().toISOString(),homeScore:1,awayScore:1})).toEqual([1,1]);
    expect(eventScore({...live,status:"FINISHED",lastSyncedAt:"2026-01-01T00:00:00Z",homeScore:2,awayScore:1})).toEqual([2,1]);
  });
});
