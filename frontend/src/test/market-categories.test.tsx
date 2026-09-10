import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CategorizedMarkets } from "../pages/EventsPage";
import type { ArenaEvent, PredictionMarket } from "../types";

function market(id: number, category: string): PredictionMarket {
  return { id, category, name: `Mercado ${id}`, status: "OPEN", availability: { allowed: true, code: "OPEN", label: "Aberto", reason: "" }, options: [{ id: id * 10, label: `Opção ${id}`, multiplier: 1.9, active: true }] };
}

describe("hierarquia de mercados", () => {
  afterEach(cleanup);

  it("renderiza somente a categoria selecionada e mantém a seleção ligada ao mercado correto", () => {
    const markets = [market(1, "Série"), market(2, "Série"), market(3, "Rounds"), market(4, "Pistol")];
    const event = { id: 22, status: "LIVE", markets, availableMarketCount: 4, predictionAvailabilityLabel: "Aberto para palpites" } as ArenaEvent;
    const choose = vi.fn();
    render(<CategorizedMarkets event={event} onPredict={choose} />);
    expect(screen.getByRole("button", { name: "Série 2" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("region", { name: "Mercados: Série" })).toBeVisible();
    expect(screen.queryByText("Mercado 3")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Rounds 1" }));
    expect(screen.queryByText("Mercado 1")).not.toBeInTheDocument();
    const region = screen.getByRole("region", { name: "Mercados: Rounds" });
    fireEvent.click(within(region).getByRole("button", { name: /Opção 3/ }));
    expect(choose).toHaveBeenCalledWith({ event, market: markets[2], option: markets[2].options[0] });
  });

  it("retorna à primeira categoria existente se uma atualização remover a selecionada", () => {
    const event = { id: 22, markets: [market(1, "Série"), market(2, "Pistol")] } as ArenaEvent;
    const { rerender } = render(<CategorizedMarkets event={event} onPredict={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "Pistol 1" }));
    rerender(<CategorizedMarkets event={{ ...event, markets: [market(1, "Série")] }} onPredict={vi.fn()} />);
    expect(screen.getByRole("region", { name: "Mercados: Série" })).toBeVisible();
    expect(screen.queryByRole("region", { name: "Mercados: Pistol" })).not.toBeInTheDocument();
  });
});
