import { useCallback, useEffect, useRef, useState } from "react";

export function useApiResource<T>(loader: () => Promise<T>, dependencies: readonly unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const requestId = useRef(0);

  const load = useCallback(async (silent: boolean) => {
    const currentId = ++requestId.current;
    if (!silent) {
      setLoading(true);
      setError(null);
    }
    try {
      const result = await loader();
      if (currentId === requestId.current) setData(result);
      return result;
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : "Não foi possível carregar os dados.";
      if (!silent && currentId === requestId.current) setError(message);
      throw reason;
    } finally {
      if (!silent && currentId === requestId.current) setLoading(false);
    }
  }, dependencies);

  const reload = useCallback(() => load(false), [load]);
  const refresh = useCallback(() => load(true), [load]);

  useEffect(() => {
    reload().catch(() => undefined);
    return () => {
      requestId.current += 1;
    };
  }, [reload]);

  return { data, setData, loading, error, reload, refresh };
}
