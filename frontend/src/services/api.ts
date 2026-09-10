import { brand } from "../app/branding";
import type {
  Achievement,
  AdminDashboard,
  ArenaEvent,
  AuthSession,
  Championship,
  Challenge,
  CommunityComment,
  CommunityPost,
  DashboardData,
  Notification,
  PageResponse,
  Pool,
  PlayerProfile,
  Prediction,
  PredictionOption,
  RankingRow,
  Sport,
  User,
  UserRole,
  ProfilePreferences,
  Wallet,
  WalletTransaction,
  MarketTemplate,
} from "../types";

const configuredApiBase = (import.meta.env.VITE_API_URL || "/api").replace(/\/$/, "");
// O backend deste projeto expõe todo o contrato sob /api. Aceitamos tanto a
// origem (http://localhost:8080) quanto a base completa (.../api) no ambiente.
const API_BASE = configuredApiBase.endsWith("/api") ? configuredApiBase : `${configuredApiBase}/api`;
const REQUEST_TIMEOUT_MS = Number(import.meta.env.VITE_API_TIMEOUT_MS || 12_000);
export const AUTH_STORAGE_KEY = `${brand.storageNamespace}:session`;

export class ApiError extends Error {
  status: number;
  code?: string;
  fieldErrors?: Record<string, string>;

  constructor(message: string, status = 0, code?: string, fieldErrors?: Record<string, string>) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

function normalizeRole(role?: string): UserRole {
  const normalized = typeof role === "string"
    ? role.trim().toUpperCase().replace(/^ROLE_/, "")
    : undefined;
  if (normalized === "ADMIN" || normalized === "PARTICIPANTE") return normalized;
  throw new ApiError("O servidor retornou um perfil de acesso inválido.", 502, "INVALID_AUTH_ROLE");
}

function normalizeSession(payload: Partial<AuthSession> & { id?: number }): AuthSession {
  return {
    token: String(payload.token || ""),
    userId: Number(payload.userId ?? payload.id ?? 0),
    name: String(payload.name || "Participante"),
    email: String(payload.email || ""),
    role: normalizeRole(payload.role),
    avatarUrl: payload.avatarUrl,
  };
}

function readStoredSession(): AuthSession | null {
  try {
    // Keep the access token scoped to this browser tab. Migrate an older
    // localStorage session once so existing demo sessions are not abruptly lost.
    const legacy = window.localStorage.getItem(AUTH_STORAGE_KEY);
    const raw = window.sessionStorage.getItem(AUTH_STORAGE_KEY) || legacy;
    if (!raw) return null;
    if (legacy && !window.sessionStorage.getItem(AUTH_STORAGE_KEY)) {
      window.sessionStorage.setItem(AUTH_STORAGE_KEY, legacy);
      window.localStorage.removeItem(AUTH_STORAGE_KEY);
    }
    const parsed = normalizeSession(JSON.parse(raw));
    return parsed.token ? parsed : null;
  } catch {
    window.sessionStorage.removeItem(AUTH_STORAGE_KEY);
    window.localStorage.removeItem(AUTH_STORAGE_KEY);
    return null;
  }
}

export const sessionStorage = {
  read: readStoredSession,
  save(session: AuthSession) {
    window.sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(normalizeSession(session)));
    window.localStorage.removeItem(AUTH_STORAGE_KEY);
  },
  clear() {
    window.sessionStorage.removeItem(AUTH_STORAGE_KEY);
    window.localStorage.removeItem(AUTH_STORAGE_KEY);
  },
};

