import { describe, expect, it } from "vitest";
import { validateResourceForm } from "../pages/AdminPages";
import { validatePoolCreationDraft } from "../pages/PoolsPage";

describe("validação dos fluxos de criação", () => {
  it("rejeita evento com prazo igual ao início e participantes duplicados", () => {
    const errors = validateResourceForm("events", {
      title: "Eventos",
      singular: "evento",
      description: "",
      fields: [
        { key: "startsAt", label: "Data e hora", type: "datetime-local", required: true },
        { key: "predictionClosesAt", label: "Limite do palpite", type: "datetime-local", required: true },
        { key: "homeCompetitorId", label: "Mandante", type: "number" },
        { key: "awayCompetitorId", label: "Visitante", type: "number" },
      ],
    }, {
      startsAt: "2026-09-03T20:00",
      predictionClosesAt: "2026-09-03T20:00",
      homeCompetitorId: 7,
      awayCompetitorId: 7,
    });

    expect(errors.predictionClosesAt).toContain("antes do início");
    expect(errors.awayCompetitorId).toBe("Selecione participantes diferentes para o evento.");
  });

  it("impede destino externo em uma notificação administrativa", () => {
    const errors = validateResourceForm("notifications", {
      title: "Notificações",
      singular: "notificação",
      description: "",
      fields: [{ key: "targetUrl", label: "Destino interno" }],
    }, { targetUrl: "https://example.test/phishing" });

    expect(errors.targetUrl).toContain("destino interno");
    expect(validateResourceForm("notifications", {
      title: "Notificações",
      singular: "notificação",
      description: "",
      fields: [{ key: "targetUrl", label: "Destino interno" }],
    }, { targetUrl: "/notifications" })).toEqual({});
  });

  it("valida limites, premiação virtual e período de bolões e ligas", () => {
    const valid = {
      name: "Liga dos Analistas",
      rules: "Classificação por pontos virtuais.",
      limit: 20,
      virtualPrizePoints: 500,
      startsAt: "2026-09-03T20:00",
      endsAt: "2026-09-10T20:00",
    };

    expect(validatePoolCreationDraft(valid)).toBeNull();
    expect(validatePoolCreationDraft({ ...valid, limit: 1 })).toContain("entre 2 e 500");
    expect(validatePoolCreationDraft({ ...valid, virtualPrizePoints: -1 })).toContain("entre 0 e 1.000.000");
    expect(validatePoolCreationDraft({ ...valid, endsAt: valid.startsAt })).toContain("depois do início");
  });
});
