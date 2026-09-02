export const brand = {
  name: import.meta.env.VITE_APP_NAME || "ArenaPredict",
  shortName: import.meta.env.VITE_APP_SHORT_NAME || "Arena",
  tagline: import.meta.env.VITE_APP_TAGLINE || "Sua leitura. Sua arena.",
  description:
    import.meta.env.VITE_APP_DESCRIPTION ||
    "Previsões esportivas, bolões e ligas com pontos exclusivamente virtuais.",
  storageNamespace: import.meta.env.VITE_APP_STORAGE_NAMESPACE || "arena-predict",
  supportEmail: import.meta.env.VITE_SUPPORT_EMAIL || "suporte@arenapredict.com",
} as const;

/**
 * Controla apenas a exibição da entrada rápida. A autorização efetiva é feita
 * pelo backend, que só disponibiliza o endpoint quando APP_DEMO_ENABLED=true.
 */
export const isExplicitDemoMode = import.meta.env.VITE_DEMO_MODE === "true";
