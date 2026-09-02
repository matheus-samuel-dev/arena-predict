import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { auditSummaryLabel, enumLabel, presentationCode, statusPresentation } from "../app/presentation";
import { Modal, StatusBadge } from "../components/UI";

describe("apresentação de estados", () => {
  afterEach(() => cleanup());

  it.each([
    ["OPEN_FOR_PREDICTIONS", "Aberto para palpites"],
    ["ACTIVE", "Ativo"],
    ["LIVE", "Ao vivo"],
    ["SETTLED", "Finalizado"],
    ["CANCELLED", "Cancelado"],
    ["WON", "Vencedor"],
    ["LOST", "Perdedor"],
    ["REFUNDED", "Reembolsado"],
    ["IN_PROGRESS", "Em andamento"],
    ["ROLE_ADMIN", "Administrador"],
    ["EXTERNAL_KEY", "Chave externa"],
    ["SPORT", "Modalidade"],
    ["EVENT_RESULT_RECORDED", "Resultado do evento registrado"],
    ["EVENT_CLASSIFICATION_RECORDED", "Classificação do evento registrada"],
    ["MARKET_SETTLED", "Mercado liquidado"],
    ["ACHIEVEMENT_DEACTIVATED", "Conquista desativada"],
    ["COMMUNITY_REPORT_MODERATED", "Denúncia da comunidade moderada"],
    ["MATCH_WINNER", "Vencedor da partida"],
    ["SERIES_WINNER", "Vencedor da série"],
    ["RACE_WINNER", "Vencedor da corrida"],
  ])("traduz %s sem expor o enum interno", (rawStatus, expectedLabel) => {
    const label = enumLabel(rawStatus);

    expect(label).toBe(expectedLabel);
    expect(label).not.toContain("_");
    expect(label).not.toBe(rawStatus);
  });

  it("converte também valores desconhecidos em texto de apresentação legível", () => {
    expect(enumLabel("CAMPO_PERSONALIZADO")).toBe("Campo personalizado");
    expect(enumLabel("")).toBe("Não informado");
  });

  it("apresenta códigos técnicos como identificadores legíveis", () => {
    expect(presentationCode("AMERICAN_FOOTBALL")).toBe("american-football");
    expect(presentationCode("LEAGUE_OF_LEGENDS")).toBe("league-of-legends");
  });

  it("corrige duplicação de status legada sem alterar o registro de auditoria", () => {
    expect(auditSummaryLabel("Evento Palmeiras x Flamengo · cancelado cancelado com 4 reembolsos"))
      .toBe("Evento Palmeiras x Flamengo cancelado com 4 reembolsos");
    expect(auditSummaryLabel("Mercado vencedor cancelado com 1 reembolso"))
      .toBe("Mercado vencedor cancelado com 1 reembolso");
    expect(auditSummaryLabel("Mercado Vencedor do confronto liquidado: 1 vencedores e 1 pontos virtuais creditados"))
      .toBe("Mercado Vencedor do confronto liquidado: 1 vencedor e 1 ponto virtual creditado");
    expect(auditSummaryLabel("Notificação administrativa enviada para 1 destinatário(s)"))
      .toBe("Notificação administrativa enviada para 1 destinatário");
  });

  it("centraliza rótulo, tom, ícone e ajuda contextual", () => {
    expect(statusPresentation("OPEN_FOR_PREDICTIONS")).toEqual({
      label: "Aberto para palpites",
      tone: "success",
      icon: "check",
      tooltip: "O evento ainda aceita palpites dentro do prazo publicado.",
    });
    expect(statusPresentation("SETTLED")).toMatchObject({
      label: "Finalizado",
      tone: "accent",
      icon: "check",
    });
  });

  it("mantém o badge compreensível para tecnologia assistiva e normaliza labels crus", () => {
    render(
      <StatusBadge
        status="LIVE"
        label="LIVE"
        tooltip="Placar atualizado pelo serviço interno de demonstração."
      />,
    );

    const badge = screen.getByLabelText(
      "Ao vivo. Placar atualizado pelo serviço interno de demonstração.",
    );
    expect(badge).toHaveAttribute("title", "Placar atualizado pelo serviço interno de demonstração.");
    expect(badge).toHaveClass("status--danger");
    expect(screen.getByText("Ao vivo")).toBeInTheDocument();
    expect(screen.queryByText("LIVE")).not.toBeInTheDocument();
    expect(badge.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });
});

function ModalHarness() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>Abrir resultado</button>
      <Modal open={open} onClose={() => setOpen(false)} title="Confirmar resultado">
        <label>
          Placar oficial
          <input aria-label="Placar oficial" />
        </label>
        <button type="button">Salvar resultado</button>
      </Modal>
    </>
  );
}

describe("acessibilidade do modal", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("move e contém o foco, fecha com ESC e restaura o acionador", async () => {
    const visibleRects = [{} as DOMRect] as unknown as DOMRectList;
    vi.spyOn(HTMLElement.prototype, "getClientRects").mockReturnValue(visibleRects);
    const requestFrame = vi.spyOn(window, "requestAnimationFrame").mockImplementation((callback) => {
      return window.setTimeout(() => callback(performance.now()), 0);
    });
    vi.spyOn(window, "cancelAnimationFrame").mockImplementation((handle) => window.clearTimeout(handle));
    const user = userEvent.setup();
    render(<ModalHarness />);

    const trigger = screen.getByRole("button", { name: "Abrir resultado" });
    await user.click(trigger);

    const dialog = screen.getByRole("dialog", { name: "Confirmar resultado" });
    const field = screen.getByRole("textbox", { name: "Placar oficial" });
    const save = screen.getByRole("button", { name: "Salvar resultado" });
    const close = screen.getByRole("button", { name: "Fechar" });
    expect(dialog).toHaveAttribute("aria-modal", "true");
    await waitFor(() => expect(field).toHaveFocus());
    expect(document.body).toHaveClass("modal-open");

    await user.tab();
    expect(save).toHaveFocus();
    await user.tab();
    expect(close).toHaveFocus();
    await user.tab({ shift: true });
    expect(save).toHaveFocus();

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(document.body).not.toHaveClass("modal-open");
    expect(trigger).toHaveFocus();
    expect(requestFrame).toHaveBeenCalledTimes(1);
  });
});
