import { useCallback, useEffect, useRef, useState } from "react";

export function useApiResource<T>(loader: (signal?: AbortSignal) => Promise<T>, dependencies: readonly unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const requestId = useRef(0);
  const pending = useRef<Promise<T> | null>(null);
  const activeController = useRef<AbortController | null>(null);

  const load = useCallback((silent: boolean) => {
    // Polling reuses an initial load/retry instead of replacing its loading state.
    if (silent && pending.current) return pending.current;
    const currentId = ++requestId.current;
    activeController.current?.abort();
    const controller = new AbortController();
    activeController.current = controller;
    if (!silent) {
      setLoading(true);
      setError(null);
    }
    const operation = (async () => {
      try {
        const result = await loader(controller.signal);
        if (currentId === requestId.current) {
          setData(result);
          setError(null);
        }
        return result;
      } catch (reason) {
        const message = reason instanceof Error ? reason.message : "Não foi possível carregar os dados.";
        if (!silent && currentId === requestId.current) setError(message);
        throw reason;
      } finally {
        if (currentId === requestId.current) {
          setLoading(false);
          pending.current = null;
          activeController.current = null;
        }
      }
    })();
    if (activeController.current === controller) pending.current = operation;
    return operation;
  }, dependencies);

  const reload = useCallback(() => load(false), [load]);
  const refresh = useCallback(() => load(true), [load]);

  useEffect(() => {
    reload().catch(() => undefined);
    return () => {
      requestId.current += 1;
      activeController.current?.abort();
      activeController.current = null;
      pending.current = null;
    };
  }, [reload]);

  return { data, setData, loading, error, reload, refresh };
}
