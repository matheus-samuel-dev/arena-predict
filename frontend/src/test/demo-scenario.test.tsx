import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AdminRoute } from "../App";
import { DemoPage } from "../pages/DemoPage";
import { postLoginDestination } from "../pages/LoginPage";
import { EventCard, MarketList } from "../components/EventCard";
import { PredictionComposer } from "../components/PredictionComposer";
import type { ArenaEvent, DemoScenario } from "../types";

const state = vi.hoisted(() => ({ profile: "PARTICIPANT" as "PARTICIPANT" | "ADMIN" | null }));
const mocks = vi.hoisted(() => ({ scenario: vi.fn(), start: vi.fn(), result: vi.fn(), reset: vi.fn(), create: vi.fn(), listPools: vi.fn(), notify: vi.fn(), refreshWallet: vi.fn(), refreshNotifications: vi.fn(), demoLogin: vi.fn() }));
vi.mock("../services/api", async (original) => ({ ...await original<typeof import("../services/api")>(), demoApi: { scenario: mocks.scenario, start: mocks.start, result: mocks.result, reset: mocks.reset }, predictionsApi: { create: mocks.create }, poolsApi: { list: mocks.listPools }, sessionStorage: { read: () => ({ demoProfile: state.profile }) } }));
vi.mock("../contexts/AuthContext", () => ({ useAuth: () => ({ user: { userId: state.profile === "ADMIN" ? 1 : 2, name: "Demo", role: state.profile === "PARTICIPANT" ? "PARTICIPANTE" : "ADMIN", demoProfile: state.profile }, authenticating: false, demoLogin: mocks.demoLogin }) }));
vi.mock("../contexts/AppDataContext", () => ({ useAppData: () => ({ wallet: { balance: 1000 }, refreshWallet: mocks.refreshWallet, refreshNotifications: mocks.refreshNotifications }) }));
vi.mock("../contexts/ToastContext", () => ({ useToast: () => ({ notify: mocks.notify }) }));

const event: ArenaEvent = { id: 77, startsAt: "2026-10-01T20:00:00Z", status: "SCHEDULED", demo: true, demoManaged: true, bestOf: 3, format: "BO3", championshipName: "Arena Demo Cup", homeCompetitor: { name: "FURIA Esports" }, awayCompetitor: { name: "Natus Vincere" }, markets: [{ id: 9, code: "SERIES_SCORE", templateCode: "SERIES_SCORE", name: "Placar correto da série", minimumPoints: 10, availability: { allowed: true, code: "OPEN", label: "Aberto", reason: "Antes da partida" }, options: [{ id: 20, key: "2:1", label: "FURIA 2 × 1 NAVI", multiplier: 2.5 }] }] };
const scenario: DemoScenario = { generation: 5, championship: { id: 80, name: "Arena Demo Cup" }, event, history: [], predictions: [], ranking: [{ position: 1, userId: 2, name: "Jogador Demo", points: 250, hits: 2, currentUser: true }], canManage: false, canPredict: true, updatedAt: "2026-10-01T18:00:00Z", notice: "Contas e rodada compartilhadas entre visitantes." };
function renderDemo() { return render(<MemoryRouter><DemoPage /></MemoryRouter>); }
async function ready() { return await screen.findByRole("heading", { name: "Experimente a Arena" }); }
function admin(value: Partial<DemoScenario> = {}) { state.profile = "ADMIN"; mocks.scenario.mockResolvedValue({ ...scenario, canManage: true, canPredict: false, ...value }); }

