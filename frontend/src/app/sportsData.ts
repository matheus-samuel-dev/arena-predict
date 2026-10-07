import { eventTeams } from "./format";
import type { ArenaEvent } from "../types";

export function isExternalEvent(event: { externalProvider?: unknown }) {
  return typeof event.externalProvider === "string"
    && Boolean(event.externalProvider.trim())
    && !["DEMO", "MOCK", "MANUAL"].includes(event.externalProvider.trim().toUpperCase());
}

export function eventSourceLabel(event: Pick<ArenaEvent, "externalProvider" | "demo" | "demoLiveData">) {
  if (isExternalEvent(event)) return ({ PANDASCORE:"PandaScore", API_FOOTBALL:"API-FOOTBALL", API_BASKETBALL:"API-BASKETBALL", API_TENNIS:"API-Tennis", API_FORMULA1:"API-FORMULA-1" } as Record<string,string>)[event.externalProvider!.trim().toUpperCase()] || "Dados reais";
  return event.demo || event.demoLiveData || ["DEMO", "MOCK"].includes(event.externalProvider?.trim().toUpperCase() || "") ? "Demo" : null;
}

export function eventFormatLabel(event: Pick<ArenaEvent, "bestOf" | "format" | "externalProvider">) {
  // Legacy multi-sport events default bestOf to 1 even when no series is played.
  if (!isExternalEvent(event) && event.format && !/^BO\d+$/i.test(event.format)) return event.format;
  if (event.bestOf != null && Number.isInteger(event.bestOf) && event.bestOf > 0) return `BO${event.bestOf}`;
  if (isExternalEvent(event)) return /^BO\d+$/i.test(event.format || "") ? event.format?.toUpperCase() : null;
  return event.format || null;
}

export function eventScore(event: ArenaEvent): readonly [number | string, number | string] | null {
  const [home, away] = eventTeams(event);
  const homeScore = event.homeScore ?? home.score;
  const awayScore = event.awayScore ?? away.score;
  if (["LIVE", "AO_VIVO"].includes(event.status) && isExternalEvent(event) && event.liveScoreAvailable === false) return null;
  if (homeScore == null || awayScore == null || homeScore === "" || awayScore === "") return null;
  return [homeScore, awayScore];
}
export function eventScoreScope(event: ArenaEvent) {
  const sport = typeof event.sport === "object" ? event.sport.code : event.sport || event.sportName;
  return isExternalEvent(event) && event.status === "FINISHED" && ["FOOTBALL","FUTEBOL"].includes(String(sport).toUpperCase()) ? "90 minutos" : null;
}
