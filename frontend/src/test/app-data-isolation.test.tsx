import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AppDataProvider, useAppData } from "../contexts/AppDataContext";

const state=vi.hoisted(()=>({session:null as {token:string;name:string}|null}));
const api=vi.hoisted(()=>({wallet:vi.fn(),notifications:vi.fn(),readAll:vi.fn(),read:vi.fn()}));
vi.mock("../contexts/AuthContext",()=>({useAuth:()=>({session:state.session})}));
vi.mock("../services/api",()=>({asList:(value:unknown)=>Array.isArray(value)?value:[],walletApi:{get:api.wallet},notificationsApi:{list:api.notifications,read:api.read,readAll:api.readAll}}));
function deferred<T>(){let resolve!:(value:T)=>void;const promise=new Promise<T>(r=>{resolve=r;});return {promise,resolve};}
function Probe(){const c=useAppData();return <><output aria-label="Saldo">{c.wallet?.balance??"NONE"}</output><output aria-label="Notificações">{c.notifications.map(n=>`${n.id}:${n.read}`).join(",")}</output><output aria-label="Carregamento">{String(c.loading)}</output><button onClick={()=>void c.refreshWallet().catch(()=>undefined)}>Saldo</button><button onClick={()=>void c.refreshNotifications().catch(()=>undefined)}>Notificações</button><button onClick={()=>void c.markAllNotificationsRead()}>Ler todas</button></>;}
const tree=()=> <AppDataProvider><Probe/></AppDataProvider>;
beforeEach(()=>{Object.values(api).forEach(m=>m.mockReset());state.session={token:"account-a",name:"A"};api.wallet.mockResolvedValue({balance:100});api.notifications.mockResolvedValue([{id:1,read:false}]);});
afterEach(cleanup);
describe("dados privados por sessão",()=>{
  it("ignora saldo e notificações atrasados de outra conta",async()=>{
    const view=render(tree());await waitFor(()=>expect(screen.getByLabelText("Saldo")).toHaveTextContent("100"));
    const wallet=deferred<unknown>(),notifications=deferred<unknown>();api.wallet.mockImplementationOnce(()=>wallet.promise);api.notifications.mockImplementationOnce(()=>notifications.promise);
    fireEvent.click(screen.getByRole("button",{name:"Saldo"}));fireEvent.click(screen.getByRole("button",{name:"Notificações"}));
    api.wallet.mockResolvedValue({balance:200});api.notifications.mockResolvedValue([{id:2,read:false}]);state.session={token:"account-b",name:"B"};view.rerender(tree());
    await waitFor(()=>expect(screen.getByLabelText("Saldo")).toHaveTextContent("200"));
    await act(async()=>{wallet.resolve({balance:999});notifications.resolve([{id:99,read:false}]);});
    expect(screen.getByLabelText("Saldo")).toHaveTextContent("200");expect(screen.getByLabelText("Notificações")).toHaveTextContent("2:false");expect(screen.getByLabelText("Notificações")).not.toHaveTextContent("99");
  });
  it("resposta antiga de ler todas não altera notificações da nova conta",async()=>{
    const view=render(tree());await waitFor(()=>expect(screen.getByLabelText("Notificações")).toHaveTextContent("1:false"));
    const pending=deferred<void>();api.readAll.mockReturnValue(pending.promise);fireEvent.click(screen.getByRole("button",{name:"Ler todas"}));
    state.session={token:"account-b",name:"B"};api.notifications.mockResolvedValue([{id:2,read:false}]);view.rerender(tree());await waitFor(()=>expect(screen.getByLabelText("Notificações")).toHaveTextContent("2:false"));
    await act(async()=>pending.resolve());expect(screen.getByLabelText("Notificações")).toHaveTextContent("2:false");
  });
  it("logout limpa dados e carregamento mesmo com pedido pendente",async()=>{
    api.wallet.mockReturnValue(new Promise(()=>{}));const view=render(tree());state.session=null;view.rerender(tree());
    expect(screen.getByLabelText("Saldo")).toHaveTextContent("NONE");expect(screen.getByLabelText("Notificações")).toBeEmptyDOMElement();expect(screen.getByLabelText("Carregamento")).toHaveTextContent("false");
  });
  it("atualização de perfil com mesmo token não duplica requests",async()=>{
    const view=render(tree());await waitFor(()=>expect(screen.getByLabelText("Saldo")).toHaveTextContent("100"));state.session={token:"account-a",name:"Nome atualizado"};view.rerender(tree());
    expect(api.wallet).toHaveBeenCalledTimes(1);expect(api.notifications).toHaveBeenCalledTimes(1);
  });
});