function safeServerMessage(message?: string) {
  if (!message) return undefined;
  const value = message.trim();
  if (!value || value.length > 240 || /[\r\n]/.test(value)) return undefined;
  if (/[<>]/.test(value)) return undefined;
  // Internal enum values are useful in contracts and logs, but should never
  // become user-facing copy (for example, OPEN_FOR_PREDICTIONS).
  if (/\b[A-Z][A-Z\d]*(?:_[A-Z\d]+)+\b/.test(value)) return undefined;
  if (/exception|stack\s*trace|hibernate|postgres|sqlstate|jdbc|at\s+[\w.$]+\(|select\s+.+\s+from/i.test(value)) return undefined;
  if (/\b(?:must|should|required|invalid|validation|failed|failure|cannot|unable|expected|constraint|property|request|field|value|between|greater|less|blank|empty|null|bad|unauthorized|forbidden|not\s+found|already\s+exists)\b/i.test(value)) return undefined;
  return value;
}

function safeFieldErrors(value: unknown): Record<string, string> | undefined {
  if (!value || typeof value !== "object" || Array.isArray(value)) return undefined;
  const entries = Object.entries(value as Record<string, unknown>)
    .filter(([field]) => /^[a-zA-Z][\w.\[\]-]{0,79}$/.test(field))
    .map(([field, message]) => [field, safeServerMessage(typeof message === "string" ? message : undefined) || "Valor inválido."]);
  return entries.length ? Object.fromEntries(entries) : undefined;
}

function friendlyMessage(status: number, path: string, serverMessage?: string) {
  const publicMessage = safeServerMessage(serverMessage);
  if (status === 401) {
    return path.includes("/auth/login")
      ? "E-mail ou senha inválidos. Confira os dados e tente novamente."
      : "Sua sessão expirou. Entre novamente para continuar.";
  }
  if (status === 403) return "Você não possui permissão para realizar esta ação.";
  if (status === 404) return "O conteúdo solicitado não foi encontrado.";
  if (status === 409) return publicMessage || "Esta ação já foi processada.";
  if (status === 422 || status === 400) return publicMessage || "Revise os dados informados.";
  if (status >= 500) return "O serviço está temporariamente indisponível. Tente novamente em instantes.";
  return publicMessage || "Não foi possível concluir a solicitação.";
}

type RequestOptions = Omit<RequestInit, "body"> & {
  body?: unknown;
  timeoutMs?: number;
  auth?: boolean;
  idempotencyKey?: string;
};

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const controller = new AbortController();
  let timedOut = false;
  const abortFromCaller = () => controller.abort();
  options.signal?.addEventListener("abort", abortFromCaller, { once: true });
  const timeout = window.setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, options.timeoutMs ?? REQUEST_TIMEOUT_MS);
  const session = readStoredSession();
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (options.body !== undefined) headers.set("Content-Type", "application/json");
  if (options.auth !== false && session?.token) headers.set("Authorization", `Bearer ${session.token}`);
  if (options.idempotencyKey) headers.set("Idempotency-Key", options.idempotencyKey);

  try {
    const response = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers,
      signal: controller.signal,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });

    if (!response.ok) {
      let body: Record<string, unknown> = {};
      try {
        body = (await response.json()) as Record<string, unknown>;
      } catch {
        body = {};
      }
      const serverMessage = typeof body.message === "string"
        ? body.message
        : typeof body.error === "string"
          ? body.error
          : undefined;
      const message = friendlyMessage(response.status, path, serverMessage);
      if (options.auth !== false && response.status === 401) {
        window.dispatchEvent(new CustomEvent("arena:unauthorized", {
          detail: { sessionToken: session?.token ?? null },
        }));
      }
      if (options.auth !== false && response.status === 403) {
        window.dispatchEvent(new CustomEvent("arena:forbidden", {
          detail: { sessionToken: session?.token ?? null },
        }));
      }
      throw new ApiError(
        message,
        response.status,
        typeof body.code === "string" ? body.code : undefined,
        safeFieldErrors(body.fieldErrors),
      );
    }

    if (response.status === 204) return undefined as T;
    const contentType = response.headers.get("content-type") || "";
    return contentType.includes("application/json") ? ((await response.json()) as T) : ((await response.text()) as T);
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error instanceof DOMException && error.name === "AbortError") {
      if (!timedOut) throw new ApiError("Solicitação cancelada.", 0, "REQUEST_CANCELLED");
      throw new ApiError("A solicitação demorou mais que o esperado. Tente novamente.", 408, "REQUEST_TIMEOUT");
    }
    throw new ApiError("Não foi possível conectar ao servidor. Verifique se a plataforma está disponível.", 0, "NETWORK_ERROR");
  } finally {
    window.clearTimeout(timeout);
    options.signal?.removeEventListener("abort", abortFromCaller);
  }
}

