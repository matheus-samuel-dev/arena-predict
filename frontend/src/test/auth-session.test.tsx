import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider, useAuth } from "../contexts/AuthContext";
import { ToastProvider } from "../contexts/ToastContext";
import { adminApi, ApiError, sessionStorage } from "../services/api";

function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ "content-type": "application/json" }),
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as Response;
}

function AuthHarness() {
  const { session, authenticating, demoLogin } = useAuth();
  return (
    <>
      <output aria-label="Perfil da sessão">{session?.role ?? "SEM_SESSAO"}</output>
      <button type="button" disabled={authenticating} onClick={() => void demoLogin("PARTICIPANT")}>Participante</button>
      <button type="button" disabled={authenticating} onClick={() => void demoLogin("ADMIN")}>Administrador</button>
    </>
  );
}

function renderAuth() {
  return render(<ToastProvider><AuthProvider><AuthHarness /></AuthProvider></ToastProvider>);
}

describe("troca segura da sessão demonstrativa", () => {
  beforeEach(() => {
    localStorage.clear();
    window.sessionStorage.clear();
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("limpa a sessão anterior e substitui participante por administrador", async () => {
    const sessionsSeenAtRequest: Array<string | null> = [];
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      const profile = (JSON.parse(String(init?.body)) as { profile: "PARTICIPANT" | "ADMIN" }).profile;
      sessionsSeenAtRequest.push(sessionStorage.read()?.token ?? null);
      return profile === "ADMIN"
        ? jsonResponse({ token: "admin-token", userId: 1, name: "Admin", email: "admin@arenapredict.com", role: "ADMIN" })
        : jsonResponse({ token: "participant-token", userId: 2, name: "Jogador", email: "jogador@arenapredict.com", role: "PARTICIPANTE" });
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    renderAuth();

    await user.click(screen.getByRole("button", { name: "Participante" }));
    await waitFor(() => expect(sessionStorage.read()).toMatchObject({ token: "participant-token", role: "PARTICIPANTE" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Administrador" })).toBeEnabled());

    await user.click(screen.getByRole("button", { name: "Administrador" }));
    await waitFor(() => expect(sessionStorage.read()).toMatchObject({ token: "admin-token", role: "ADMIN" }));

    expect(screen.getByLabelText("Perfil da sessão")).toHaveTextContent("ADMIN");
    expect(sessionsSeenAtRequest).toEqual([null, null]);
  });

  it("ignora um 401 atrasado da sessão antiga depois da troca de perfil", async () => {
    let resolveOldRequest!: (response: Response) => void;
    const oldRequest = new Promise<Response>((resolve) => {
      resolveOldRequest = resolve;
    });
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/admin/dashboard")) return oldRequest;
      const profile = (JSON.parse(String(init?.body)) as { profile: "PARTICIPANT" | "ADMIN" }).profile;
      return Promise.resolve(profile === "ADMIN"
        ? jsonResponse({ token: "new-admin-token", userId: 1, name: "Admin", email: "admin@arenapredict.com", role: "ADMIN" })
        : jsonResponse({ token: "old-participant-token", userId: 2, name: "Jogador", email: "jogador@arenapredict.com", role: "PARTICIPANTE" }));
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    renderAuth();

    await user.click(screen.getByRole("button", { name: "Participante" }));
    await waitFor(() => expect(sessionStorage.read()?.token).toBe("old-participant-token"));
    const staleRequest = adminApi.dashboard();
    expect((fetchMock.mock.calls.at(-1)?.[1]?.headers as Headers).get("Authorization")).toBe("Bearer old-participant-token");

    await user.click(screen.getByRole("button", { name: "Administrador" }));
    await waitFor(() => expect(sessionStorage.read()?.token).toBe("new-admin-token"));
    resolveOldRequest(jsonResponse({ message: "token antigo" }, 401));

    await expect(staleRequest).rejects.toMatchObject({ status: 401 } satisfies Partial<ApiError>);
    expect(sessionStorage.read()).toMatchObject({ token: "new-admin-token", role: "ADMIN" });
    expect(screen.getByLabelText("Perfil da sessão")).toHaveTextContent("ADMIN");
    expect(screen.queryByText("Sua sessão expirou. Entre novamente para continuar.")).not.toBeInTheDocument();
  });
});
