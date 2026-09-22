import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { EventCard, isPredictionOpen, marketAvailabilityStatus, marketAvailabilityTooltip, marketDisplayName, MarketList } from "../components/EventCard";
import type { ArenaEvent, PredictionMarket } from "../types";

const openMarket: PredictionMarket = {
  id: 2, name: "Total de mapas", status: "OPEN", category: "Série", timingMode: "LIVE_ENABLED",
  availability: { allowed: true, code: "OPEN", label: "Aberto", reason: "Aceita palpites ao vivo." },
  options: [{ id: 3, label: "Acima de 2,5", multiplier: 1.9, active: true }],
};
const live: ArenaEvent = {
  id: 1, title: "FURIA × NAVI", status: "LIVE", startsAt: "2020-01-01T12:00:00Z",
  predictionClosesAt: "2020-01-01T11:55:00Z", availableMarketCount: 1,
  predictionAvailabilityLabel: "Aberto para palpites", markets: [openMarket],
  homeCompetitor: { name: "FURIA" }, awayCompetitor: { name: "NAVI" },
};

describe("disponibilidade de mercados retornada pelo servidor", () => {
  afterEach(cleanup);
  it("usa códigos do servidor sem inferir status pelo texto e mostra a limitação estática", () => {
    expect(marketAvailabilityStatus({ ...openMarket, availability: { allowed:false, code:"DEADLINE", label:"Encerrado", reason:"Suspensão anterior resolvida" } })).toBe("CLOSED");
    render(<MarketList event={live} markets={[{ ...openMarket, pricingMode:"STATIC", pricingReason:"Dados ao vivo insuficientes: multiplicadores estáticos." }]} onPredict={vi.fn()} />);
    expect(screen.getByText("Dados ao vivo insuficientes: multiplicadores estáticos.")).toBeVisible();
  });
  it("permite mercado ao vivo mesmo com prazo global legado vencido", () => {
    const choose = vi.fn();
    render(<MarketList event={live} markets={[openMarket]} onPredict={choose} />);
    fireEvent.click(screen.getByRole("button", { name: /Acima de 2,5/ }));
    expect(choose).toHaveBeenCalledWith({ event: live, market: openMarket, option: openMarket.options[0] });
    expect(screen.getByRole("button", { name: /Acima de 2,5/ })).toHaveAttribute("aria-pressed", "true");
    expect(isPredictionOpen(live)).toBe(true);
  });

  it("mantém a regra completa sob demanda e prioriza uma explicação curta", () => {
    const market = {
      ...openMarket,
      name: "Resultado da partida · 90 minutos",
      code: "WINNER",
      timingMode: "PRE_MATCH_ONLY" as const,
      closesAt: "2030-01-01T15:00:00Z",
      settlementDescription: "Compara o placar ao final do tempo regulamentar.",
    };
    render(<MarketList event={live} markets={[market]} onPredict={vi.fn()} />);

    expect(marketDisplayName(market)).toBe("Pré-jogo · Resultado final");
    expect(screen.getByText("Disponível até o início do evento.")).toBeVisible();
    expect(screen.getByText(market.settlementDescription)).not.toBeVisible();
    fireEvent.click(screen.getByText("Entenda o mercado"));
    expect(screen.getByText(market.settlementDescription)).toBeVisible();
    expect(screen.getByText(/pontos utilizados são devolvidos/i)).toBeVisible();
  });

  it("não usa estado aberto do evento para habilitar mercado suspenso", () => {
    const suspended = { ...openMarket, status: "SUSPENDED", availability: { allowed: false, code: "SUSPENDED", label: "Mercado suspenso", reason: "Mercado temporariamente suspenso durante atualização." } };
    const choose = vi.fn();
    render(<MarketList event={{ ...live, status: "OPEN_FOR_PREDICTIONS" }} markets={[suspended]} onPredict={choose} />);
    expect(screen.getByRole("button", { name: /Acima de 2,5/ })).toBeDisabled();
    expect(screen.getByText(suspended.availability.reason)).toBeVisible();
    expect(screen.queryByText("Indisponível")).not.toBeInTheDocument();
    expect(choose).not.toHaveBeenCalled();
  });

  it("não usa verde quando um mercado tecnicamente aberto já encerrou para o participante", () => {
    expect(marketAvailabilityStatus({
      ...openMarket,
      availability: { allowed: false, code: "EVENT_STARTED", label: "Encerrado após o início", reason: "Este mercado aceita palpites somente no pré-jogo." },
    })).toBe("CLOSED");
  });

  it("concentra a transparência demonstrativa no aviso global", () => {
    expect(marketAvailabilityTooltip({
      ...openMarket,
      availability: { ...openMarket.availability!, reason: "Multiplicadores demonstrativos; pontos exclusivamente virtuais." },
    })).toBe("Disponibilidade controlada pelo servidor.");
  });

  it("falha fechado quando o servidor ainda não informa disponibilidade", () => {
    expect(isPredictionOpen({ ...live, availableMarketCount: undefined, markets: [{ ...openMarket, availability: undefined }] })).toBe(false);
  });

  it("card não anuncia abertura por causa do enum legado e mantém CTA correto", () => {
    render(<MemoryRouter><EventCard event={{ ...live, status: "OPEN_FOR_PREDICTIONS", availableMarketCount: 0, predictionAvailabilityLabel: "Palpites encerrados" }} /></MemoryRouter>);
    expect(screen.queryByText("Aberto para palpites")).not.toBeInTheDocument();
    expect(screen.getByText("Palpites encerrados")).toBeVisible();
    expect(screen.getByRole("link", { name: "Ver detalhes" })).toHaveAttribute("href", "/events/1");
  });
});
