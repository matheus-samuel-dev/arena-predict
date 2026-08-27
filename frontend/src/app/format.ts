import { statusPresentation } from "./presentation";

export function points(value: unknown) {
  const number = Number(value || 0);
  return new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 0 }).format(Number.isFinite(number) ? number : 0);
}

export function percentage(value: unknown, fallback = 0) {
  const number = Number(value ?? fallback);
  return `${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 }).format(Number.isFinite(number) ? number : fallback)}%`;
}

export function dateTime(value?: string | null, withYear = false) {
  if (!value) return "A definir";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "A definir";
  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "short",
    year: withYear ? "numeric" : undefined,
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export function relativeTime(value?: string | null) {
  if (!value) return "agora";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "agora";
  const difference = date.getTime() - Date.now();
  const abs = Math.abs(difference);
  const formatter = new Intl.RelativeTimeFormat("pt-BR", { numeric: "auto" });
  if (abs < 60_000) return formatter.format(Math.round(difference / 1000), "second");
  if (abs < 3_600_000) return formatter.format(Math.round(difference / 60_000), "minute");
  if (abs < 86_400_000) return formatter.format(Math.round(difference / 3_600_000), "hour");
  return formatter.format(Math.round(difference / 86_400_000), "day");
}

export function multiplier(value: unknown) {
  const number = Number(value || 0);
  return `${new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(number)}×`;
}

export function eventStatusLabel(status?: string) {
  return status ? statusPresentation(status).label : "Agendado";
}

export function predictionStatusLabel(status?: string) {
  return status ? statusPresentation(status).label : "Pendente";
}

export function getWalletBalance(wallet: { balance?: number; balancePoints?: number; availablePoints?: number } | null) {
  return Number(wallet?.balancePoints ?? wallet?.availablePoints ?? wallet?.balance ?? 0);
}

interface TeamLike {
  name?: string;
  shortName?: string;
  code?: string;
  logoUrl?: string | null;
  imageUrl?: string | null;
  score?: number | string | null;
}

export function eventTeams(event: {
  home?: TeamLike;
  away?: TeamLike;
  homeTeam?: TeamLike;
  awayTeam?: TeamLike;
  homeCompetitor?: TeamLike;
  awayCompetitor?: TeamLike;
  competitors?: TeamLike[];
  title?: string;
}): readonly [TeamLike, TeamLike] {
  const home = event.home || event.homeTeam || event.homeCompetitor || event.competitors?.[0];
  const away = event.away || event.awayTeam || event.awayCompetitor || event.competitors?.[1];
  if (!home && !away && event.title) {
    const [left, right] = event.title.split(/\s+(?:vs\.?|x)\s+/i);
    return [{ name: left || event.title }, { name: right || "A definir" }];
  }
  return [home || { name: "A definir" }, away || { name: "A definir" }];
}

export function sportName(value: unknown) {
  if (typeof value === "string") return value;
  if (value && typeof value === "object" && "name" in value) return String((value as { name: unknown }).name);
  return "Modalidade";
}

export function championshipName(value: unknown) {
  if (typeof value === "string") return value;
  if (value && typeof value === "object" && "name" in value) return String((value as { name: unknown }).name);
  return "Campeonato";
}
