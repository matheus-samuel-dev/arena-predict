import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { TEAM_PLACEHOLDER_PATH, TeamLogo, normalizeTeamName, resolveTeamLogo } from "../components/TeamLogo";

describe("TeamLogo", () => {
  afterEach(() => cleanup());

  it("resolve apenas identidades locais e usa fallback para marcas sem asset distribuível", () => {
    expect(normalizeTeamName("  São Paulo  ")).toBe("sao paulo");
    expect(resolveTeamLogo("Palmeiras")).toBeUndefined();
    expect(resolveTeamLogo("Team Vitality")).toBeUndefined();
    expect(resolveTeamLogo("FURIA")).toBe("/assets/teams/furia.svg");
    expect(resolveTeamLogo("Natus Vincere")).toBeUndefined();
    expect(resolveTeamLogo("Carlos Alcaraz")).toBe("/assets/teams/carlos-alcaraz.svg");
  });

  it("prioriza URL da API e troca uma imagem quebrada por identidade exclusiva", () => {
    const { container } = render(<TeamLogo name="Clube Aurora" logoUrl="/logos/aurora.svg" decorative={false} />);
    const image = container.querySelector("img");
    expect(image).toHaveAttribute("src", "/logos/aurora.svg");
    expect(screen.getByRole("img", { name: "Clube Aurora" })).toBeInTheDocument();
    fireEvent.error(image!);
    expect(container.querySelector("img")?.getAttribute("src")).toMatch(/^data:image\/svg\+xml/);
    expect(container.querySelector("img")).not.toHaveAttribute("src", TEAM_PLACEHOLDER_PATH);
    expect(container.textContent).toBe("");
  });

  it("gera identidade estável quando uma URL cadastrada falha", () => {
    const { container } = render(<TeamLogo name="NAVI" logoUrl="/logos/antigo.svg" />);
    fireEvent.error(container.querySelector("img")!);

    expect(container.querySelector("img")?.getAttribute("src")).toMatch(/^data:image\/svg\+xml/);
    expect(container.firstElementChild).not.toHaveClass("team-logo--fallback");
  });

  it("gera fallbacks diferentes para participantes sem logo", () => {
    const { container } = render(<TeamLogo name="Equipe sem imagem" />);
    const firstSource = container.querySelector("img")?.getAttribute("src");
    expect(container.firstElementChild).toHaveClass("team-logo--generated");
    expect(firstSource).toMatch(/^data:image\/svg\+xml/);
    cleanup();
    const other = render(<TeamLogo name="Outro participante" />);
    expect(other.container.querySelector("img")?.getAttribute("src")).not.toBe(firstSource);
    expect(container.textContent).toBe("");
  });
});
