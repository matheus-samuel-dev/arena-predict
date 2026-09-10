import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ScoreModal, validateResultFields } from "../pages/AdminPages";
import type { ResultField } from "../types";

const mocks = vi.hoisted(() => ({ result: vi.fn(), classification: vi.fn(), notify: vi.fn() }));
vi.mock("../services/api", async (original) => {
  const actual = await original<typeof import("../services/api")>();
  return { ...actual, createIdempotencyKey: () => "result-intent", adminApi: { ...actual.adminApi, recordEventResult: mocks.result, recordEventClassification: mocks.classification } };
});
vi.mock("../contexts/ToastContext", () => ({ useToast: () => ({ notify: mocks.notify }) }));

const metric = (key: string, label: string, group: string): ResultField => ({ key, label, group, type: "number", required: true });
const base = { id: 9, format: "BO3", status: "LIVE", title: "Evento de validação", homeCompetitor: { name: "Participante A" }, awayCompetitor: { name: "Participante B" } };
function confirm() { fireEvent.click(screen.getByRole("checkbox", { name: /Revisei o evento/ })); }
function save() { fireEvent.submit(screen.getByRole("button", { name: /Salvar (placar|classificação)/ }).closest("form")!); }

describe("registro de resultados por modalidade", () => {
  beforeEach(() => { Object.values(mocks).forEach((mock) => mock.mockReset()); mocks.result.mockResolvedValue({}); mocks.classification.mockResolvedValue({}); });
  afterEach(cleanup);

  it.each([
    ["futebol", metric("cornersHome", "Escanteios do mandante", "Escanteios")],
    ["basquete", metric("q1Home", "Pontos no primeiro quarto", "Quartos")],
    ["tênis", metric("gamesHome", "Games do jogador 1", "Games")],
    ["CS2", metric("map1Home", "Rounds do mapa 1", "Mapa 1")],
    ["LoL", metric("killsHome", "Abates da equipe 1", "Abates")],
  ])("usa o schema de %s sem renderizar campos das demais modalidades", (_, field) => {
    render(<ScoreModal record={{ ...base, resultSchema: [field] }} onClose={vi.fn()} onSaved={vi.fn()} />);
    expect(screen.getByLabelText(new RegExp(field.label))).toBeVisible();
    expect(screen.getAllByRole("spinbutton")).toHaveLength(3);
    expect(screen.queryByLabelText(/Safety car/)).not.toBeInTheDocument();
  });

  it("expande a seção com o primeiro dado ausente, mostra erro e leva o foco", () => {
    render(<ScoreModal record={{ ...base, resultSchema: [metric("halfHome", "Placar do intervalo", "Intervalo"), metric("cornersHome", "Escanteios do mandante", "Escanteios")], resultData: { halfHome: "1" } }} onClose={vi.fn()} onSaved={vi.fn()} />);
    confirm();
    save();
    expect(mocks.result).not.toHaveBeenCalled();
    const field = screen.getByLabelText(/Escanteios do mandante/);
    expect(field.closest("details")).toHaveAttribute("open");
    expect(field).toHaveFocus();
    expect(field).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByRole("alert")).toHaveTextContent("Preencha os dados necessários");
  });

  it("envia placar e métricas no mesmo comando de liquidação", async () => {
    render(<ScoreModal record={{ ...base, resultSchema: [metric("map1Home", "Rounds do mapa 1", "Mapa 1")] }} onClose={vi.fn()} onSaved={vi.fn()} />);
    fireEvent.change(screen.getByLabelText("Placar de Participante A"), { target: { value: "2" } });
    fireEvent.change(screen.getByLabelText("Placar de Participante B"), { target: { value: "1" } });
    fireEvent.change(screen.getByLabelText(/Rounds do mapa 1/), { target: { value: "13" } });
    confirm(); save();
    await waitFor(() => expect(mocks.result).toHaveBeenCalledWith(9, { homeScore: 2, awayScore: 1, resultData: { map1Home: "13" }, finishEvent: true, settleMarkets: true }, "result-intent"));
  });

  it("permite salvar placar parcial sem exigir nem liquidar métricas ausentes", async () => {
    render(<ScoreModal record={{ ...base, resultSchema: [metric("map1Home", "Rounds do mapa 1", "Mapa 1")] }} onClose={vi.fn()} onSaved={vi.fn()} />);
    fireEvent.click(screen.getByRole("checkbox", { name: "Marcar evento como encerrado" }));
    expect(screen.getByRole("checkbox", { name: "Liquidar mercados com este resultado" })).toBeDisabled();
    confirm(); save();
    await waitFor(() => expect(mocks.result).toHaveBeenCalledWith(9, expect.objectContaining({ finishEvent: false, settleMarkets: false, resultData: {} }), "result-intent"));
  });

  it("impede dois salvamentos e alterações enquanto o resultado está sendo processado", async () => {
    let complete!: (value: unknown) => void;
    mocks.result.mockReturnValue(new Promise((resolve) => { complete = resolve; }));
    const close = vi.fn();
    render(<ScoreModal record={base} onClose={close} onSaved={vi.fn()} />);
    confirm(); save(); save();
    expect(mocks.result).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText("Placar de Participante A")).toBeDisabled();
    fireEvent.keyDown(document, { key: "Escape" });
    expect(close).not.toHaveBeenCalled();
    await act(async () => complete({}));
    expect(close).toHaveBeenCalledTimes(1);
  });

  it("F1 solicita classificação e dados da corrida, sem placar A × B", async () => {
    const schema: ResultField[] = [{ key: "safetyCar", label: "Houve safety car", group: "Corrida", type: "select", required: true, options: [{ value: "YES", label: "Sim" }, { value: "NO", label: "Não" }] }];
    render(<ScoreModal record={{ ...base, format: "RACE", resultSchema: schema, participants: [{ competitor: { id: 1, name: "Piloto A" }, displayOrder: 0 }, { competitor: { id: 2, name: "Piloto B" }, displayOrder: 1 }] }} onClose={vi.fn()} onSaved={vi.fn()} />);
    expect(screen.queryByLabelText("Placar de Participante A")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Posição de Piloto A"), { target: { value: "1" } });
    fireEvent.change(screen.getByLabelText("Posição de Piloto B"), { target: { value: "1" } });
    fireEvent.change(screen.getByLabelText(/Houve safety car/), { target: { value: "YES" } });
    confirm(); save();
    expect(screen.getByRole("alert")).toHaveTextContent("posição diferente");
    expect(mocks.classification).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText("Posição de Piloto B"), { target: { value: "2" } });
    save();
    await waitFor(() => expect(mocks.classification).toHaveBeenCalledWith(9, expect.objectContaining({ participants: [{ competitorId: 1, displayOrder: 0, position: 1, scoreLabel: null }, { competitorId: 2, displayOrder: 1, position: 2, scoreLabel: null }], resultData: { safetyCar: "YES" }, settleMarkets: true }), "result-intent"));
  });

  it("rejeita estatísticas decimais, fora de faixa e seleções desconhecidas", () => {
    const fields: ResultField[] = [metric("rounds", "Rounds", "Mapa"), { key: "winner", label: "Vencedor", group: "Mapa", type: "select", required: true, options: [{ value: "HOME", label: "Participante A" }] }];
    expect(Object.keys(validateResultFields(fields, { rounds: "1.5", winner: "AWAY" }, true))).toEqual(["rounds", "winner"]);
    expect(validateResultFields(fields, { rounds: "10001", winner: "HOME" }, true)).toHaveProperty("rounds");
    expect(validateResultFields(fields, { rounds: "13", winner: "HOME" }, true)).toEqual({});
  });
});
