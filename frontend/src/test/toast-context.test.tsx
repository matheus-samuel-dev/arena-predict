import { act, cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ToastProvider, useToast } from "../contexts/ToastContext";

function ToastHarness() {
  const { notify } = useToast();
  return (
    <div>
      <button type="button" onClick={() => notify("Alteração salva.", "success")}>Sucesso</button>
      <button type="button" onClick={() => notify("Revise os dados.", "error")}>Erro</button>
      <button type="button" onClick={() => notify("Atenção ao prazo.", "warning")}>Aviso</button>
      <button type="button" onClick={() => notify("Dados atualizados.", "info")}>Informação</button>
    </div>
  );
}

describe("notificações globais", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("apresenta estados semânticos, região acessível e fechamento manual", () => {
    render(<ToastProvider><ToastHarness /></ToastProvider>);

    fireEvent.click(screen.getByRole("button", { name: "Sucesso" }));
    fireEvent.click(screen.getByRole("button", { name: "Erro" }));
    fireEvent.click(screen.getByRole("button", { name: "Aviso" }));

    const region = screen.getByRole("region", { name: "Notificações da aplicação" });
    expect(region).toHaveAttribute("aria-live", "polite");
    expect(screen.getByText("Alteração salva.").closest(".toast")).toHaveClass("toast--success");
    expect(screen.getByText("Revise os dados.").closest(".toast")).toHaveClass("toast--error");
    expect(screen.getByText("Revise os dados.").closest(".toast")).toHaveAttribute("role", "alert");
    expect(screen.getByText("Atenção ao prazo.").closest(".toast")).toHaveClass("toast--warning");

    const successToast = screen.getByText("Alteração salva.").closest(".toast");
    expect(successToast).not.toBeNull();
    fireEvent.click(within(successToast as HTMLElement).getByRole("button", { name: "Fechar aviso" }));
    expect(screen.queryByText("Alteração salva.")).not.toBeInTheDocument();
  });

  it("remove automaticamente a notificação e limpa seus temporizadores", () => {
    vi.useFakeTimers();
    const { unmount } = render(<ToastProvider><ToastHarness /></ToastProvider>);
    fireEvent.click(screen.getByRole("button", { name: "Informação" }));
    expect(screen.getByText("Dados atualizados.")).toBeInTheDocument();

    act(() => vi.advanceTimersByTime(5_000));
    expect(screen.queryByText("Dados atualizados.")).not.toBeInTheDocument();
    expect(() => unmount()).not.toThrow();
  });
});
