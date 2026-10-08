import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { DemoPredictionAccess } from "../components/DemoPredictionAccess";
import { authApi } from "../services/api";
import type { ArenaEvent } from "../types";

afterEach(()=>{cleanup();vi.unstubAllGlobals();vi.useRealTimers();});
describe("acesso funcional sem enfraquecer autorização",()=>{
  it("oferece CTA para rodada separada e esclarece participação real",()=>{
    render(<MemoryRouter><DemoPredictionAccess event={{id:1,demo:false,externalProvider:"PANDASCORE"} as ArenaEvent}/></MemoryRouter>);
    expect(screen.getByRole("link",{name:"Fazer um palpite Demo"})).toHaveAttribute("href","/demo");
    expect(screen.getByText(/Entrar no treino ao vivo/)).toBeInTheDocument();
    expect(screen.getByText(/carteira e histórico isolados/)).toBeInTheDocument();
  });
  it("não chama um evento demonstrativo de real",()=>{
    render(<MemoryRouter><DemoPredictionAccess event={{id:1,demo:true,demoManaged:true} as ArenaEvent}/></MemoryRouter>);
    expect(screen.getByText(/trocar de perfil/)).toBeInTheDocument();
    expect(screen.queryByText(/eventos reais/)).not.toBeInTheDocument();
  });
  it("recupera cadastro com resposta perdida por login sem repetir escrita",async()=>{
    const fetchMock=vi.fn().mockRejectedValueOnce(new TypeError("connection interrupted"))
      .mockResolvedValueOnce(new Response(JSON.stringify({token:"qa-token",userId:91,role:"PARTICIPANTE",name:"QA",demoProfile:null}),{headers:{"content-type":"application/json"}}));
    vi.stubGlobal("fetch",fetchMock);
    const result=await authApi.register({name:"QA",email:"qa@example.test",password:"test-password"});
    expect(result.userId).toBe(91);expect(result.demoProfile).toBeNull();
    expect(fetchMock.mock.calls.map(call=>String(call[0]))).toEqual([expect.stringContaining("/auth/register"),expect.stringContaining("/auth/login")]);
  });
  it("não transforma cadastro inválido em tentativa de login",async()=>{
    const fetchMock=vi.fn().mockResolvedValue(new Response(JSON.stringify({message:"Dados inválidos"}),{status:400,headers:{"content-type":"application/json"}}));vi.stubGlobal("fetch",fetchMock);
    await expect(authApi.register({name:"QA",email:"qa@example.test",password:"test-password"})).rejects.toMatchObject({status:400});
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