function query(params: Record<string, string | number | boolean | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") search.set(key, String(value));
  });
  const value = search.toString();
  return value ? `?${value}` : "";
}

export function asList<T>(value: T[] | PageResponse<T> | { items?: T[] } | null | undefined): T[] {
  if (Array.isArray(value)) return value;
  if (value && "content" in value && Array.isArray(value.content)) return value.content;
  if (value && "items" in value && Array.isArray(value.items)) return value.items;
  return [];
}

function normalizeEvent(event: ArenaEvent): ArenaEvent {
  const participantCompetitors = event.participants?.map((item) => ({ ...item.competitor, score: item.scoreLabel || item.position })) || [];
  const home = event.homeCompetitor || event.home || event.homeTeam || event.competitors?.[0] || participantCompetitors[0];
  const away = event.awayCompetitor || event.away || event.awayTeam || event.competitors?.[1] || participantCompetitors[1];
  return {
    ...event,
    home: home ? { ...home, score: event.homeScore ?? home.score } : undefined,
    away: away ? { ...away, score: event.awayScore ?? away.score } : undefined,
    phase: event.phase || (event as ArenaEvent & { stage?: string }).stage,
    predictionDeadline: event.predictionDeadline || event.predictionClosesAt,
    liveClock: event.liveClock || event.clock,
    demoLiveData: event.demoLiveData ?? event.demo,
    competitors: event.competitors?.length ? event.competitors : participantCompetitors,
    statistics: event.statistics || parseLiveStatistics(event.liveData),
    markets: event.markets?.map((market) => ({
      ...market,
      options: market.options?.map((option) => ({
        ...option,
        name: option.name || option.label,
        suspended: option.suspended ?? ((option as PredictionOption & { active?: boolean }).active === false),
      })) || [],
    })) || [],
  };
}

function parseLiveStatistics(raw?: string): Record<string, number | string> | undefined {
  if (!raw) return undefined;
  try {
    const value = JSON.parse(raw) as Record<string, unknown>;
    const stats: Record<string, number | string> = {};
    const labels: Record<string, string> = { shots: "Finalizações", possession: "Posse de bola", rounds: "Rodadas" };
    Object.entries(value).forEach(([key, item]) => {
      if (key === "demo") return;
      if (key === "maps" && Array.isArray(item)) {
        item.slice(0, 3).forEach((map) => {
          if (map && typeof map === "object") {
            const record = map as { name?: unknown; score?: unknown };
            if (record.name && record.score) stats[String(record.name)] = String(record.score);
          }
        });
      } else if (Array.isArray(item) && item.length >= 2) {
        const suffix = key === "possession" ? "%" : "";
        stats[labels[key] || key] = `${String(item[0])}${suffix} × ${String(item[1])}${suffix}`;
      } else if (["string", "number"].includes(typeof item)) {
        stats[labels[key] || key] = item as string | number;
      }
    });
    return Object.keys(stats).length ? stats : undefined;
  } catch {
    return undefined;
  }
}

function normalizeRanking(row: RankingRow): RankingRow {
  return {
    ...row,
    name: row.name || row.playerName || row.participant,
    hits: row.hits ?? row.correctPredictions,
    predictions: row.predictions ?? row.totalPredictions,
  };
}

function normalizePool(pool: Pool): Pool {
  return {
    ...pool,
    privacy: pool.privacy || (pool.publicPool ? "PUBLIC" : "PRIVATE"),
    owner: pool.owner ?? false,
  };
}

