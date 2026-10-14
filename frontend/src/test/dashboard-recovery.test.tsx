import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DashboardPage } from "../pages/DashboardPage";

const mocks = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("../services/api", () => ({ dashboardApi: { get: mocks.get } }));
vi.mock("../contexts/AuthContext", () => ({ useAuth: () => ({ user: { name: "Jogador Demo" } }) }));
vi.mock("../components/PredictionComposer", () => ({ PredictionComposer: () => null }));

const fixture = {
  points: 5000, level: 1, levelTitle: "Treino isolado", xp: 0, nextLevelXp: 0,
  rankingPosition: 1, activePredictions: 0, finishedPredictions: 0, accuracy: 0,
  featuredEvents: [], liveEvents: [], upcomingEvents: [], recentPredictions: [], activePools: [],
  weeklyRanking: [{ position: 1, userId: 2, name: "Jogador Demo", points: 0, currentUser: true }],
  challenges: [], recentAchievements: [], performance: [], demoMode: true,
};

beforeEach(() => { mocks.get.mockReset(); });
afterEach(cleanup);

describe("participant dashboard recovery", () => {
  it("retry replaces the timeout with wallet indicators, ranking and functioning navigation", async () => {
    mocks.get.mockRejectedValueOnce(new Error("A solicitação demorou mais que o esperado. Tente novamente."));
    mocks.get.mockResolvedValueOnce(fixture);
    render(<MemoryRouter><DashboardPage /></MemoryRouter>);
    expect(await screen.findByText("Não foi possível carregar")).toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    expect(await screen.findByRole("heading", { name: "Boa leitura, Jogador." })).toBeVisible();
    expect(screen.getByText("5.000 pts")).toBeVisible();
    expect(screen.getByText("Ranking semanal")).toBeVisible();
    expect(screen.queryByText("Não foi possível carregar")).not.toBeInTheDocument();
    expect(screen.getAllByRole("link").some(link => link.getAttribute("href") === "/predictions")).toBe(true);
    expect(mocks.get).toHaveBeenCalledTimes(2);
  });

  it("passes cancellation to the dashboard API when navigation unmounts it", async () => {
    mocks.get.mockImplementation((_signal?: AbortSignal) => new Promise(() => undefined));
    const { unmount } = render(<MemoryRouter><DashboardPage /></MemoryRouter>);
    await waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(1));
    const signal = mocks.get.mock.calls[0][0] as AbortSignal;
    expect(signal.aborted).toBe(false); unmount(); expect(signal.aborted).toBe(true);
  });
});
