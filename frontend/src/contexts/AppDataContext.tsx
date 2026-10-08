import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { asList, notificationsApi, walletApi } from "../services/api";
import type { Notification, Wallet } from "../types";
import { useAuth } from "./AuthContext";

interface AppDataContextValue {
  wallet: Wallet | null;
  notifications: Notification[];
  loading: boolean;
  walletError: string | null;
  notificationsError: string | null;
  unreadCount: number;
  refreshWallet: () => Promise<void>;
  refreshNotifications: () => Promise<void>;
  markNotificationRead: (id: number | string) => Promise<void>;
  markAllNotificationsRead: () => Promise<void>;
}

const AppDataContext = createContext<AppDataContextValue | null>(null);

export function AppDataProvider({ children }: { children: ReactNode }) {
  const { session } = useAuth();
  const sessionToken = session?.token ?? null;
  const activeSessionToken = useRef(sessionToken);
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(false);
  const [walletError, setWalletError] = useState<string | null>(null);
  const [notificationsError, setNotificationsError] = useState<string | null>(null);

  useLayoutEffect(() => {
    activeSessionToken.current = sessionToken;
    // Clear the previous identity before painting another account's screen.
    setWallet(null);
    setNotifications([]);
    setWalletError(null);
    setNotificationsError(null);
    setLoading(Boolean(sessionToken));
  }, [sessionToken]);

  const refreshWallet = useCallback(async () => {
    if (!sessionToken) return;
    const token = sessionToken;
    try {
      const nextWallet = await walletApi.get();
      if (activeSessionToken.current !== token) return;
      setWallet(nextWallet);
      setWalletError(null);
    } catch (reason) {
      if (activeSessionToken.current === token) setWalletError(reason instanceof Error ? reason.message : "Não foi possível carregar o saldo.");
      throw reason;
    }
  }, [sessionToken]);

  const refreshNotifications = useCallback(async () => {
    if (!sessionToken) return;
    const token = sessionToken;
    try {
      const nextNotifications = await notificationsApi.list();
      if (activeSessionToken.current !== token) return;
      setNotifications(asList(nextNotifications));
      setNotificationsError(null);
    } catch (reason) {
      if (activeSessionToken.current === token) setNotificationsError(reason instanceof Error ? reason.message : "Não foi possível carregar as notificações.");
      throw reason;
    }
  }, [sessionToken]);

  useEffect(() => {
    let active = true;
    if (!sessionToken) return undefined;
    Promise.allSettled([walletApi.get(), notificationsApi.list()])
      .then(([walletResult, notificationResult]) => {
        if (!active || activeSessionToken.current !== sessionToken) return;
        if (walletResult.status === "fulfilled") setWallet(walletResult.value);
        else setWalletError(walletResult.reason instanceof Error ? walletResult.reason.message : "Não foi possível carregar o saldo.");
        if (notificationResult.status === "fulfilled") setNotifications(asList(notificationResult.value));
        else setNotificationsError(notificationResult.reason instanceof Error ? notificationResult.reason.message : "Não foi possível carregar as notificações.");
      })
      .finally(() => active && activeSessionToken.current === sessionToken && setLoading(false));
    return () => {
      active = false;
    };
  }, [sessionToken]);

  const markNotificationRead = useCallback(async (id: number | string) => {
    const token = activeSessionToken.current;
    if (!token) return;
    await notificationsApi.read(id);
    if (activeSessionToken.current !== token) return;
    setNotifications((current) => current.map((item) => (item.id === id ? { ...item, read: true } : item)));
  }, []);

  const markAllNotificationsRead = useCallback(async () => {
    const token = activeSessionToken.current;
    if (!token) return;
    await notificationsApi.readAll();
    if (activeSessionToken.current !== token) return;
    setNotifications((current) => current.map((item) => ({ ...item, read: true })));
  }, []);

  const value = useMemo(
    () => ({
      wallet,
      notifications,
      loading,
      walletError,
      notificationsError,
      unreadCount: notifications.filter((item) => !item.read && !item.readAt).length,
      refreshWallet,
      refreshNotifications,
      markNotificationRead,
      markAllNotificationsRead,
    }),
    [wallet, notifications, loading, walletError, notificationsError, refreshWallet, refreshNotifications, markNotificationRead, markAllNotificationsRead],
  );

  return <AppDataContext.Provider value={value}>{children}</AppDataContext.Provider>;
}

export function useAppData() {
  const context = useContext(AppDataContext);
  if (!context) throw new Error("useAppData must be used inside AppDataProvider");
  return context;
}
