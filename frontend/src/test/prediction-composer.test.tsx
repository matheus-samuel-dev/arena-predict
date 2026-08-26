import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PredictionComposer } from "../components/PredictionComposer";
import type { PredictionDraft } from "../types";

const mocks = vi.hoisted(() => ({
  create: vi.fn(),
  listPools: vi.fn(),
  notify: vi.fn(),
  refreshWallet: vi.fn(),
  refreshNotifications: vi.fn(),
}));

vi.mock("../services/api", () => ({
  createIdempotencyKey: () => "prediction-intent-fixed",
  poolsApi: { list: mocks.listPools },
  predictionsApi: { create: mocks.create },
}));

vi.mock("../contexts/AppDataContext", () => ({
  useAppData: () => ({
    wallet: { balance: 1_000 },
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
  market: { id: 8, name: "Vencedor da partida", minimumPoints: 20 },
  option: { id: 13, label: "Boston Celtics", multiplier: 1.72 },
} as unknown as PredictionDraft;

describe("confirmação do palpite", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
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
});
