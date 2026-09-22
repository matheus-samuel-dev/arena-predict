import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { calculatePotentialPoints, PredictionComposer } from "../components/PredictionComposer";
import type { PredictionDraft } from "../types";

const mocks = vi.hoisted(() => ({
  create: vi.fn(),
  listPools: vi.fn(),
  notify: vi.fn(),
  refreshWallet: vi.fn(),
  refreshNotifications: vi.fn(),
}));
const walletState = vi.hoisted(() => ({ balance: 1_000 }));

vi.mock("../services/api", () => ({
  createIdempotencyKey: () => "prediction-intent-fixed",
  poolsApi: { list: mocks.listPools },
  predictionsApi: { create: mocks.create },
}));

vi.mock("../contexts/AppDataContext", () => ({
  useAppData: () => ({
    wallet: walletState,
    refreshWallet: mocks.refreshWallet,
    refreshNotifications: mocks.refreshNotifications,
  }),
}));

vi.mock("../contexts/ToastContext", () => ({
  useToast: () => ({ notify: mocks.notify }),
}));

const draft = {
  event: {
    id: 3,
    championshipName: "NBA",
    home: { id: 1, name: "Boston Celtics" },
    away: { id: 2, name: "Dallas Mavericks" },
  },
  market: { id: 8, name: "Vencedor da partida", minimumPoints: 20, availability: { allowed: true, code: "OPEN", label: "Aberto", reason: "" } },
  option: { id: 13, label: "Boston Celtics", multiplier: 1.72 },
} as unknown as PredictionDraft;

