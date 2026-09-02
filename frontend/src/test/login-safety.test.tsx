import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LoginPage, postLoginDestination } from "../pages/LoginPage";
import type { AuthSession } from "../types";

const mocks = vi.hoisted(() => ({
  demoEnabled: false,
  authenticating: false,
  login: vi.fn(),
  demoLogin: vi.fn(),
  register: vi.fn(),
  notify: vi.fn(),
}));

vi.mock("../app/branding", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../app/branding")>();
  return {
    ...actual,
    get isExplicitDemoMode() {
      return mocks.demoEnabled;
    },
  };
});

vi.mock("../contexts/AuthContext", () => ({
  useAuth: () => ({
    session: null,
    user: null,
    initializing: false,
    authenticating: mocks.authenticating,
    login: mocks.login,
    demoLogin: mocks.demoLogin,
    register: mocks.register,
    logout: vi.fn(),
    refreshUser: vi.fn(),
    updateLocalUser: vi.fn(),
  }),
}));

vi.mock("../contexts/ToastContext", () => ({
  useToast: () => ({ notify: mocks.notify }),
}));

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={["/login"]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/app" element={<h1>Área do participante</h1>} />
        <Route path="/admin" element={<h1>Área administrativa</h1>} />
      </Routes>
    </MemoryRouter>,
  );
}

function session(role: AuthSession["role"]): AuthSession {
  return {
    token: `${role.toLowerCase()}-token`,
    userId: role === "ADMIN" ? 1 : 2,
    name: role === "ADMIN" ? "Administrador Demo" : "Jogador Demo",
    email: role === "ADMIN" ? "admin@example.test" : "participant@example.test",
    role,
  };
}

describe("segurança e demonstração do login", () => {
  beforeEach(() => {
    mocks.demoEnabled = false;
    mocks.authenticating = false;
    mocks.login.mockReset();
    mocks.demoLogin.mockReset();
    mocks.register.mockReset();
    mocks.notify.mockReset();
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("não redireciona participantes para rotas administrativas herdadas", () => {
    expect(postLoginDestination("PARTICIPANTE", "/admin/results")).toBe("/app");
    expect(postLoginDestination("PARTICIPANTE", "/events")).toBe("/events");
    expect(postLoginDestination("ADMIN", "/events")).toBe("/admin");
    expect(postLoginDestination("PARTICIPANTE", "https://site-invalido.example")).toBe("/app");
  });

  it("não oferece credenciais rápidas fora do modo demo explícito", () => {
    renderLogin();

    expect(screen.queryByText("Acesso rápido de demonstração")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Entrar na demonstração como participante" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Entrar na demonstração como administrador" })).not.toBeInTheDocument();
  });

  it.each([
    ["participant", "PARTICIPANTE", "Área do participante", "participante"],
    ["admin", "ADMIN", "Área administrativa", "administrador"],
  ] as const)(
    "solicita somente o perfil previsto no acesso demonstrativo de %s",
    async (profile, role, destination, notificationProfile) => {
      mocks.demoEnabled = true;
      mocks.demoLogin.mockResolvedValue(session(role));
      const user = userEvent.setup();
      renderLogin();

      await user.click(screen.getByRole("button", {
        name: profile === "admin" ? "Entrar na demonstração como administrador" : "Entrar na demonstração como participante",
      }));

      expect(mocks.demoLogin).toHaveBeenCalledTimes(1);
      expect(mocks.demoLogin).toHaveBeenCalledWith(profile === "admin" ? "ADMIN" : "PARTICIPANT");
      expect(mocks.login).not.toHaveBeenCalled();
      expect(await screen.findByRole("heading", { name: destination })).toBeInTheDocument();
      expect(mocks.notify).toHaveBeenCalledWith(
        `Acesso demonstrativo como ${notificationProfile} iniciado.`,
        "success",
      );
    },
  );

  it("associa erros ao campo, move o foco e permite mostrar ou ocultar a senha", async () => {
    vi.spyOn(window, "requestAnimationFrame").mockImplementation((callback) => {
      callback(performance.now());
      return 1;
    });
    const user = userEvent.setup();
    renderLogin();

    const email = screen.getByRole("textbox", { name: "E-mail" });
    const password = screen.getByLabelText("Senha");
    await user.click(screen.getByRole("button", { name: "Entrar na Arena" }));

    expect(screen.getByRole("alert")).toHaveTextContent("Informe seu e-mail.");
    expect(email).toHaveAttribute("aria-invalid", "true");
    await waitFor(() => expect(email).toHaveFocus());

    await user.type(email, "email-invalido");
    await user.click(screen.getByRole("button", { name: "Entrar na Arena" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Informe um e-mail válido");
    expect(mocks.login).not.toHaveBeenCalled();

    expect(password).toHaveAttribute("type", "password");
    await user.click(screen.getByRole("button", { name: "Mostrar senha" }));
    expect(password).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Ocultar senha" })).toHaveAttribute("aria-pressed", "true");
  });

  it("desabilita mudanças e submissões enquanto autentica para evitar envio duplicado", () => {
    mocks.demoEnabled = true;
    mocks.authenticating = true;
    renderLogin();

    expect(screen.getByRole("tabpanel")).toHaveAttribute("aria-busy", "true");
    expect(screen.getByRole("button", { name: "Entrando…" })).toBeDisabled();
    expect(screen.getByRole("tab", { name: "Entrar" })).toBeDisabled();
    expect(screen.getByRole("tab", { name: "Criar conta" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Entrar na demonstração como participante" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Entrar na demonstração como administrador" })).toBeDisabled();
  });
});
