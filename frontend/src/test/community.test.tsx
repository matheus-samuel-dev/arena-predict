import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { CommunityPage } from "../pages/CommunityPage";

const mocks = vi.hoisted(() => ({
  feed: vi.fn(),
  createPost: vi.fn(),
  like: vi.fn(),
  unlike: vi.fn(),
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
    unlike: mocks.unlike,
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
  topic: "Brasileirão",
  createdAt: "2026-07-15T12:00:00Z",
  likeCount: 2,
  commentCount: 1,
  likedByCurrentUser: false,
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function renderCommunity() {
  return render(<MemoryRouter><CommunityPage /></MemoryRouter>);
}

describe("página Comunidade", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.feed.mockResolvedValue([post]);
    mocks.createPost.mockResolvedValue({ ...post, id: 8 });
    mocks.like.mockResolvedValue({ ...post, likeCount: 3, likedByCurrentUser: true });
    mocks.unlike.mockResolvedValue({ ...post, likeCount: 2, likedByCurrentUser: false });
    mocks.report.mockResolvedValue(undefined);
  });

  afterEach(() => cleanup());

  it("carrega o feed com data semântica e permite atualização manual", async () => {
    const user = userEvent.setup();
    const { container } = renderCommunity();

    expect(await screen.findByText(post.content)).toBeInTheDocument();
    expect(container.querySelector(`time[datetime="${post.createdAt}"]`)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Atualizar feed/i }));

    await waitFor(() => expect(mocks.feed).toHaveBeenCalledTimes(2));
    expect(mocks.notify).toHaveBeenCalledWith("Feed atualizado.", "success");
  });

  it("publica conteúdo com assunto, bloqueia duplo envio e limpa o formulário", async () => {
    const creation = deferred<typeof post>();
    mocks.createPost.mockReturnValueOnce(creation.promise);
    const user = userEvent.setup();

    renderCommunity();
    await screen.findByText(post.content);
    const topic = screen.getByRole("textbox", { name: "Assunto da publicação (opcional)" });
    const content = screen.getByRole("textbox", { name: "Nova publicação" });
    const submit = screen.getByRole("button", { name: /Publicar/i });
    await user.type(topic, "FURIA x NAVI");
    await user.type(content, "O controle econômico pode definir o próximo mapa.");

    await user.click(submit);
    expect(submit).toBeDisabled();
    await user.click(submit);
    expect(mocks.createPost).toHaveBeenCalledTimes(1);
    expect(mocks.createPost).toHaveBeenCalledWith(
      "O controle econômico pode definir o próximo mapa.",
      "FURIA x NAVI",
    );

    await act(async () => creation.resolve({ ...post, id: 8 }));

    await waitFor(() => expect(mocks.feed).toHaveBeenCalledTimes(2));
    await waitFor(() => {
      expect(screen.getByRole("textbox", { name: "Assunto da publicação (opcional)" })).toHaveValue("");
      expect(screen.getByRole("textbox", { name: "Nova publicação" })).toHaveValue("");
    });
    expect(mocks.notify).toHaveBeenCalledWith("Publicação compartilhada com a comunidade.", "success");
  });

  it("preserva o rascunho e libera nova tentativa quando a publicação falha", async () => {
    mocks.createPost.mockRejectedValueOnce(new Error("Serviço temporariamente indisponível."));
    const user = userEvent.setup();

    renderCommunity();
    await screen.findByText(post.content);
    const content = screen.getByRole("textbox", { name: "Nova publicação" });
    await user.type(content, "Análise que deve permanecer no formulário.");
    await user.click(screen.getByRole("button", { name: /Publicar/i }));

    await waitFor(() => expect(mocks.notify).toHaveBeenCalledWith("Serviço temporariamente indisponível.", "error"));
    expect(content).toHaveValue("Análise que deve permanecer no formulário.");
    expect(screen.getByRole("button", { name: /Publicar/i })).toBeEnabled();
  });

  it("registra uma reação uma única vez e permite removê-la", async () => {
    const reaction = deferred<typeof post>();
    mocks.like.mockReturnValueOnce(reaction.promise);
    const user = userEvent.setup();

    renderCommunity();
    const like = await screen.findByRole("button", { name: /Curtir publicação de Ana Arena, 2 curtidas/i });
    await user.click(like);
    expect(like).toBeDisabled();
    expect(like).toHaveAttribute("aria-busy", "true");
    await user.click(like);
    expect(mocks.like).toHaveBeenCalledTimes(1);

    await act(async () => reaction.resolve({ ...post, likeCount: 3, likedByCurrentUser: true }));

    const liked = await screen.findByRole("button", { name: /Remover curtida da publicação de Ana Arena, 3 curtidas/i });
    expect(liked).toHaveAttribute("aria-pressed", "true");
    expect(liked).toBeEnabled();
    expect(mocks.notify).toHaveBeenCalledWith("Reação registrada.", "success");

    await user.click(liked);

    expect(mocks.unlike).toHaveBeenCalledTimes(1);
    const unliked = await screen.findByRole("button", { name: /Curtir publicação de Ana Arena, 2 curtidas/i });
    expect(unliked).toHaveAttribute("aria-pressed", "false");
    expect(mocks.notify).toHaveBeenCalledWith("Reação removida.", "success");
  });

  it("restaura a ação de curtir e informa o erro quando a reação falha", async () => {
    mocks.like.mockRejectedValueOnce(new Error("Não foi possível registrar a reação."));
    const user = userEvent.setup();

    renderCommunity();
    const like = await screen.findByRole("button", { name: /Curtir publicação de Ana Arena/i });
    await user.click(like);

    await waitFor(() => expect(mocks.notify).toHaveBeenCalledWith("Não foi possível registrar a reação.", "error"));
    expect(like).toBeEnabled();
    expect(like).toHaveAttribute("aria-pressed", "false");
  });

  it("mantém curtida e contador quando a remoção falha", async () => {
    mocks.feed.mockResolvedValueOnce([{ ...post, likeCount: 3, likedByCurrentUser: true }]);
    mocks.unlike.mockRejectedValueOnce(new Error("Não foi possível remover a reação."));
    const user = userEvent.setup();

    renderCommunity();
    const liked = await screen.findByRole("button", { name: /Remover curtida da publicação de Ana Arena, 3 curtidas/i });
    await user.click(liked);

    await waitFor(() => expect(mocks.notify).toHaveBeenCalledWith("Não foi possível remover a reação.", "error"));
    expect(liked).toBeEnabled();
    expect(liked).toHaveAttribute("aria-pressed", "true");
    expect(liked).toHaveTextContent("3");
  });

  it("apresenta loading, erro recuperável e empty state do feed", async () => {
    const initialFeed = deferred<unknown[]>();
    mocks.feed.mockReturnValueOnce(initialFeed.promise);
    const { unmount } = renderCommunity();
    expect(screen.getByRole("status", { name: "Carregando conteúdo" })).toBeInTheDocument();
    await act(async () => initialFeed.resolve([]));
    expect(await screen.findByText("A conversa começa com você")).toBeInTheDocument();
    unmount();

    mocks.feed
      .mockRejectedValueOnce(new Error("Falha ao carregar o feed."))
      .mockResolvedValueOnce([]);
    const user = userEvent.setup();
    renderCommunity();
    expect(await screen.findByText("Falha ao carregar o feed.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Tentar novamente/i }));
    expect(await screen.findByText("A conversa começa com você")).toBeInTheDocument();
    await waitFor(() => expect(mocks.feed).toHaveBeenCalledTimes(3));
  });

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

    renderCommunity();
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

  it("evita comentário duplicado e preserva o texto quando o envio falha", async () => {
    mocks.comments.mockResolvedValue([]);
    const submission = deferred<{ id: number; content: string }>();
    mocks.comment.mockReturnValueOnce(submission.promise);
    const user = userEvent.setup();

    renderCommunity();
    await user.click(await screen.findByRole("button", { name: /Abrir comentários/i }));
    const field = await screen.findByLabelText("Seu comentário");
    await user.type(field, "Minha contribuição precisa permanecer.");
    const submit = screen.getByRole("button", { name: /Comentar/i });
    await user.dblClick(submit);

    expect(mocks.comment).toHaveBeenCalledTimes(1);
    expect(submit).toBeDisabled();
    await act(async () => submission.reject(new Error("Não foi possível publicar o comentário.")));

    await waitFor(() => expect(mocks.notify).toHaveBeenCalledWith("Não foi possível publicar o comentário.", "error"));
    expect(field).toHaveValue("Minha contribuição precisa permanecer.");
    expect(submit).toBeEnabled();
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

    renderCommunity();
    await user.click(await screen.findByRole("button", { name: /Abrir comentários/i }));

    expect(await screen.findByText("Falha ao consultar a discussão.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Tentar novamente/i }));

    expect(await screen.findByText("Discussão recuperada")).toBeInTheDocument();
    await waitFor(() => expect(mocks.comments).toHaveBeenCalledTimes(2));
  });
});
