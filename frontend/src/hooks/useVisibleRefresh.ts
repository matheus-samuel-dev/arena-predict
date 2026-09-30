import { useEffect, useRef } from "react";

/** Poll persisted Arena data only, without overlapping requests or hidden-tab traffic. */
export function useVisibleRefresh(refresh: () => Promise<unknown>, intervalMs = 60_000, enabled = true) {
  const callback = useRef(refresh);
  useEffect(() => { callback.current = refresh; }, [refresh]);
  useEffect(() => {
    if (!enabled) return;
    let active = true;
    let pending = false;
    const update = async () => {
      if (!active || pending || document.hidden) return;
      pending = true;
      try { await callback.current(); } catch { /* The resource retains its last successful response. */ }
      finally { pending = false; }
    };
    const timer = window.setInterval(update, intervalMs);
    document.addEventListener("visibilitychange", update);
    return () => {
      active = false;
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", update);
    };
  }, [intervalMs, enabled]);
}