describe("confirmação do palpite", () => {
  it.each([[1000,1.001,1001],[75,1.85,138],[20000,1.999,39980]])("calcula %s pontos com multiplicador %s sem erro binário de arredondamento", (stake, coefficient, expected) => {
    expect(calculatePotentialPoints(stake,coefficient)).toBe(expected);
  });
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    walletState.balance = 1_000;
    mocks.listPools.mockResolvedValue([]);
    mocks.refreshWallet.mockResolvedValue(undefined);
    mocks.refreshNotifications.mockResolvedValue(undefined);
    mocks.create.mockResolvedValue({
      id: 91,
      eventId: 3,
      marketId: 8,
      optionId: 13,
      stakePoints: 20,
      potentialPoints: 34,
      status: "ACTIVE",
    });
  });

  afterEach(() => cleanup());

  it("mantém o recibo visível e só atualiza a página após o reconhecimento", async () => {
    const onClose = vi.fn();
    const onCreated = vi.fn();
    render(<PredictionComposer draft={draft} onClose={onClose} onCreated={onCreated} />);

    fireEvent.change(screen.getByRole("spinbutton"), { target: { value: "20" } });
    const submit = screen.getByRole("button", { name: "Confirmar palpite" });
    await waitFor(() => expect(submit).toBeEnabled());
    fireEvent.click(submit);

    expect(await screen.findByText("Sua leitura está registrada")).toBeInTheDocument();
    expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({ expectedMultiplier: 1.72 }));
    expect(screen.getByText("Boston Celtics × Dallas Mavericks")).toBeVisible();
    expect(screen.getByText("Boston Celtics", { selector: ".prediction-receipt dd" })).toBeVisible();
    expect(screen.getByRole("link", { name: "Ver meus palpites" })).toHaveAttribute("href", "/predictions");
    expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({
      stakePoints: 20,
      idempotencyKey: "prediction-intent-fixed",
    }));
    expect(onCreated).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Continuar na Arena" }));
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onCreated).toHaveBeenCalledWith(expect.objectContaining({ id: 91 }));
  });

  it("bloqueia dois submits no mesmo instante e não fecha durante o débito", async () => {
    let complete!: (value: unknown) => void;
    mocks.create.mockReturnValue(new Promise((resolve) => { complete = resolve; }));
    const close = vi.fn();
    render(<PredictionComposer draft={draft} onClose={close} />);
    const form = screen.getByRole("button", { name: "Confirmar palpite" }).closest("form")!;
    fireEvent.submit(form);
    fireEvent.submit(form);
    fireEvent.keyDown(document, { key: "Escape" });
    fireEvent.click(screen.getByRole("button", { name: "Fechar" }));
    expect(mocks.create).toHaveBeenCalledTimes(1);
    expect(close).not.toHaveBeenCalled();
    expect(screen.getByRole("spinbutton")).toBeDisabled();
    await act(async () => complete({ id: 92, stakePoints: 50, potentialPoints: 86 }));
    expect(await screen.findByText("Sua leitura está registrada")).toBeVisible();
  });

  it.each([
    ["19", /mínimo deste mercado é 20/],
    ["1001", /saldo de pontos é insuficiente/],
    ["20.5", /quantidade inteira/],
    ["20001", /máximo por palpite/],
  ])("rejeita %s pontos sem enviar débito", async (value, message) => {
    render(<PredictionComposer draft={draft} onClose={vi.fn()} />);
    fireEvent.change(screen.getByRole("spinbutton"), { target: { value } });
    const submit = screen.getByRole("button", { name: "Confirmar palpite" });
    expect(submit).toBeDisabled();
    expect(screen.getByRole("alert")).toHaveTextContent(message);
    fireEvent.submit(submit.closest("form")!);
    expect(mocks.create).not.toHaveBeenCalled();
    await waitFor(() => expect(mocks.listPools).toHaveBeenCalled());
  });

  it("mostra retorno arredondado e aceita usar exatamente o saldo", async () => {
    walletState.balance = 21;
    render(<PredictionComposer draft={draft} onClose={vi.fn()} />);
    fireEvent.change(screen.getByRole("spinbutton"), { target: { value: "21" } });
    expect(screen.getByText("36 pts")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Confirmar palpite" }));
    await screen.findByText("Sua leitura está registrada");
    expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({ stakePoints: 21 }));
    expect(mocks.refreshWallet).toHaveBeenCalledTimes(1);
  });

  it("mantém a chave ao repetir uma confirmação após falha de rede", async () => {
    mocks.create.mockRejectedValueOnce(new Error("Falha de conexão. Tente novamente."));
    render(<PredictionComposer draft={draft} onClose={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "Confirmar palpite" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Falha de conexão");
    fireEvent.click(screen.getByRole("button", { name: "Confirmar palpite" }));
    await screen.findByText("Sua leitura está registrada");
    expect(mocks.create).toHaveBeenCalledTimes(2);
    expect(mocks.create.mock.calls[1][0]).toEqual(mocks.create.mock.calls[0][0]);
  });

  it("atualização do servidor suspende o boletim aberto e preserva os pontos digitados", async () => {
    const { rerender } = render(<PredictionComposer draft={draft} onClose={vi.fn()} />);
    fireEvent.change(screen.getByRole("spinbutton"), { target: { value: "123" } });
    const latest = { ...draft.event, markets: [{ ...draft.market, options: [draft.option], availability: { allowed: false, code: "SUSPENDED", label: "Mercado suspenso", reason: "Mercado temporariamente suspenso." } }] };
    rerender(<PredictionComposer draft={draft} currentEvent={latest} onClose={vi.fn()} />);
    expect(screen.getByRole("spinbutton")).toHaveValue(123);
    expect(screen.getByRole("alert")).toHaveTextContent("Mercado temporariamente suspenso");
    expect(screen.getByRole("button", { name: "Confirmar palpite" })).toBeDisabled();
    await waitFor(() => expect(mocks.listPools).toHaveBeenCalledTimes(1));
  });

  it("não permite opção suspensa mesmo que outro resultado do mercado esteja aberto", async () => {
    render(<PredictionComposer draft={{ ...draft, option: { ...draft.option, active: false } }} onClose={vi.fn()} />);
    expect(screen.getByRole("alert")).toHaveTextContent("opção está temporariamente suspensa");
    expect(screen.getByRole("button", { name: "Confirmar palpite" })).toBeDisabled();
    await waitFor(() => expect(mocks.listPools).toHaveBeenCalled());
  });

  it("liga participa automaticamente e só bolão social aparece para vincular", async () => {
    mocks.listPools.mockResolvedValue([
      { id: 1, name: "Bolão de amigos", poolType: "POOL", status: "OPEN", joined: true },
      { id: 2, name: "Liga da plataforma", poolType: "LEAGUE", status: "OPEN", joined: true },
      { id: 3, name: "Outro grupo", poolType: "POOL", status: "OPEN", joined: false },
    ]);
    render(<PredictionComposer draft={draft} onClose={vi.fn()} />);
    expect(await screen.findByRole("option", { name: "Bolão de amigos" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Liga da plataforma" })).not.toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Outro grupo" })).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "1" } });
    fireEvent.click(screen.getByRole("button", { name: "Confirmar palpite" }));
    await screen.findByText("Sua leitura está registrada");
    expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({ poolId: 1 }));
  });
});
