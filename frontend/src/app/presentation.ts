export type PresentationTone = "neutral" | "info" | "success" | "warning" | "danger" | "accent";

export type PresentationIcon =
  | "neutral"
  | "clock"
  | "live"
  | "check"
  | "close"
  | "pause"
  | "refund"
  | "archive"
  | "shield";

export interface StatusPresentation {
  label: string;
  tone: PresentationTone;
  icon: PresentationIcon;
  tooltip?: string;
}

const LABELS: Record<string, string> = {
  ADMIN: "Administrador",
  PARTICIPANTE: "Participante",
  USER: "Participante",
  TRADITIONAL: "Esporte tradicional",
  SPORT: "Modalidade",
  ESPORT: "eSport",
  ESPORTS: "eSports",
  MOTORSPORT: "Automobilismo",
  DRAFT: "Rascunho",
  SCHEDULED: "Agendado",
  AGENDADO: "Agendado",
  OPEN_FOR_PREDICTIONS: "Aberto para palpites",
  OPEN: "Aberto",
  ABERTO: "Aberto",
  LIVE: "Ao vivo",
  AO_VIVO: "Ao vivo",
  ACTIVE: "Ativo",
  ATIVO: "Ativo",
  IN_PROGRESS: "Em andamento",
  PENDING: "Pendente",
  PENDENTE: "Pendente",
  PROCESSING: "Em processamento",
  SUSPENDED: "Suspenso",
  PAUSED: "Pausado",
  CLOSED: "Encerrado",
  FINISHED: "Encerrado",
  ENCERRADO: "Encerrado",
  SETTLED: "Finalizado",
  COMPLETED: "Concluído",
  WON: "Vencedor",
  VENCEDOR: "Vencedor",
  LOST: "Perdedor",
  PERDEDOR: "Perdedor",
  CANCELLED: "Cancelado",
  CANCELED: "Cancelado",
  CANCELADO: "Cancelado",
  REFUNDED: "Reembolsado",
  REEMBOLSADO: "Reembolsado",
  POSTPONED: "Adiado",
  ADIADO: "Adiado",
  ARCHIVED: "Arquivado",
  INACTIVE: "Inativo",
  DISABLED: "Desativado",
  ENABLED: "Ativado",
  BLOCKED: "Bloqueado",
  EXPIRED: "Expirado",
  FAILED: "Falhou",
  ERROR: "Erro",
  SUCCESS: "Concluído",
  PUBLISHED: "Publicado",
  HIDDEN: "Oculto",
  REMOVED: "Removido",
  REVIEWED: "Revisado",
  DISMISSED: "Descartado",
  ACTIONED: "Ação aplicada",
  PUBLIC: "Público",
  PRIVATE: "Privado",
  POOL: "Bolão",
  LEAGUE: "Liga",
  COMMON: "Comum",
  RARE: "Rara",
  EPIC: "Épica",
  LEGENDARY: "Lendária",
  STANDARD: "Padrão",
  INDIVIDUAL: "Individual",
  RACE: "Corrida",
  MATCH_WINNER: "Vencedor da partida",
  SERIES_WINNER: "Vencedor da série",
  RACE_WINNER: "Vencedor da corrida",
  BO1: "Melhor de 1",
  BO3: "Melhor de 3",
  BO5: "Melhor de 5",
  WEEKLY: "Semanal",
  MONTHLY: "Mensal",
  ALL: "Geral",
  ALL_TIME: "Geral",
  GENERAL: "Geral",
  CURRENT: "Atual",
  TRUE: "Ativado",
  FALSE: "Desativado",
  ENFORCED: "Obrigatório",
  BRANDING: "Identidade",
  DEMO: "Demonstração",
  VIRTUAL_POINTS: "Pontos virtuais",
  OTHER: "Outro",
  GLOBAL: "Global",
  FRIENDS: "Amigos",
  FIRST_PREDICTION: "Primeiro palpite",
  FIRST_WIN: "Primeira vitória",
  PREDICTION_COUNT: "Quantidade de palpites",
  WON_COUNT: "Palpites vencedores",
  SPORT_VARIETY: "Variedade de modalidades",
  POOL_MEMBER: "Participação em bolão",
  POOL_MEMBER_COUNT: "Participações em bolões",
  INITIAL_BONUS: "Bônus inicial",
  PREDICTION_PLACED: "Palpite registrado",
  PREDICTION_WON: "Palpite vencedor",
  REFUND: "Reembolso",
  CHALLENGE_COMPLETED: "Desafio concluído",
  ACHIEVEMENT: "Conquista",
  ACHIEVEMENT_UNLOCKED: "Conquista desbloqueada",
  RANKING_REWARD: "Recompensa de ranking",
  ADMIN_ADJUSTMENT: "Ajuste administrativo",
  ADMIN_NOTICE: "Aviso da administração",
  COMMENT: "Novo comentário",
  REPLY: "Nova resposta",
  UPDATE: "Atualização",
  EVENT_STARTED: "Evento iniciado",
  EVENT_FINISHED: "Evento encerrado",
  RESULT_PROCESSED: "Resultado processado",
  RANKING_CHANGED: "Mudança no ranking",
  POOL_INVITE: "Convite para bolão",
  HOME: "Participante 1",
  AWAY: "Participante 2",
  DRAW: "Empate",
  READ: "Lida",
  UNREAD: "Não lida",
  RECORDED: "Registrado",
  WINNER: "Vencedor",
  LOSER: "Perdedor",
  VOID: "Anulado",
  EXTERNAL_KEY: "Chave externa",
  CHAMPIONSHIP: "Campeonato",
  COMPETITOR: "Participante",
  EVENT: "Evento",
  MARKET: "Mercado",
  CHALLENGE: "Desafio",
  NOTIFICATION: "Notificação",
  PREDICTION: "Palpite",
  COMMUNITY_REPORT: "Denúncia da comunidade",
  COMMUNITY_POST: "Publicação da comunidade",
  SCORING_RULE: "Regra de pontuação",
  POINT_WALLET: "Carteira de pontos virtuais",
  SYSTEM: "Sistema",
  AUTHENTICATED: "Autenticado",
  ALL_USERS: "Todos os usuários",
  SPORT_CREATED: "Modalidade criada",
  SPORT_UPDATED: "Modalidade atualizada",
  CHAMPIONSHIP_CREATED: "Campeonato criado",
  CHAMPIONSHIP_UPDATED: "Campeonato atualizado",
  COMPETITOR_CREATED: "Participante criado",
  COMPETITOR_UPDATED: "Participante atualizado",
  EVENT_CREATED: "Evento criado",
  EVENT_UPDATED: "Evento atualizado",
  EVENT_RESULT_RECORDED: "Resultado do evento registrado",
  EVENT_CLASSIFICATION_RECORDED: "Classificação do evento registrada",
  EVENT_CANCELLED: "Evento cancelado",
  MARKET_CREATED: "Mercado criado",
  MARKET_UPDATED: "Mercado atualizado",
  MARKET_STATUS_CHANGED: "Status do mercado alterado",
  MARKET_SETTLED: "Mercado liquidado",
  MARKET_CANCELLED: "Mercado cancelado",
  ACHIEVEMENT_CREATED: "Conquista criada",
  ACHIEVEMENT_UPDATED: "Conquista atualizada",
  ACHIEVEMENT_DEACTIVATED: "Conquista desativada",
  CHALLENGE_CREATED: "Desafio criado",
  CHALLENGE_UPDATED: "Desafio atualizado",
  CHALLENGE_DEACTIVATED: "Desafio desativado",
  NOTIFICATION_DISPATCHED: "Notificação enviada",
  NOTIFICATION_DELETED: "Notificação removida",
  COMMUNITY_REPORT_MODERATED: "Denúncia da comunidade moderada",
  COMMUNITY_POST_MODERATED: "Publicação da comunidade moderada",
};