describe("jornada Demo integrada", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset()); state.profile = "PARTICIPANT";
    mocks.scenario.mockResolvedValue(scenario); mocks.listPools.mockResolvedValue([]);
    mocks.refreshWallet.mockResolvedValue(undefined); mocks.refreshNotifications.mockResolvedValue(undefined);
    vi.spyOn(document, "hidden", "get").mockReturnValue(false);
  });
  afterEach(() => { cleanup(); vi.restoreAllMocks(); });

  it("carrega snapshot, mostra dados compartilhados e envia palpite pelo compositor existente", async () => {
    mocks.create.mockResolvedValue({ id: 99, eventId: 77, optionLabel: "FURIA 2 × 1 NAVI", stakePoints: 50, potentialPoints: 125, status: "ACTIVE" });
    renderDemo(); await ready();
    expect(screen.getByRole("note")).toHaveTextContent("compartilhadas");
    expect(screen.getByRole("table", { name: "Ranking da competição Demo" })).toHaveTextContent("250");
    expect(screen.queryByRole("button", { name: "Iniciar partida Demo" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /FURIA 2 × 1 NAVI/ }));
    expect(screen.getByRole("dialog", { name: "Confirmar palpite" })).toBeVisible();
    expect(screen.queryByText(/Vincular a um bolão/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Confirmar palpite" }));
    expect(await screen.findByText("Sua leitura está registrada")).toBeVisible();
    expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({ eventId: 77, marketId: 9, optionId: 20, expectedMultiplier: 2.5 }));
    expect(mocks.listPools).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Continuar na Arena" }));
    await waitFor(() => expect(mocks.scenario).toHaveBeenCalledTimes(2));
  });

  it("admin precisa confirmar início e o estado resultante vem do servidor", async () => {
    admin(); mocks.start.mockResolvedValue({ ...scenario, canManage: true, canPredict: false, event: { ...event, status: "LIVE" } });
    renderDemo(); await ready();
    expect(screen.getByRole("button", { name: /FURIA 2 × 1 NAVI/ })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Iniciar partida Demo" }));
    expect(mocks.start).not.toHaveBeenCalled();
    const dialog = screen.getByRole("dialog", { name: "Iniciar partida Demo" });
    expect(dialog).toHaveTextContent("encerra os palpites");
    fireEvent.click(within(dialog).getByRole("button", { name: "Confirmar início" }));
    await waitFor(() => expect(mocks.start).toHaveBeenCalledWith(77));
    expect(await screen.findByRole("button", { name: "Simular resultado" })).toBeVisible();
  });

  it("simula final BO3, mostra palpites processados e ranking sem calcular pontos no browser", async () => {
    admin({ event: { ...event, status: "LIVE" } });
    mocks.result.mockResolvedValue({ ...scenario, canManage: true, canPredict: false, event: { ...event, status: "FINISHED", homeScore: 2, awayScore: 1, resultProcessedAt: "2026-10-01T21:00:00Z" }, predictions: [{ id: 99, eventId: 77, optionLabel: "FURIA 2 × 1 NAVI", status: "WON", stakePoints: 50, rewardedPoints: 125 }], ranking: [{ position: 1, name: "Jogador Demo", points: 375 }] });
    renderDemo(); await ready(); fireEvent.click(screen.getByRole("button", { name: "Simular resultado" }));
    fireEvent.change(screen.getByLabelText("Placar final simulado · BO3"), { target: { value: "2:1" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirmar resultado" }));
    expect(await screen.findByText("125 pts recebidos")).toBeVisible();
    expect(mocks.result).toHaveBeenCalledWith(77, 2, 1);
    expect(screen.getByRole("table")).toHaveTextContent("375");
    expect(screen.getByText("Resultado processado")).toBeVisible();
  });

  it("reset pede confirmação e envia a geração observada, preservando o aviso de histórico", async () => {
    admin(); mocks.reset.mockResolvedValue({ ...scenario, generation: 6, event: { ...event, id: 78 }, canManage: true, canPredict: false });
    renderDemo(); await ready(); fireEvent.click(screen.getByRole("button", { name: "Começar nova rodada" }));
    expect(screen.getByRole("dialog")).toHaveTextContent("reembolsados");
    expect(screen.getByRole("dialog")).toHaveTextContent("preservados");
    fireEvent.click(screen.getByRole("button", { name: "Confirmar nova rodada" }));
    await waitFor(() => expect(mocks.reset).toHaveBeenCalledWith(5));
    expect(await screen.findByText("LABORATÓRIO ARENA · RODADA 6")).toBeVisible();
  });

  it("bloqueia confirmação obsoleta quando outro visitante muda a geração", async () => {
    admin(); renderDemo(); await ready(); fireEvent.click(screen.getByRole("button", { name: "Começar nova rodada" }));
    mocks.scenario.mockResolvedValue({ ...scenario, generation: 6, event: { ...event, id: 78 }, canManage: true, canPredict: false });
    await act(async () => { document.dispatchEvent(new Event("visibilitychange")); });
    expect(screen.getByRole("button", { name: "Confirmar nova rodada" })).toBeDisabled();
    expect(screen.getByRole("alert")).toHaveTextContent("Outro visitante");
    expect(mocks.reset).not.toHaveBeenCalled();
  });

  it("não deixa um refresh antigo desfazer o início já confirmado pelo servidor", async () => {
    admin(); renderDemo(); await ready();
    let finishOldRead!: (value: DemoScenario) => void;
    mocks.scenario.mockReturnValueOnce(new Promise((resolve) => { finishOldRead = resolve; }));
    fireEvent(document, new Event("visibilitychange"));
    mocks.start.mockResolvedValue({ ...scenario, canManage: true, canPredict: false, event: { ...event, status: "LIVE" } });
    fireEvent.click(screen.getByRole("button", { name: "Iniciar partida Demo" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirmar início" }));
    expect(await screen.findByRole("button", { name: "Simular resultado" })).toBeVisible();
    await act(async () => finishOldRead({ ...scenario, canManage: true, canPredict: false }));
    expect(screen.getByRole("button", { name: "Simular resultado" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "Iniciar partida Demo" })).not.toBeInTheDocument();
  });

  it("protege envio duplicado, mantém modal durante loading e permite recuperação de falha", async () => {
    admin(); let reject!: (reason: Error) => void;
    mocks.start.mockReturnValue(new Promise((_resolve, rejectPromise) => { reject = rejectPromise; }));
    renderDemo(); await ready(); fireEvent.click(screen.getByRole("button", { name: "Iniciar partida Demo" }));
    const button = screen.getByRole("button", { name: "Confirmar início" }); fireEvent.click(button); fireEvent.click(button);
    expect(button).toBeDisabled(); expect(mocks.start).toHaveBeenCalledTimes(1);
    fireEvent.keyDown(document, { key: "Escape" }); expect(screen.getByRole("dialog")).toBeVisible();
    await act(async () => reject(new Error("Servidor temporariamente indisponível")));
    expect(await screen.findByRole("alert")).toHaveTextContent("temporariamente indisponível");
    expect(button).toBeEnabled(); expect(mocks.scenario).toHaveBeenCalledTimes(2);
  });

  it("exibe carregamento e erro inicial com tentativa de recuperação", async () => {
    let reject!: (reason: Error) => void; mocks.scenario.mockReturnValueOnce(new Promise((_resolve, rejectPromise) => { reject = rejectPromise; }));
    renderDemo(); expect(screen.getByLabelText("Carregando conteúdo")).toBeVisible();
    await act(async () => reject(new Error("Sem conexão")));
    expect(screen.getByRole("alert")).toHaveTextContent("Sem conexão");
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" })); await ready();
  });

  it("preserva dados durante erro de refresh e acompanha resultado em outra sessão", async () => {
    renderDemo(); await ready(); mocks.scenario.mockRejectedValueOnce(new Error("Sem conexão"));
    fireEvent.click(screen.getByRole("button", { name: "Atualizar" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("últimos dados"); expect(screen.getByRole("table")).toHaveTextContent("250");
    mocks.scenario.mockResolvedValue({ ...scenario, canPredict: false, event: { ...event, status: "FINISHED", homeScore: 0, awayScore: 2 }, ranking: [{ position: 1, name: "Jogador Demo", points: 480 }] });
    await act(async () => { document.dispatchEvent(new Event("visibilitychange")); });
    expect(screen.getByRole("table")).toHaveTextContent("480"); expect(screen.getByText("Rodada concluída")).toBeVisible();
  });

  it("troca de perfil usa autenticação Demo e não altera papel localmente", async () => {
    mocks.demoLogin.mockResolvedValue({}); renderDemo(); await ready();
    fireEvent.click(screen.getByRole("button", { name: "Administrador Demo" }));
    expect(mocks.demoLogin).toHaveBeenCalledWith("ADMIN");
    expect(screen.queryByRole("button", { name: "Iniciar partida Demo" })).not.toBeInTheDocument();
  });

  it("mostra cancelamento e reembolso sem anunciar partida ao vivo ou recompensa", async () => {
    admin({ event: { ...event, status: "CANCELLED" }, predictions: [{ id: 99, eventId: 77, optionLabel: "2 × 1", status: "REFUNDED", stakePoints: 50, rewardedPoints: 0 }] });
    renderDemo(); await ready();
    expect(screen.getByRole("heading", { name: "Rodada cancelada" })).toBeVisible();
    expect(screen.getByText("50 pts reembolsados")).toBeVisible();
    expect(screen.queryByText("Aguardando resultado")).not.toBeInTheDocument();
    expect(screen.queryByText("A partida está ao vivo")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Simular resultado" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Começar nova rodada" })).toBeEnabled();
  });

  it("não atribui a um provedor externo a ausência de placar da rodada Demo", async () => {
    admin({ event: { ...event, status: "LIVE", homeScore: null, awayScore: null } });
    renderDemo(); await ready();
    expect(screen.getByText("Aguardando o resultado da rodada Demo.")).toBeVisible();
    expect(screen.queryByText(/indisponível pelo provedor/)).not.toBeInTheDocument();
  });

  it("conta comum escolhe acesso Demo sem consultar o endpoint reservado", async () => {
    state.profile = null;
    mocks.demoLogin.mockResolvedValue({}); renderDemo(); await ready();
    expect(screen.getByRole("heading", { name: "Escolha um perfil Demo" })).toBeVisible();
    expect(mocks.scenario).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Participante Demo" }));
    expect(mocks.demoLogin).toHaveBeenCalledWith("PARTICIPANT");
  });

  it("conta comum também consulta uma partida controlada sem registrar palpite pelo fluxo genérico", () => {
    state.profile = null;
    const choose = vi.fn();
    render(<MemoryRouter><EventCard event={event} onPredict={choose} /><MarketList event={event} markets={event.markets!} onPredict={choose} /><PredictionComposer draft={{ event, market: event.markets![0], option: event.markets![0].options[0] }} onClose={vi.fn()} /></MemoryRouter>);
    expect(screen.getByRole("button", { name: /FURIA 2 × 1 NAVI/ })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Confirmar palpite" })).not.toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Ir para a demonstração" }).every((link) => link.getAttribute("href") === "/demo")).toBe(true);
    expect(mocks.create).not.toHaveBeenCalled(); expect(mocks.listPools).not.toHaveBeenCalled();
    expect(choose).not.toHaveBeenCalled();
  });

  it("participante Demo pode registrar palpite pelo detalhe normal do evento controlado", async () => {
    mocks.create.mockResolvedValue({ id: 99, eventId: 77, optionLabel: "FURIA 2 × 1 NAVI", stakePoints: 50, potentialPoints: 125, status: "ACTIVE" });
    render(<MemoryRouter><PredictionComposer draft={{ event, market: event.markets![0], option: event.markets![0].options[0] }} onClose={vi.fn()} /></MemoryRouter>);
    expect(screen.getByRole("button", { name: "Confirmar palpite" })).toBeEnabled();
    expect(screen.queryByText(/Vincular a um bolão/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Confirmar palpite" }));
    expect(await screen.findByText("Sua leitura está registrada")).toBeVisible();
    expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({ eventId: 77, expectedMultiplier: 2.5 }));
    expect(mocks.listPools).not.toHaveBeenCalled();
  });

  it("conta Demo não recebe botão de palpite em outros eventos nem consegue enviar modal direto", () => {
    const realEvent = { ...event, demo: false, demoManaged: false, externalProvider: "PANDASCORE", externalId: "99" };
    render(<MemoryRouter><EventCard event={realEvent} onPredict={vi.fn()} /><MarketList event={realEvent} markets={realEvent.markets!} onPredict={vi.fn()} /><PredictionComposer draft={{ event: realEvent, market: realEvent.markets![0], option: realEvent.markets![0].options[0] }} onClose={vi.fn()} /></MemoryRouter>);
    expect(screen.getByRole("dialog", { name: "Palpites da conta Demo" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "Confirmar palpite" })).not.toBeInTheDocument();
    expect(mocks.create).not.toHaveBeenCalled();
  });

  it("abre o produto completo após login Demo e permite consulta administrativa", async () => {
    expect(postLoginDestination("ADMIN", "/admin/results", "ADMIN")).toBe("/admin");
    expect(postLoginDestination("PARTICIPANTE", "/events", "PARTICIPANT")).toBe("/events");
    state.profile = "ADMIN";
    render(<MemoryRouter initialEntries={["/admin/results"]}><Routes><Route element={<AdminRoute />}><Route path="/admin/results" element={<div>Resultados oficiais</div>} /></Route><Route path="/demo" element={<div>Jornada protegida</div>} /></Routes></MemoryRouter>);
    expect(await screen.findByText("Resultados oficiais")).toBeVisible(); expect(screen.queryByText("Jornada protegida")).not.toBeInTheDocument();
  });
});
