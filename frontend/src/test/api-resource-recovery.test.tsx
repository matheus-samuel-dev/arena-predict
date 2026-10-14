import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useApiResource } from "../hooks/useApiResource";

afterEach(cleanup);

describe("resource recovery and request lifecycle", () => {
  it("clears an initial error when a later background refresh succeeds", async () => {
    const loader = vi.fn().mockRejectedValueOnce(new Error("temporary timeout")).mockResolvedValue({ points: 5000 });
    const { result } = renderHook(() => useApiResource(loader));
    await waitFor(() => expect(result.current.error).toBe("temporary timeout"));
    await act(async () => { await result.current.refresh(); });
    expect(result.current.error).toBeNull();
    expect(result.current.data).toEqual({ points: 5000 });
    expect(result.current.loading).toBe(false);
  });

  it("coalesces overlapping background refreshes with the pending initial load", async () => {
    let resolve!: (value: number) => void;
    const loader = vi.fn(() => new Promise<number>((done) => { resolve = done; }));
    const { result } = renderHook(() => useApiResource(loader));
    await waitFor(() => expect(loader).toHaveBeenCalledTimes(1));
    await act(async () => {
      const first = result.current.refresh();
      const second = result.current.refresh();
      expect(first).toBe(second);
      resolve(42); await first;
    });
    expect(loader).toHaveBeenCalledTimes(1);
    expect(result.current.loading).toBe(false);
    expect(result.current.data).toBe(42);
  });

  it("aborts the request when leaving the page", async () => {
    const loader = vi.fn((_signal?: AbortSignal) => new Promise<number>(() => undefined));
    const { unmount } = renderHook(() => useApiResource(loader));
    await waitFor(() => expect(loader).toHaveBeenCalledTimes(1));
    const signal = loader.mock.calls[0][0];
    expect(signal?.aborted).toBe(false); unmount(); expect(signal?.aborted).toBe(true);
  });

  it("a superseded request cannot overwrite a successful retry", async () => {
    let resolveOld!: (value: number) => void;
    const loader = vi.fn((_signal?: AbortSignal) => new Promise<number>((done) => { resolveOld = done; }));
    const { result } = renderHook(() => useApiResource(loader));
    await waitFor(() => expect(loader).toHaveBeenCalledTimes(1));
    const oldSignal = loader.mock.calls[0][0];
    loader.mockResolvedValueOnce(2);
    await act(async () => { await result.current.reload(); });
    expect(oldSignal?.aborted).toBe(true);
    await act(async () => { resolveOld(1); });
    expect(result.current.data).toBe(2);
  });
});
