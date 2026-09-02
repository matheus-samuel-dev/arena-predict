import { CheckCircle2, CircleAlert, Info, TriangleAlert, X } from "lucide-react";
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";

export type ToastKind = "success" | "error" | "warning" | "info";

interface ToastItem {
  id: number;
  kind: ToastKind;
  message: string;
}

interface ToastContextValue {
  notify: (message: string, kind?: ToastKind) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(0);
  const timers = useRef(new Map<number, number>());

  const dismiss = useCallback((id: number) => {
    const timer = timers.current.get(id);
    if (timer !== undefined) window.clearTimeout(timer);
    timers.current.delete(id);
    setItems((current) => current.filter((item) => item.id !== id));
  }, []);

  const notify = useCallback(
    (message: string, kind: ToastKind = "info") => {
      nextId.current += 1;
      const id = nextId.current;
      setItems((current) => {
        const retained = current.slice(-2);
        current.slice(0, Math.max(0, current.length - retained.length)).forEach((item) => {
          const timer = timers.current.get(item.id);
          if (timer !== undefined) window.clearTimeout(timer);
          timers.current.delete(item.id);
        });
        return [...retained, { id, kind, message }];
      });
      timers.current.set(id, window.setTimeout(() => dismiss(id), 5000));
    },
    [dismiss],
  );

  useEffect(() => () => {
    timers.current.forEach((timer) => window.clearTimeout(timer));
    timers.current.clear();
  }, []);

  const value = useMemo(() => ({ notify }), [notify]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-region" role="region" aria-label="Notificações da aplicação" aria-live="polite" aria-atomic="false">
        {items.map((item) => {
          const Icon = item.kind === "success" ? CheckCircle2 : item.kind === "error" ? TriangleAlert : item.kind === "warning" ? CircleAlert : Info;
          return (
            <div className={`toast toast--${item.kind}`} key={item.id} role={item.kind === "error" ? "alert" : "status"} aria-atomic="true">
              <Icon size={19} aria-hidden="true" />
              <span>{item.message}</span>
              <button type="button" onClick={() => dismiss(item.id)} aria-label="Fechar aviso">
                <X size={17} />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) throw new Error("useToast must be used inside ToastProvider");
  return context;
}
