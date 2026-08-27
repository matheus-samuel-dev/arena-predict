import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { TeamLogo, normalizeTeamName, resolveTeamLogo } from "../components/TeamLogo";

describe("TeamLogo", () => {
  afterEach(() => cleanup());

  it("resolve aliases oficiais sem exigir cadastro manual no componente", () => {
    expect(normalizeTeamName("  São Paulo  ")).toBe("sao paulo");
    expect(resolveTeamLogo("Palmeiras")).toContain("Palmeiras_logo.svg");
    expect(resolveTeamLogo("Team Vitality")).toContain("Team_Vitality_logo.svg");
  });

  it("prioriza URL da API e troca uma imagem quebrada por iniciais", () => {
    const { container } = render(<TeamLogo name="Clube Aurora" logoUrl="/logos/aurora.svg" decorative={false} />);
    const image = container.querySelector("img");
    expect(image).toHaveAttribute("src", "/logos/aurora.svg");
    expect(screen.getByRole("img", { name: "Clube Aurora" })).toBeInTheDocument();
    fireEvent.error(image!);
    expect(container.querySelector("img")).not.toBeInTheDocument();
    expect(screen.getByText("CA")).toBeInTheDocument();
  });

  it("mantém fallback legível para participante sem logo", () => {
    const { container } = render(<TeamLogo name="Equipe sem imagem" />);
    expect(container.firstElementChild).toHaveClass("team-logo--fallback");
    expect(screen.getByText("ES")).toBeInTheDocument();
  });
});

