import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
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
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(false);
  const [walletError, setWalletError] = useState<string | null>(null);
  const [notificationsError, setNotificationsError] = useState<string | null>(null);

  const refreshWallet = useCallback(async () => {
    if (!session) return;
    try {
      setWallet(await walletApi.get());
      setWalletError(null);
    } catch (reason) {
      setWalletError(reason instanceof Error ? reason.message : "Não foi possível carregar o saldo.");
      throw reason;
    }
  }, [session]);

  const refreshNotifications = useCallback(async () => {
    if (!session) return;
    try {
      setNotifications(asList(await notificationsApi.list()));
      setNotificationsError(null);
    } catch (reason) {
      setNotificationsError(reason instanceof Error ? reason.message : "Não foi possível carregar as notificações.");
      throw reason;
    }
  }, [session]);

  useEffect(() => {
    let active = true;
    if (!session) {
      setWallet(null);
      setNotifications([]);
      setWalletError(null);
      setNotificationsError(null);
      return undefined;
    }
    setLoading(true);
    setWalletError(null);
    setNotificationsError(null);
    Promise.allSettled([walletApi.get(), notificationsApi.list()])
      .then(([walletResult, notificationResult]) => {
        if (!active) return;
        if (walletResult.status === "fulfilled") setWallet(walletResult.value);
        else setWalletError(walletResult.reason instanceof Error ? walletResult.reason.message : "Não foi possível carregar o saldo.");
        if (notificationResult.status === "fulfilled") setNotifications(asList(notificationResult.value));
        else setNotificationsError(notificationResult.reason instanceof Error ? notificationResult.reason.message : "Não foi possível carregar as notificações.");
      })
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [session]);

  const markNotificationRead = useCallback(async (id: number | string) => {
    await notificationsApi.read(id);
    setNotifications((current) => current.map((item) => (item.id === id ? { ...item, read: true } : item)));
  }, []);

  const markAllNotificationsRead = useCallback(async () => {
    await notificationsApi.readAll();
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