export const authApi = {
  async login(email: string, password: string) {
    const result = await request<AuthSession>("/auth/login", {
      method: "POST",
      body: { email, password },
      auth: false,
    });
    return normalizeSession(result);
  },
  async register(payload: { name: string; email: string; password: string }) {
    const result = await request<AuthSession>("/auth/register", { method: "POST", body: payload, auth: false });
    return normalizeSession(result);
  },
  async demo(profile: "PARTICIPANT" | "ADMIN") {
    const result = await request<AuthSession>("/auth/demo", {
      method: "POST",
      body: { profile },
      auth: false,
    });
    return normalizeSession(result);
  },
  async me(): Promise<User> {
    const result = await request<User & { id?: number }>("/auth/me");
    return {
      userId: Number(result.userId ?? result.id ?? 0),
      name: result.name,
      email: result.email,
      role: normalizeRole(result.role),
      avatarUrl: result.avatarUrl,
    };
  },
  logout: () => request<void>("/auth/logout", { method: "POST" }),
};

export const dashboardApi = {
  async get() {
    const result = await request<DashboardData>("/dashboard");
    return {
      ...result,
      featuredEvents: result.featuredEvents?.map(normalizeEvent),
      liveEvents: result.liveEvents?.map(normalizeEvent),
      upcomingEvents: result.upcomingEvents?.map(normalizeEvent),
      recentPredictions: result.recentPredictions,
      activePools: Array.isArray(result.activePools) ? result.activePools.map(normalizePool) : result.activePools,
      weeklyRanking: result.weeklyRanking?.map(normalizeRanking),
    };
  },
};

export const eventsApi = {
  list: (filters: { status?: string; sport?: string; featured?: boolean; page?: number; size?: number } = {}) =>
    request<ArenaEvent[] | PageResponse<ArenaEvent>>(`/events${query(filters)}`).then((result) => asList(result).map(normalizeEvent)),
  get: (id: number | string) => request<ArenaEvent>(`/events/${id}`).then(normalizeEvent),
  live: () => request<ArenaEvent[] | PageResponse<ArenaEvent>>("/events/live").then((result) => asList(result).map(normalizeEvent)),
};

export const predictionsApi = {
  list: (filters: { status?: string; page?: number; size?: number } = {}) =>
    request<Prediction[] | PageResponse<Prediction>>(`/predictions${query(filters)}`),
  create: (payload: {
    eventId: number | string;
    marketId: number | string;
    optionId: number | string;
    stakePoints: number;
    poolId?: number | string;
    idempotencyKey?: string;
  }) =>
    request<Prediction>("/predictions", {
      method: "POST",
      body: payload,
      idempotencyKey: payload.idempotencyKey,
    }),
  cancel: (id: number | string) => request<Prediction>(`/predictions/${id}/cancel`, { method: "POST" }),
};

export const walletApi = {
  get: () => request<Wallet>("/wallet"),
  transactions: (page = 0, size = 30) =>
    request<WalletTransaction[] | PageResponse<WalletTransaction>>(`/wallet/transactions${query({ page, size })}`),
};

export const poolsApi = {
  list: () => request<Pool[] | PageResponse<Pool>>("/pools").then((result) => asList(result).map(normalizePool)),
  get: (id: number | string) => request<Pool>(`/pools/${id}`).then(normalizePool),
  create: (payload: Partial<Pool>) => request<Pool>("/pools", { method: "POST", body: {
    ...payload,
    publicPool: payload.publicPool ?? payload.privacy === "PUBLIC",
    rules: payload.rules || "Classificação por pontos virtuais acumulados nos palpites do período.",
    virtualPrizePoints: payload.virtualPrizePoints ?? 0,
  } }).then(normalizePool),
  join: (inviteCode: string) => request<Pool>("/pools/join", { method: "POST", body: { inviteCode } }),
  joinPublic: (id: number | string) => request<Pool>(`/pools/${id}/join`, { method: "POST" }).then(normalizePool),
  leave: (id: number | string) => request<void>(`/pools/${id}/leave`, { method: "POST" }),
  ranking: (id: number | string) => request<RankingRow[] | PageResponse<RankingRow>>(`/pools/${id}/ranking`).then((result) => asList(result).map(normalizeRanking)),
};

export const rankingsApi = {
  list: (filters: { period?: string; sport?: string; championship?: string; scope?: string } = {}) =>
    request<RankingRow[] | PageResponse<RankingRow>>(`/rankings${query(filters)}`).then((result) => asList(result).map(normalizeRanking)),
};