const WORDS: Record<string, string> = {
  ACTION: "ação",
  ACHIEVEMENT: "conquista",
  CANCEL: "cancelamento",
  CATEGORY: "categoria",
  CHALLENGE: "desafio",
  CHAMPIONSHIP: "campeonato",
  CODE: "código",
  COMPETITOR: "participante",
  CREATE: "criação",
  CREATED: "criado",
  DELETE: "exclusão",
  EVENT: "evento",
  EXTERNAL: "externa",
  ID: "ID",
  KEY: "chave",
  MARKET: "mercado",
  MODERATION: "moderação",
  NOTIFICATION: "notificação",
  POINT: "ponto",
  POINTS: "pontos",
  POST: "publicação",
  RESULT: "resultado",
  ROLE: "perfil",
  SETTLE: "liquidação",
  SPORT: "modalidade",
  STATUS: "status",
  UPDATE: "atualização",
  UPDATED: "atualizado",
  USER: "usuário",
  VALUE: "valor",
};

function normalize(value: unknown) {
  return String(value ?? "")
    .trim()
    .replace(/^ROLE_/i, "")
    .replace(/([a-z\d])([A-Z])/g, "$1_$2")
    .replace(/[\s-]+/g, "_")
    .replace(/_+/g, "_")
    .toUpperCase();
}

export function enumLabel(value: unknown) {
  const normalized = normalize(value);
  if (!normalized) return "Não informado";
  if (LABELS[normalized]) return LABELS[normalized];

  const translated = normalized
    .split("_")
    .filter(Boolean)
    .map((word) => WORDS[word] || (/^BO\d+$/.test(word) ? word : word.toLocaleLowerCase("pt-BR")))
    .join(" ");

  return translated.charAt(0).toLocaleUpperCase("pt-BR") + translated.slice(1);
}

