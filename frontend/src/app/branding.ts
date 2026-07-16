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

export const isExplicitDemoMode = import.meta.env.VITE_DEMO_MODE === "true";

export const demoCredentials = {
  admin: {
    name: "Admin Arena",
    email: "admin@arenapredict.com",
    password: "Admin@123",
  },
  participant: {
    name: "Jogador Demo",
    email: "jogador@arenapredict.com",
    password: "Jogador@123",
  },
} as const;
