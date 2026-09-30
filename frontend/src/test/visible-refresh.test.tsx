import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useVisibleRefresh } from "../hooks/useVisibleRefresh";

describe("atualização leve dos dados persistidos", () => {
  beforeEach(() => { vi.useFakeTimers(); vi.spyOn(document, "hidden", "get").mockReturnValue(false); });
  afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks(); });

  it("não sobrepõe requisições e para ao desmontar", async () => {
    let complete!: () => void;
    const refresh = vi.fn().mockImplementation(() => new Promise<void>((resolve) => { complete = resolve; }));
    const { unmount } = renderHook(() => useVisibleRefresh(refresh, 30_000));
    await act(() => vi.advanceTimersByTimeAsync(90_000));
    expect(refresh).toHaveBeenCalledTimes(1);
    await act(async () => complete());
    await act(() => vi.advanceTimersByTimeAsync(30_000));
    expect(refresh).toHaveBeenCalledTimes(2);
    unmount();
    await act(async () => complete());
    await act(() => vi.advanceTimersByTimeAsync(60_000));
    expect(refresh).toHaveBeenCalledTimes(2);
  });

  it("poupa tráfego em aba oculta e atualiza ao retornar", async () => {
    const hidden = vi.spyOn(document, "hidden", "get").mockReturnValue(true);
    const refresh = vi.fn().mockResolvedValue(undefined);
    renderHook(() => useVisibleRefresh(refresh));
    await act(() => vi.advanceTimersByTimeAsync(120_000));
    expect(refresh).not.toHaveBeenCalled();
    hidden.mockReturnValue(false);
    await act(async () => { document.dispatchEvent(new Event("visibilitychange")); });
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("volta a tentar no intervalo normal depois de uma indisponibilidade", async () => {
    const refresh = vi.fn().mockRejectedValueOnce(new Error("Sem conexão")).mockResolvedValue(undefined);
    renderHook(() => useVisibleRefresh(refresh));
    await act(() => vi.advanceTimersByTimeAsync(120_000));
    expect(refresh).toHaveBeenCalledTimes(2);
  });

  it("desativa consultas quando o resultado já foi confirmado", async () => {
    const refresh = vi.fn();
    renderHook(() => useVisibleRefresh(refresh, 30_000, false));
    await act(() => vi.advanceTimersByTimeAsync(120_000));
    expect(refresh).not.toHaveBeenCalled();
  });
});