export function presentationCode(value: unknown) {
  const raw = String(value ?? "").trim();
  if (!raw) return "não informado";
  return raw
    .replace(/([a-z\d])([A-Z])/g, "$1-$2")
    .replace(/[\s_]+/g, "-")
    .replace(/-+/g, "-")
    .toLocaleLowerCase("pt-BR");
}

export function auditSummaryLabel(value: unknown) {
  const text = String(value ?? "").replace(/\s+/g, " ").trim();
  if (!text) return "Registro administrativo";

  // Older demo data may contain a status both in the event title and in the
  // audit sentence. Keep the immutable audit payload untouched and remove the
  // duplicated word only in the presentation layer.
  return text
    .replace(/\s*·\s*(cancelad[oa]s?)\s+\1\b/giu, " $1")
    .replace(/\b1 vencedores\b/giu, "1 vencedor")
    .replace(/\b1 pontos virtuais creditados\b/giu, "1 ponto virtual creditado")
    .replace(/\b1 destinatário\(s\)/giu, "1 destinatário");
}

const PRESENTATIONS: Record<string, Omit<StatusPresentation, "label"> & { label?: string }> = {
  DRAFT: { tone: "neutral", icon: "neutral" },
  SCHEDULED: { tone: "info", icon: "clock" },
  AGENDADO: { tone: "info", icon: "clock" },
  OPEN_FOR_PREDICTIONS: {
    tone: "success",
    icon: "check",
    tooltip: "O evento ainda aceita palpites dentro do prazo publicado.",
  },
  OPEN: { tone: "success", icon: "check" },
  ABERTO: { tone: "success", icon: "check" },
  ACTIVE: { tone: "success", icon: "check" },
  ATIVO: { tone: "success", icon: "check" },
  ENABLED: { tone: "success", icon: "check" },
  LIVE: { tone: "danger", icon: "live" },
  AO_VIVO: { tone: "danger", icon: "live" },
  IN_PROGRESS: { tone: "info", icon: "live" },
  PENDING: { tone: "info", icon: "clock" },
  PENDENTE: { tone: "info", icon: "clock" },
  PROCESSING: { tone: "info", icon: "clock" },
  WON: { tone: "success", icon: "check" },
  WINNER: { tone: "success", icon: "check" },
  VENCEDOR: { tone: "success", icon: "check" },
  COMPLETED: { tone: "success", icon: "check" },
  SUCCESS: { tone: "success", icon: "check" },
  FINISHED: { tone: "accent", icon: "check" },
  ENCERRADO: { tone: "accent", icon: "check" },
  SETTLED: { tone: "accent", icon: "check", tooltip: "Resultado processado e recompensas consolidadas." },
  CLOSED: { tone: "neutral", icon: "archive" },
  ARCHIVED: { tone: "neutral", icon: "archive" },
  REVIEWED: { tone: "accent", icon: "shield" },
  READ: { tone: "neutral", icon: "check" },
  RECORDED: { tone: "accent", icon: "check" },
  ACTIONED: { tone: "accent", icon: "shield" },
  LOST: { tone: "danger", icon: "close" },
  PERDEDOR: { tone: "danger", icon: "close" },
  LOSER: { tone: "danger", icon: "close" },
  CANCELLED: { tone: "danger", icon: "close" },
  CANCELED: { tone: "danger", icon: "close" },
  CANCELADO: { tone: "danger", icon: "close" },
  BLOCKED: { tone: "danger", icon: "shield" },
  FAILED: { tone: "danger", icon: "close" },
  ERROR: { tone: "danger", icon: "close" },
  REMOVED: { tone: "danger", icon: "close" },
  VOID: { tone: "warning", icon: "refund" },
  REFUNDED: { tone: "warning", icon: "refund" },
  REEMBOLSADO: { tone: "warning", icon: "refund" },
  POSTPONED: { tone: "warning", icon: "pause" },
  ADIADO: { tone: "warning", icon: "pause" },
  SUSPENDED: { tone: "warning", icon: "pause" },
  PAUSED: { tone: "warning", icon: "pause" },
  EXPIRED: { tone: "warning", icon: "clock" },
  INACTIVE: { tone: "neutral", icon: "pause" },
  DISABLED: { tone: "neutral", icon: "pause" },
  HIDDEN: { tone: "warning", icon: "shield" },
  DISMISSED: { tone: "neutral", icon: "archive" },
};

export function statusPresentation(value: unknown): StatusPresentation {
  const normalized = normalize(value);
  const definition = PRESENTATIONS[normalized];
  if (definition) return { label: definition.label || enumLabel(normalized), ...definition };
  return { label: enumLabel(normalized), tone: "neutral", icon: "neutral" };
}
