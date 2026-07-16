import { CheckCircle2, Info, TriangleAlert, X } from "lucide-react";
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";

type ToastKind = "success" | "error" | "info";

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

  const dismiss = useCallback((id: number) => {
    setItems((current) => current.filter((item) => item.id !== id));
  }, []);

  const notify = useCallback(
    (message: string, kind: ToastKind = "info") => {
      const id = Date.now() + Math.floor(Math.random() * 1000);
      setItems((current) => [...current.slice(-2), { id, kind, message }]);
      window.setTimeout(() => dismiss(id), 5000);
    },
    [dismiss],
  );

  const value = useMemo(() => ({ notify }), [notify]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-region" role="region" aria-label="Notificações da aplicação" aria-live="polite">
        {items.map((item) => {
          const Icon = item.kind === "success" ? CheckCircle2 : item.kind === "error" ? TriangleAlert : Info;
          return (
            <div className={`toast toast--${item.kind}`} key={item.id} role={item.kind === "error" ? "alert" : "status"}>
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
