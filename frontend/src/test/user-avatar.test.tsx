import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import {
  DEMO_PLAYER_AVATAR_PATH,
  getAvatarInitials,
  UserAvatar,
} from "../components/UserAvatar";

describe("UserAvatar", () => {
  afterEach(() => cleanup());

  it.each([
    ["Rafa", "RA"],
    ["Ana Lima", "AL"],
    ["João Pedro de Almeida", "JA"],
    ["  Maria   da   Silva  ", "MS"],
    [undefined, "AP"],
  ])("gera iniciais previsíveis para %s", (name, expected) => {
    expect(getAvatarInitials(name)).toBe(expected);
  });

  it("exibe iniciais quando não há imagem e mantém o avatar decorativo", () => {
    const { container } = render(<UserAvatar name="Ana Lima" size="xl" />);

    expect(screen.getByText("AL")).toBeInTheDocument();
    expect(container.querySelector("img")).not.toBeInTheDocument();
    expect(container.firstElementChild).toHaveClass("user-avatar", "user-avatar--xl");
    expect(container.firstElementChild).toHaveAttribute("aria-hidden", "true");
  });

  it("renderiza uma imagem válida sem duplicar o nome para tecnologias assistivas", () => {
    const { container } = render(<UserAvatar name="Ana Lima" avatarUrl="/assets/users/ana.jpg" />);
    const image = container.querySelector("img");

    expect(image).toHaveAttribute("src", "/assets/users/ana.jpg");
    expect(image).toHaveAttribute("alt", "");
    expect(image).toHaveAttribute("aria-hidden", "true");
    expect(screen.queryByText("AL")).not.toBeInTheDocument();
  });

  it("troca uma URL quebrada pelas iniciais sem substituir a superfície estável", () => {
    const { container } = render(<UserAvatar name="Carlos Souza" src="/imagem-quebrada.jpg" />);
    const surface = container.firstElementChild;
    const image = container.querySelector("img");

    expect(image).toBeInTheDocument();
    fireEvent.error(image!);

    expect(container.firstElementChild).toBe(surface);
    expect(container.querySelector("img")).not.toBeInTheDocument();
    expect(screen.getByText("CS")).toBeInTheDocument();
  });

  it("usa o asset local no perfil Jogador Demo e permite rótulo acessível quando isolado", () => {
    const { container } = render(<UserAvatar name="Jogador Demo" aria-label="Avatar de Jogador Demo" />);
    const surface = screen.getByRole("img", { name: "Avatar de Jogador Demo" });

    expect(container.querySelector("img")).toHaveAttribute("src", DEMO_PLAYER_AVATAR_PATH);
    expect(surface).not.toHaveAttribute("aria-hidden");
  });
});
