import { cleanup, configure, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "../App";
import { adminNavigation, playerNavigation } from "../components/AppShell";
import { sessionStorage } from "../services/api";

const state = vi.hoisted(() => ({ admin: false }));
configure({ asyncUtilTimeout: 5000 });
vi.mock("../contexts/AuthContext", () => ({ useAuth: () => {
  const user = { userId: 2, name: "Visitante Demo", email: "demo@example.test", role: state.admin ? "ADMIN" : "PARTICIPANTE", demoProfile: state.admin ? "ADMIN" : "PARTICIPANT" };
  return { user, session: user, initializing: false, authenticating: false, updateLocalUser: vi.fn(), logout: vi.fn() };
} }));
vi.mock("../contexts/AppDataContext", () => ({
  AppDataProvider: ({ children }: { children: React.ReactNode }) => children,
  useAppData: () => ({ wallet: { balance: 1000 }, notifications: [], unreadCount: 0, loading: false, refreshWallet: vi.fn(), refreshNotifications: vi.fn() }),
}));
vi.mock("../contexts/ToastContext", () => ({ useToast: () => ({ notify: vi.fn() }) }));

const resources = adminNavigation.flatMap((group) => group.items).filter((item) => item.to !== "/admin");
const playerPages = [
  ["/app", "Visão geral"], ["/events", "Eventos"], ["/live", "Ao vivo"], ["/predictions", "Meus palpites"],
  ["/pools", "Bolões"], ["/leagues", "Ligas"], ["/rankings", "Rankings"], ["/statistics", "Estatísticas"],
  ["/challenges", "Desafios"], ["/achievements", "Conquistas"], ["/community", "Comunidade"], ["/account", "Minha conta"],
] as const;

function show(path: string) { return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>); }

describe("produto completo para perfis Demo", () => {
  beforeEach(() => {
    state.admin = false;
    localStorage.clear(); window.sessionStorage.clear();
    sessionStorage.save({ token: "test-jwt", userId: 2, name: "Visitante Demo", email: "demo@example.test", role: "PARTICIPANTE", demoProfile: "PARTICIPANT" });
    vi.stubGlobal("fetch", vi.fn(async (url: string) => {
      const path = new URL(String(url), "http://localhost").pathname;
      const body = path.endsWith("/profile") ? { name: "Visitante Demo", email: "demo@example.test", userId: 2, role: "PARTICIPANTE", favoriteSports: [], preferences: {} }
        : path.endsWith("/dashboard") ? { level: 1, points: 1000, recentPredictions: [], featuredEvents: [], liveEvents: [], upcomingEvents: [] }
        : path.includes("sports-sync") ? { status: "UNCONFIGURED", provider: "PANDASCORE" }
        : path.endsWith("/wallet") ? { balance: 1000 } : [];
      return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
    }));
  });
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it.each(playerPages)("carrega o componente real da rota %s", async (path, title) => {
    show(path);
    await waitFor(() => expect(document.querySelector("#main-content")?.textContent).toBeTruthy());
    expect(await screen.findByRole("navigation", { name: "Navegação principal" })).toBeInTheDocument();
    await waitFor(() => expect(document.querySelector("#main-content")?.querySelector("h1")).toBeTruthy());
    expect(document.querySelector("#main-content")?.textContent).not.toMatch(/Essa arquibancada não existe|Acesso restrito|Carregando sua experiência/);
    expect(screen.getAllByText(title).length).toBeGreaterThan(0);
  });

  it.each(resources)("carrega o backoffice %s com consulta e sem ações genéricas Demo", async ({ to }) => {
    state.admin = true;
    show(to);
    await waitFor(() => expect(document.querySelector("#main-content")?.textContent).toContain("Consulta administrativa"));
    expect(document.querySelector("#main-content")?.textContent).not.toMatch(/Acesso restrito|Essa arquibancada/);
    expect(screen.queryByRole("button", { name: /^Criar / })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Simular resultado e Reset Demo" })).toHaveAttribute("href", "/demo");
  });

  it("menus preservam todos os domínios, seções recolhíveis e destinos válidos", async () => {
    show("/events");
    const navigation = await screen.findByRole("navigation", { name: "Navegação principal" });
    fireEvent.click(within(navigation).getByRole("button", { name: "Comunidade e conta" }));
    for (const item of playerNavigation.flatMap((group) => group.items)) {
      expect(within(navigation).getByRole("link", { name: item.label })).toHaveAttribute("href", item.to);
      expect(playerPages.map(([path]) => path)).toContain(item.to);
    }
    expect(document.querySelector(".demo-banner")).toBeNull();
    expect(document.querySelector(".demo-browse-notice")).toBeNull();
  });

  it("participante não entra em rotas administrativas por URL direta", async () => {
    show("/admin/users");
    expect(await screen.findByRole("heading", { name: "Acesso restrito" })).toBeInTheDocument();
  });
});