export const notificationsApi = {
  list: () => request<Notification[] | PageResponse<Notification>>("/notifications").then((result) => asList(result).map((item) => ({ ...item, link: item.link || item.targetUrl }))),
  read: (id: number | string) => request<Notification>(`/notifications/${id}/read`, { method: "PATCH" }),
  readAll: () => request<void>("/notifications/read-all", { method: "PATCH" }),
};

export const catalogApi = {
  sports: () => request<Sport[] | PageResponse<Sport>>("/sports").then((result) => asList(result).map((item) => ({ ...item, slug: item.slug || (item as Sport & { code?: string }).code }))),
  championships: (sport?: string) =>
    request<Championship[] | PageResponse<Championship>>(`/championships${query({ sport })}`),
};

export const profileApi = {
  get: () => request<PlayerProfile>("/profile"),
  update: (payload: Record<string, unknown>) => request<PlayerProfile>("/profile", { method: "PATCH", body: payload }),
  changePassword: (payload: { currentPassword: string; newPassword: string }) =>
    request<void>("/profile/password", { method: "PATCH", body: payload }),
  preferences: (payload: Record<string, unknown>) =>
    request<ProfilePreferences>("/profile/preferences", { method: "PATCH", body: payload }),
};

export const achievementsApi = {
  list: () => request<Achievement[] | PageResponse<Achievement>>("/achievements"),
};

export const challengesApi = {
  list: () => request<Challenge[] | PageResponse<Challenge>>("/challenges"),
};

export const communityApi = {
  feed: (page = 0) => request<PageResponse<CommunityPost> | CommunityPost[]>(`/community/posts${query({ page })}`),
  createPost: (content: string, topic?: string) => request<CommunityPost>("/community/posts", {
    method: "POST",
    body: { content, ...(topic?.trim() ? { topic: topic.trim() } : {}) },
  }),
  like: (id: number | string) => request<CommunityPost>(`/community/posts/${id}/like`, { method: "POST" }),
  comments: (id: number | string) =>
    request<CommunityComment[]>(`/community/posts/${id}/comments`),
  comment: (id: number | string, content: string) =>
    request<CommunityComment>(`/community/posts/${id}/comments`, { method: "POST", body: { content } }),
  report: (id: number | string, reason: string) =>
    request<void>(`/community/posts/${id}/reports`, { method: "POST", body: { reason } }),
};

const adminListPaths: Record<string, string> = {
  sports: "/admin/sports",
  championships: "/admin/championships",
  competitors: "/admin/competitors",
  events: "/admin/events",
  markets: "/admin/markets",
  users: "/admin/users",
  pools: "/admin/pools",
  "scoring-rules": "/admin/scoring-rules",
  reports: "/admin/reports",
  audit: "/admin/audit",
  settings: "/admin/settings",
  moderation: "/admin/moderation",
  achievements: "/admin/achievements",
  challenges: "/admin/challenges",
  notifications: "/admin/notifications",
};

export interface MarketSettlement {
  marketId: number | string;
  correctOptionKey: string;
  winners: number;
  losers: number;
  rewardedPoints: number;
  alreadySettled: boolean;
}

