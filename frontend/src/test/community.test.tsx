import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CommunityPage } from "../pages/CommunityPage";

const mocks = vi.hoisted(() => ({
  feed: vi.fn(),
  createPost: vi.fn(),
  like: vi.fn(),
  comments: vi.fn(),
  comment: vi.fn(),
  report: vi.fn(),
  notify: vi.fn(),
}));

vi.mock("../services/api", () => ({
  asList: (value: unknown) => Array.isArray(value)
    ? value
    : value && typeof value === "object" && "content" in value
      ? (value as { content?: unknown[] }).content || []
      : [],
  communityApi: {
    feed: mocks.feed,
    createPost: mocks.createPost,
    like: mocks.like,
    comments: mocks.comments,
    comment: mocks.comment,
    report: mocks.report,
  },
}));

vi.mock("../contexts/AuthContext", () => ({
  useAuth: () => ({ user: { name: "Jogador Teste", email: "jogador@arenapredict.com", role: "PARTICIPANTE" } }),
}));

vi.mock("../contexts/ToastContext", () => ({
  useToast: () => ({ notify: mocks.notify }),
}));

const post = {
  id: 7,
  author: { name: "Ana Arena" },
  content: "Minha leitura para o próximo evento.",
  createdAt: "2026-07-15T12:00:00Z",
  commentCount: 1,
};

describe("comentários da comunidade", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.feed.mockResolvedValue([post]);
    mocks.createPost.mockResolvedValue({});
    mocks.like.mockResolvedValue({});
    mocks.report.mockResolvedValue(undefined);
  });

  afterEach(() => cleanup());

  it("carrega a discussão, publica um comentário e atualiza a contagem", async () => {
    mocks.comments.mockResolvedValue([{
      id: 11,
      author: { name: "Bruno" },
      content: "Comentário existente",
      createdAt: "2026-07-15T12:05:00Z",
    }]);
    mocks.comment.mockResolvedValue({
      id: 12,
      author: { name: "Jogador Teste" },
      content: "Concordo com essa análise.",
      createdAt: "2026-07-15T12:10:00Z",
    });
    const user = userEvent.setup();

    render(<CommunityPage />);
    const openComments = await screen.findByRole("button", { name: /Abrir comentários de Ana Arena, 1 comentário/i });
    await user.click(openComments);

    expect(mocks.comments).toHaveBeenCalledWith(7);
    expect(await screen.findByText("Comentário existente")).toBeInTheDocument();

    const field = screen.getByLabelText("Seu comentário");
    const submit = screen.getByRole("button", { name: /Comentar/i });
    expect(submit).toBeDisabled();
    await user.type(field, "Concordo com essa análise.");
    await user.click(submit);

    expect(mocks.comment).toHaveBeenCalledWith(7, "Concordo com essa análise.");
    expect(await screen.findByText("Concordo com essa análise.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Abrir comentários de Ana Arena, 2 comentários/i })).toBeInTheDocument();
    expect(mocks.notify).toHaveBeenCalledWith("Comentário publicado.", "success");
  });

  it("exibe falha de carregamento e permite tentar novamente", async () => {
    mocks.comments
      .mockRejectedValueOnce(new Error("Falha ao consultar a discussão."))
      .mockResolvedValueOnce([{
        id: 13,
        author: { name: "Carla" },
        content: "Discussão recuperada",
        createdAt: "2026-07-15T12:15:00Z",
      }]);
    const user = userEvent.setup();

    render(<CommunityPage />);
    await user.click(await screen.findByRole("button", { name: /Abrir comentários/i }));

    expect(await screen.findByText("Falha ao consultar a discussão.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Tentar novamente/i }));

    expect(await screen.findByText("Discussão recuperada")).toBeInTheDocument();
    await waitFor(() => expect(mocks.comments).toHaveBeenCalledTimes(2));
  });
});
