import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { authApi, sessionStorage } from "../services/api";
import type { AuthSession, User } from "../types";
import { useToast } from "./ToastContext";

interface AuthContextValue {
  session: AuthSession | null;
  user: User | null;
  initializing: boolean;
  authenticating: boolean;
  login: (email: string, password: string) => Promise<AuthSession>;
  demoLogin: (profile: "PARTICIPANT" | "ADMIN") => Promise<AuthSession>;
  register: (payload: { name: string; email: string; password: string }) => Promise<AuthSession>;
  logout: (silent?: boolean) => Promise<void>;
  refreshUser: () => Promise<void>;
  updateLocalUser: (user: Partial<User>) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => sessionStorage.read());
  const [user, setUser] = useState<User | null>(() => sessionStorage.read());
  const [initializing, setInitializing] = useState(Boolean(sessionStorage.read()));
  const [authenticating, setAuthenticating] = useState(false);
  const pendingAuthentication = useRef<Promise<AuthSession> | null>(null);
  const { notify } = useToast();

  const applySession = useCallback((next: AuthSession) => {
    sessionStorage.save(next);
    setSession(next);
    setUser(next);
  }, []);

  const clearSession = useCallback(() => {
    sessionStorage.clear();
    setSession(null);
    setUser(null);
  }, []);

  const refreshUser = useCallback(async () => {
    const stored = sessionStorage.read();
    if (!stored) {
      clearSession();
      return;
    }
    const current = await authApi.me();
    const updated: AuthSession = { ...stored, ...current };
    applySession(updated);
  }, [applySession, clearSession]);

  useEffect(() => {
    let active = true;
    if (!sessionStorage.read()) {
      setInitializing(false);
      return undefined;
    }
    refreshUser()
      .catch(() => {
        if (active) clearSession();
      })
      .finally(() => {
        if (active) setInitializing(false);
      });
    return () => {
      active = false;
    };
  }, [clearSession, refreshUser]);

  useEffect(() => {
    const onUnauthorized = () => {
      clearSession();
      notify("Sua sessão expirou. Entre novamente para continuar.", "error");
    };
    const onForbidden = () => notify("Seu perfil não possui permissão para essa ação.", "error");
    window.addEventListener("arena:unauthorized", onUnauthorized);
    window.addEventListener("arena:forbidden", onForbidden);
    return () => {
      window.removeEventListener("arena:unauthorized", onUnauthorized);
      window.removeEventListener("arena:forbidden", onForbidden);
    };
  }, [clearSession, notify]);

  const authenticate = useCallback((operation: () => Promise<AuthSession>) => {
    if (pendingAuthentication.current) return pendingAuthentication.current;
    setAuthenticating(true);
    const request = operation()
      .then((authenticated) => {
        applySession(authenticated);
        return authenticated;
      })
      .finally(() => {
        if (pendingAuthentication.current === request) {
          pendingAuthentication.current = null;
          setAuthenticating(false);
        }
      });
    pendingAuthentication.current = request;
    return request;
  }, [applySession]);

  const login = useCallback(
    (email: string, password: string) => authenticate(() => authApi.login(email.trim().toLowerCase(), password)),
    [authenticate],
  );

  const register = useCallback(
    (payload: { name: string; email: string; password: string }) => authenticate(() => authApi.register({
          ...payload,
          name: payload.name.trim(),
          email: payload.email.trim().toLowerCase(),
        })),
    [authenticate],
  );

  const demoLogin = useCallback(
    (profile: "PARTICIPANT" | "ADMIN") => authenticate(async () => {
        const authenticated = await authApi.demo(profile);
        const expectedRole = profile === "ADMIN" ? "ADMIN" : "PARTICIPANTE";
        if (authenticated.role !== expectedRole) {
          throw new Error("O perfil demonstrativo retornado não corresponde ao acesso solicitado.");
        }
        return authenticated;
      }),
    [authenticate],
  );

  const logout = useCallback(
    async (silent = false) => {
      try {
        await authApi.logout();
      } catch {
        // JWT é stateless: a sessão local sempre é encerrada, mesmo se o servidor estiver indisponível.
      } finally {
        clearSession();
        if (!silent) notify("Sessão encerrada com segurança.", "success");
      }
    },
    [clearSession, notify],
  );

  const updateLocalUser = useCallback(
    (patch: Partial<User>) => {
      if (!session) return;
      const updated = { ...session, ...patch } as AuthSession;
      applySession(updated);
    },
    [applySession, session],
  );

  const value = useMemo(
    () => ({ session, user, initializing, authenticating, login, demoLogin, register, logout, refreshUser, updateLocalUser }),
    [session, user, initializing, authenticating, login, demoLogin, register, logout, refreshUser, updateLocalUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
