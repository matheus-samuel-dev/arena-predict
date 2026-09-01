import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RankingsPage } from "../pages/PerformancePages";

const rankingRows = [
  { position: 1, userId: 1, name: "Ana Ribeiro", points: 908, hits: 5, predictions: 6, accuracy: 83.33, streak: 5, avatarUrl: "/assets/avatars/ana-ribeiro.webp" },
  { position: 2, userId: 2, name: "Beatriz Nunes", points: 732, hits: 4, predictions: 6, accuracy: 66.67, streak: 3, avatarUrl: "/assets/avatars/beatriz-nunes.webp" },
  { position: 3, userId: 3, name: "Camila Rocha", points: 693, hits: 4, predictions: 6, accuracy: 66.67, streak: 2, avatarUrl: "/assets/avatars/camila-rocha.webp" },
];

const { listRanking, listSports } = vi.hoisted(() => ({
  listRanking: vi.fn(),
  listSports: vi.fn(),
}));

listRanking.mockResolvedValue(rankingRows);
listSports.mockResolvedValue([{ id: 7, name: "Counter-Strike 2", slug: "CS2" }]);

vi.mock("../services/api", () => ({
  rankingsApi: { list: (filters: unknown) => listRanking(filters) },
  catalogApi: { sports: () => listSports() },
  asList: (value: unknown) => Array.isArray(value) ? value : [],
  achievementsApi: { list: vi.fn() },
  challengesApi: { list: vi.fn() },
  predictionsApi: { list: vi.fn() },
}));

describe("RankingsPage", () => {
  afterEach(() => {
    cleanup();
    listRanking.mockClear();
    listSports.mockClear();
  });

  it("organiza posição, identidade, métricas e pontuação em regiões próprias", async () => {
    const { container } = render(<RankingsPage />);
    await screen.findAllByText("Ana Ribeiro");

    expect(container.querySelectorAll(".podium__item")).toHaveLength(3);
    expect(container.querySelectorAll(".podium__rank")).toHaveLength(3);
    expect(container.querySelectorAll(".podium__metrics")).toHaveLength(3);
    expect(container.querySelectorAll(".podium__score")).toHaveLength(3);
    expect(container.querySelector(".podium__item--1 .podium__position")).toHaveTextContent("#1");
    expect(container.querySelector(".podium__item--1 .podium__score")).toHaveTextContent("908 pts");
  });

  it("filtra participantes sem acento e consulta período e modalidade no backend", async () => {
    render(<RankingsPage />);
    const table = await screen.findByRole("table", { name: "Classificação do ranking" });
    const search = screen.getByRole("searchbox", { name: "Buscar participante no ranking" });
    fireEvent.change(search, { target: { value: "beatriz" } });

    expect(within(table).getByText("Beatriz Nunes")).toBeInTheDocument();
    expect(within(table).queryByText("Ana Ribeiro")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("1 participante encontrado");

    fireEvent.click(screen.getByRole("button", { name: "Mensal" }));
    await waitFor(() => expect(listRanking).toHaveBeenCalledWith(expect.objectContaining({ period: "MONTHLY" })));
    fireEvent.change(screen.getByRole("combobox", { name: "Filtrar ranking por modalidade" }), { target: { value: "CS2" } });
    await waitFor(() => expect(listRanking).toHaveBeenCalledWith(expect.objectContaining({ period: "MONTHLY", sport: "CS2" })));
  });
});
