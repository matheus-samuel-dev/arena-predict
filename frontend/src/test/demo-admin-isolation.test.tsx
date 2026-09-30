import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AdminResourcePage } from "../pages/AdminPages";

const mocks = vi.hoisted(() => ({ list: vi.fn(), notify: vi.fn() }));
vi.mock("../services/api", async (original) => {
  const actual = await original<typeof import("../services/api")>();
  return { ...actual, adminApi: { ...actual.adminApi, list: mocks.list } };
});
vi.mock("../contexts/ToastContext", () => ({ useToast: () => ({ notify: mocks.notify }) }));
const controlled = { id: 77, name: "Rodada protegida", title: "Rodada protegida", demoManaged: true, demo: true, status: "LIVE", startsAt: "2026-09-29T12:00:00Z" };
function show(resource: string) {
  render(<MemoryRouter initialEntries={[`/admin/${resource}`]}><Routes><Route path="/admin/:resource" element={<AdminResourcePage />} /></Routes></MemoryRouter>);
}

describe("catálogo administrativo com demonstração controlada", () => {
  beforeEach(() => { mocks.list.mockReset(); mocks.notify.mockReset(); });
  afterEach(cleanup);

  it.each(["events", "championships", "markets", "results"])("mantém %s controlados somente leitura", async (resource) => {
    mocks.list.mockImplementation(async (requested: string) => requested === resource ? [controlled] : []);
    show(resource);
    const row = (await screen.findByText("Somente leitura · Demo guiada")).closest('[role="row"]')! as HTMLElement;
    expect(within(row).queryByRole("button")).not.toBeInTheDocument();
    expect(within(row).getByRole("link", { name: "Ir para a demonstração" })).toHaveAttribute("href", "/demo");
  });

  it("não oferece campeonato controlado para novos eventos comuns", async () => {
    mocks.list.mockImplementation(async (resource: string) => resource === "championships" ? [controlled, { id: 78, name: "Campeonato comum" }] : []);
    show("events");
    fireEvent.click(await screen.findByRole("button", { name: "Criar evento" }));
    expect(screen.queryByRole("option", { name: "Rodada protegida" })).not.toBeInTheDocument();
    expect(screen.getByRole("option", { name: /Campeonato comum/ })).toBeInTheDocument();
  });

  it("não oferece evento controlado na criação e geração genéricas de mercados", async () => {
    mocks.list.mockImplementation(async (resource: string) => resource === "events" ? [controlled, { id: 78, title: "Evento comum", status: "LIVE" }] : []);
    show("markets");
    fireEvent.click(await screen.findByRole("button", { name: "Criar mercado" }));
    expect(screen.queryByRole("option", { name: /Rodada protegida/ })).not.toBeInTheDocument();
    expect(screen.getByRole("option", { name: /Evento comum/ })).toBeInTheDocument();
    fireEvent.keyDown(document, { key: "Escape" });
    fireEvent.click(screen.getByRole("button", { name: "Abrir catálogo" }));
    expect(screen.queryByRole("option", { name: /Rodada protegida/ })).not.toBeInTheDocument();
    expect(screen.getByRole("option", { name: /Evento comum/ })).toBeInTheDocument();
  });
});
