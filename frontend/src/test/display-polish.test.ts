import { describe, expect, it } from "vitest";
import { scoringRuleDescription } from "../pages/AdminPages";
import { poolScopeLabels } from "../pages/PoolsPage";

describe("polimento de apresentação", () => {
  it("explica ligas sem escopo específico como competições amplas", () => {
    expect(poolScopeLabels({ sport: null, championship: null })).toEqual({
      sport: "Todas as modalidades",
      championship: "Todos os campeonatos",
    });
  });

  it("preserva nomes longos de escopo sem substituí-los por rótulos genéricos", () => {
    const scope = poolScopeLabels({
      sport: "Automobilismo de resistência internacional",
      championship: "Campeonato Mundial de Endurance · Temporada 2026",
    });

    expect(scope.sport).toBe("Automobilismo de resistência internacional");
    expect(scope.championship).toBe("Campeonato Mundial de Endurance · Temporada 2026");
  });

  it("remove repetição de demonstração apenas da explicação de multiplicador", () => {
    expect(scoringRuleDescription("O potencial usa o multiplicador simulado da opção."))
      .toBe("O potencial usa o multiplicador da opção.");
    expect(scoringRuleDescription("Dados demonstrativos permanecem identificados."))
      .toBe("Dados demonstrativos permanecem identificados.");
  });
});
