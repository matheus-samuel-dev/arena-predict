import { useCallback, useEffect, useRef, useState } from "react";
import { demoApi } from "../services/api";
import type { DemoScenario } from "../types";

/** Mutations invalidate older reads so a late poll cannot resurrect a previous round. */
export function useDemoScenario(identity?: number, enabled = true) {
  const [data, setData] = useState<DemoScenario | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const request = useRef(0);
  const refresh = useCallback(async () => {
    if (!enabled) return undefined;
    const current = ++request.current;
    setRefreshing(true);
    try {
      const result = await demoApi.scenario();
      if (request.current === current) { setData(result); setError(""); }
      return result;
    } catch (reason) {
      if (request.current === current) setError(reason instanceof Error ? reason.message : "Não foi possível atualizar a demonstração.");
      throw reason;
    } finally {
      if (request.current === current) { setLoading(false); setRefreshing(false); }
    }
  }, [enabled]);
  const accept = useCallback((result: DemoScenario) => {
    request.current += 1;
    setData(result); setError(""); setLoading(false); setRefreshing(false);
  }, []);
  useEffect(() => {
    setData(null); setLoading(enabled); setError("");
    if (!enabled) return;
    void refresh().catch(() => undefined);
    return () => { request.current += 1; };
  }, [identity, enabled, refresh]);
  return { data, loading, error, refreshing, refresh, accept };
}
