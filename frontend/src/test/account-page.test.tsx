import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ProfilePage } from "../pages/AccountPages";

const mocks = vi.hoisted(() => ({
  getProfile: vi.fn(),
  getDashboard: vi.fn(),
  listAchievements: vi.fn(),
  updateProfile: vi.fn(),
  updateLocalUser: vi.fn(),
  notify: vi.fn(),
}));

vi.mock("../services/api", () => ({
  asList: (value: unknown) => Array.isArray(value) ? value : [],
  profileApi: {
    get: mocks.getProfile,
    update: mocks.updateProfile,
    changePassword: vi.fn(),
    preferences: vi.fn(),
  },
  dashboardApi: { get: mocks.getDashboard },
  achievementsApi: { list: mocks.listAchievements },
}));

const profileFixture = {
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
  xp: 11_305,
  points: 4_489,
  createdAt: "2026-01-12T14:00:00Z",
};

vi.mock("../contexts/AuthContext", () => ({
  useAuth: () => ({ updateLocalUser: mocks.updateLocalUser }),
}));

vi.mock("../contexts/ToastContext", () => ({
  useToast: () => ({ notify: mocks.notify }),
}));

describe("página Conta", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.getProfile.mockResolvedValue(profileFixture);
    mocks.updateProfile.mockImplementation(async (payload) => ({ ...profileFixture, ...payload }));
    mocks.getDashboard.mockResolvedValue({
      activePredictions: 4,
      finishedPredictions: 6,
      wonPredictions: 5,
      accuracy: 0.75,
      streak: 2,
      bestStreak: 4,
      rankingPosition: 2,
      nextLevelXp: 15_000,
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

    expect(await screen.findByRole("heading", { name: "Minha conta" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Beatriz Nunes" })).toBeInTheDocument();
    expect(screen.getByText(/@beatriz\.nunes/)).toHaveTextContent("beatriz.nunes@arenapredict.com");
    expect(screen.getByText(/Membro desde/)).toBeInTheDocument();
    expect(screen.getByRole("progressbar", { name: "Progresso para o nível 4" })).toHaveAttribute("aria-valuenow", "1305");
    expect(screen.getByText("Palpites vencedores")).toBeInTheDocument();
    expect(screen.getByText("Melhor sequência: 4")).toBeInTheDocument();
    expect(screen.getByText("Palmeiras x Flamengo")).toBeInTheDocument();
    expect(screen.getByText("Leitura certeira")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ver catálogo" })).toHaveAttribute("href", "/achievements");
    expect(container.querySelector(".profile-hero img")).toHaveAttribute("src", "/assets/avatars/beatriz-nunes.webp");
    expect(container.querySelector(".user-avatar__initials")).not.toBeInTheDocument();
  });

  it("persiste a escolha da galeria uma única vez e atualiza a sessão do header", async () => {
    let completeUpdate: ((value: unknown) => void) | undefined;
    mocks.updateProfile.mockImplementationOnce(() => new Promise((resolve) => {
      completeUpdate = resolve;
    }));
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/account"]}><ProfilePage /></MemoryRouter>);

    const option = await screen.findByRole("button", { name: "Selecionar: Avatar Arena 4" });
    await user.dblClick(option);

    expect(mocks.updateProfile).toHaveBeenCalledTimes(1);
    expect(mocks.updateProfile).toHaveBeenCalledWith(expect.objectContaining({
      avatarUrl: "/assets/avatars/camila-rocha.webp",
      email: "beatriz.nunes@arenapredict.com",
    }));

    completeUpdate?.({ ...profileFixture, avatarUrl: "/assets/avatars/camila-rocha.webp" });
    await waitFor(() => expect(mocks.updateLocalUser).toHaveBeenCalledWith(expect.objectContaining({
      avatarUrl: "/assets/avatars/camila-rocha.webp",
    })));
    expect(mocks.notify).toHaveBeenCalledWith("Avatar atualizado em toda a ArenaPredict.", "success");
    expect(screen.getByRole("button", { name: "Avatar selecionado: Avatar Arena 4" })).toHaveAttribute("aria-pressed", "true");
  });

  it("mantém o avatar atual e libera nova tentativa quando a persistência falha", async () => {
    mocks.updateProfile.mockRejectedValueOnce(new Error("Perfil temporariamente indisponível."));
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/account"]}><ProfilePage /></MemoryRouter>);

    const option = await screen.findByRole("button", { name: "Selecionar: Avatar Arena 5" });
    await user.click(option);

    await waitFor(() => expect(mocks.notify).toHaveBeenCalledWith("Perfil temporariamente indisponível.", "error"));
    expect(option).toBeEnabled();
    expect(screen.getByRole("button", { name: "Avatar selecionado: Avatar Arena 3" })).toHaveAttribute("aria-pressed", "true");
    expect(mocks.updateLocalUser).not.toHaveBeenCalled();
  });

  it("apresenta erro recuperável quando o perfil não carrega", async () => {
    mocks.getProfile
      .mockRejectedValueOnce(new Error("Não foi possível consultar sua conta."))
      .mockResolvedValueOnce(profileFixture);
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={["/account"]}><ProfilePage /></MemoryRouter>);

    expect(await screen.findByText("Não foi possível consultar sua conta.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tentar novamente" }));

    expect(await screen.findByRole("heading", { name: "Beatriz Nunes" })).toBeInTheDocument();
    expect(mocks.getProfile).toHaveBeenCalledTimes(2);
  });
});