export const adminApi = {
  dashboard: () => request<AdminDashboard>("/admin/dashboard"),
  async list<T>(resource: string, params: Record<string, string | number | boolean | undefined> = {}) {
    if (resource === "results") return request<T[] | PageResponse<T>>(`/admin/events${query(params)}`);
    const path = adminListPaths[resource];
    if (!path) throw new ApiError("Recurso administrativo não reconhecido.", 400, "UNKNOWN_ADMIN_RESOURCE");
    return request<T[] | PageResponse<T>>(`${path}${query(params)}`);
  },
  create: <T>(resource: string, payload: Record<string, unknown>) =>
    request<T>(`/admin/${resource}`, { method: "POST", body: adminPayload(resource, payload) }),
  update: <T>(resource: string, id: number | string, payload: Record<string, unknown>) =>
    request<T>(`/admin/${resource}/${id}`, { method: "PUT", body: adminPayload(resource, payload) }),
  recordEventResult: (
    eventId: number | string,
    payload: { homeScore: number; awayScore: number; finishEvent: boolean; resultData?: Record<string, string>; settleMarkets?: boolean },
    idempotencyKey = createIdempotencyKey(),
  ) =>
    request<Record<string, unknown>>(`/admin/events/${eventId}/result`, {
      method: "PUT",
      body: payload,
      idempotencyKey,
    }),
  recordEventClassification: (eventId: number | string, payload: {
    participants: Array<{
      competitorId: number;
      displayOrder?: number;
      position?: number | null;
      scoreLabel?: string | null;
    }>;
    finishEvent: boolean;
    resultData?: Record<string, string>;
    settleMarkets?: boolean;
  }, idempotencyKey = createIdempotencyKey()) => request<Record<string, unknown>>(`/admin/events/${eventId}/classification`, {
    method: "PUT",
    body: payload,
    idempotencyKey,
  }),
  settleMarket: (marketId: number | string, correctOptionKey: string) =>
    request<MarketSettlement>(`/admin/markets/${marketId}/settle`, {
      method: "POST",
      body: { correctOptionKey },
      idempotencyKey: createIdempotencyKey(),
    }),
  marketTemplates: (eventId: number | string) => request<MarketTemplate[]>(`/admin/events/${eventId}/market-templates`),
  generateMarkets: (eventId: number | string) => request<unknown>(`/admin/events/${eventId}/markets/generate`, {
    method: "POST", idempotencyKey: createIdempotencyKey(),
  }),
  moderateReport: (reportId: number | string, payload: { status: "REVIEWED" | "DISMISSED"; moderatorNote?: string }) =>
    request<Record<string, unknown>>(`/admin/community/reports/${reportId}`, {
      method: "PATCH",
      body: payload,
    }),
  hideCommunityPost: (postId: number | string) =>
    request<Record<string, unknown>>(`/admin/community/posts/${postId}`, {
      method: "PATCH",
      body: { status: "HIDDEN" },
    }),
  cancelEvent: (eventId: number | string) =>
    request<{ refundedPredictions: number }>(`/admin/events/${eventId}/cancel`, { method: "POST" }),
  cancelMarket: (marketId: number | string) =>
    request<{ refundedPredictions: number }>(`/admin/markets/${marketId}/cancel`, { method: "POST" }),
};

function adminPayload(resource: string, payload: Record<string, unknown>) {
  const next = { ...payload };
  if (resource === "sports") {
    next.code = next.code || next.slug;
    delete next.slug;
    if (next.category === "SPORT") next.category = "TRADITIONAL";
    if (next.category === "ESPORT") next.category = "ESPORTS";
    next.displayOrder ??= 0;
  }
  if (resource === "competitors") {
    next.code = next.code || next.shortName;
    delete next.shortName;
  }
  if (resource === "events") {
    next.externalKey ||= `arena-${Date.now()}`;
    next.predictionClosesAt = next.predictionClosesAt || next.predictionDeadline;
    delete next.predictionDeadline;
    for (const key of ["startsAt", "predictionClosesAt"]) {
      if (typeof next[key] === "string" && next[key]) next[key] = new Date(String(next[key])).toISOString();
    }
    if (typeof next.participantsJson === "string") {
      next.participants = JSON.parse(String(next.participantsJson));
      delete next.participantsJson;
    }
  }
  if (resource === "championships") {
    for (const key of ["startsAt", "endsAt"]) {
      if (typeof next[key] === "string" && next[key]) next[key] = new Date(String(next[key])).toISOString();
    }
  }
  if (resource === "markets") {
    for (const key of ["opensAt", "closesAt"]) {
      if (typeof next[key] === "string" && next[key]) next[key] = new Date(String(next[key])).toISOString();
    }
  }
  if (resource === "challenges") {
    for (const key of ["startsAt", "expiresAt"]) {
      if (typeof next[key] === "string" && next[key]) next[key] = new Date(String(next[key])).toISOString();
    }
  }
  return next;
}

export function createIdempotencyKey() {
  return typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
