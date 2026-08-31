import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ProfilePage } from "../pages/AccountPages";

const mocks = vi.hoisted(() => ({
  getProfile: vi.fn(),
  getDashboard: vi.fn(),
  listAchievements: vi.fn(),
  updateLocalUser: vi.fn(),
  notify: vi.fn(),
}));

vi.mock("../services/api", () => ({
  asList: (value: unknown) => Array.isArray(value) ? value : [],
  profileApi: {
    get: mocks.getProfile,
    update: vi.fn(),
    changePassword: vi.fn(),
    preferences: vi.fn(),
  },
  dashboardApi: { get: mocks.getDashboard },
  achievementsApi: { list: mocks.listAchievements },
}));

vi.mock("../contexts/AuthContext", () => ({
  useAuth: () => ({ updateLocalUser: mocks.updateLocalUser }),
}));

vi.mock("../contexts/ToastContext", () => ({
  useToast: () => ({ notify: mocks.notify }),
}));

describe("página Conta", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.getProfile.mockResolvedValue({
      userId: 7,
      name: "Beatriz Nunes",
      email: "beatriz.nunes@arenapredict.com",
      role: "PARTICIPANTE",
      avatarUrl: "/assets/avatars/beatriz-nunes.webp",
      bio: "Analista esportiva.",
      favoriteSports: ["FOOTBALL"],
      notifications: true,
      publicProfile: true,
      level: 3,
      xp: 1305,
      points: 4489,
    });
    mocks.getDashboard.mockResolvedValue({
      activePredictions: 4,
      finishedPredictions: 6,
      accuracy: 0.75,
      streak: 2,
      rankingPosition: 2,
      recentPredictions: [{
        id: 91,
        eventTitle: "Palmeiras x Flamengo",
        optionName: "Palmeiras",
        stakePoints: 80,
        status: "ACTIVE",
        placedAt: "2026-08-27T18:00:00Z",
      }],
    });
    mocks.listAchievements.mockResolvedValue([{
      id: 3,
      name: "Leitura certeira",
      description: "Acerte seu primeiro mercado.",
      pointsReward: 200,
      unlocked: true,
    }]);
  });

  afterEach(() => cleanup());

  it("apresenta identidade visual, desempenho e atividade sem deixar a rota vazia", async () => {
    const { container } = render(<MemoryRouter initialEntries={["/account"]}><ProfilePage /></MemoryRouter>);

    expect(await screen.findByRole("heading", { name: "Conta" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Beatriz Nunes" })).toBeInTheDocument();
    expect(screen.getByText(/@beatriz\.nunes/)).toHaveTextContent("beatriz.nunes@arenapredict.com");
    expect(screen.getByText("Palmeiras x Flamengo")).toBeInTheDocument();
    expect(screen.getByText("Leitura certeira")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ver catálogo" })).toHaveAttribute("href", "/achievements");
    expect(container.querySelector(".profile-hero img")).toHaveAttribute("src", "/assets/avatars/beatriz-nunes.webp");
    expect(container.querySelector(".user-avatar__initials")).not.toBeInTheDocument();
  });
});
