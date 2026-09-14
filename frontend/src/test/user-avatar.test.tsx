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

  it("usa os avatares dos dados, sem classificar cadastros normais pelo nome", () => {
    expect(normalizeUserName("  Beatríz   Nunes  ")).toBe("beatriz nunes");
    expect(resolveAvatarSource("Jogador Demo", DEMO_PLAYER_AVATAR_PATH)).toBe(DEMO_PLAYER_AVATAR_PATH);
    expect(resolveAvatarSource("Beatriz Nunes")).toBeNull();
    const { container } = render(<UserAvatar name="Sofia Martins" />);
    expect(container.querySelector(".user-avatar__initials")).toHaveTextContent("SM");
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

  it("mantém um avatar visual demo mesmo se o retrato e o fallback local falharem", () => {
    const { container } = render(<UserAvatar name="Sofia Martins" avatarUrl="/assets/avatars/sofia-martins.webp" />);
    fireEvent.error(container.querySelector("img")!);

    expect(container.querySelector("img")).toHaveAttribute("src", "/assets/avatars/avatar-default.webp");
    fireEvent.error(container.querySelector("img")!);
    expect(container.querySelector("svg.user-avatar__image")).toBeInTheDocument();
    expect(container.querySelector(".user-avatar__initials")).not.toBeInTheDocument();
    expect(container.textContent).toBe("");
  });

  it("usa o asset local no perfil Jogador Demo e permite rótulo acessível quando isolado", () => {
    const { container } = render(<UserAvatar name="Jogador Demo" avatarUrl={DEMO_PLAYER_AVATAR_PATH} aria-label="Avatar de Jogador Demo" />);
    const surface = screen.getByRole("img", { name: "Avatar de Jogador Demo" });

    expect(container.querySelector("img")).toHaveAttribute("src", DEMO_PLAYER_AVATAR_PATH);
    expect(surface).not.toHaveAttribute("aria-hidden");
  });
});
