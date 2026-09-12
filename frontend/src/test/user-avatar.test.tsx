import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import {
  DEMO_PLAYER_AVATAR_PATH,
  normalizeUserName,
  resolveAvatarSource,
  userAvatarGradient,
  userInitials,
  UserAvatar,
} from "../components/UserAvatar";

describe("UserAvatar", () => {
  afterEach(() => cleanup());

  it("resolve identidades demo de forma centralizada e tolerante a acentos", () => {
    expect(normalizeUserName("  Beatríz   Nunes  ")).toBe("beatriz nunes");
    expect(resolveAvatarSource("Jogador Demo")).toBe(DEMO_PLAYER_AVATAR_PATH);
    expect(resolveAvatarSource("Beatriz Nunes")).toBe("/assets/avatars/beatriz-nunes.webp");
  });

  it("gera iniciais e cores estáveis quando o usuário não tem imagem", () => {
    const { container } = render(<UserAvatar name="Participante sem cadastro" size="xl" />);

    expect(container.querySelector("img")).not.toBeInTheDocument();
    expect(container.querySelector(".user-avatar__initials")).toHaveTextContent("PC");
    expect(userInitials("Sofia Martins")).toBe("SM");
    expect(userAvatarGradient("Sofia Martins")).toBe(userAvatarGradient("Sofia Martins"));
    expect(container.firstElementChild).toHaveClass("user-avatar", "user-avatar--xl");
    expect(container.firstElementChild).toHaveAttribute("aria-hidden", "true");
  });

  it("renderiza uma imagem válida sem duplicar o nome para tecnologias assistivas", () => {
    const { container } = render(<UserAvatar name="Ana Lima" avatarUrl="/assets/users/ana.jpg" />);
    const image = container.querySelector("img");

    expect(image).toHaveAttribute("src", "/assets/users/ana.jpg");
    expect(image).toHaveAttribute("alt", "");
    expect(image).toHaveAttribute("aria-hidden", "true");
  });

  it("troca uma URL quebrada pelas iniciais sem alterar a superfície", () => {
    const { container } = render(<UserAvatar name="Carlos Souza" src="/imagem-quebrada.jpg" />);
    const surface = container.firstElementChild;
    const image = container.querySelector("img");

    fireEvent.error(image!);

    expect(container.firstElementChild).toBe(surface);
    expect(container.querySelector("img")).not.toBeInTheDocument();
    expect(container.querySelector(".user-avatar__initials")).toHaveTextContent("CS");
  });

  it("preserva a identidade demo mapeada quando uma URL antiga falha", () => {
    const { container } = render(<UserAvatar name="Beatriz Nunes" avatarUrl="/imagem-antiga.jpg" />);
    fireEvent.error(container.querySelector("img")!);

    expect(container.querySelector("img")).toHaveAttribute("src", "/assets/avatars/beatriz-nunes.webp");
    expect(container.textContent).toBe("");
  });

  it("usa o asset local no perfil Jogador Demo e permite rótulo acessível quando isolado", () => {
    const { container } = render(<UserAvatar name="Jogador Demo" aria-label="Avatar de Jogador Demo" />);
    const surface = screen.getByRole("img", { name: "Avatar de Jogador Demo" });

    expect(container.querySelector("img")).toHaveAttribute("src", DEMO_PLAYER_AVATAR_PATH);
    expect(surface).not.toHaveAttribute("aria-hidden");
  });
});
