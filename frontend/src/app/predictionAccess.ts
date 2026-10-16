import type { ArenaEvent } from "../types";
import { sessionStorage } from "../services/api";

/** UI mirrors server ownership rules; the backend remains authoritative. */
export function usesPrivateTraining(event: ArenaEvent) {
  const session = sessionStorage.read();
  const sport = typeof event.sport === "object" ? event.sport.code : event.sport;
  return session?.demoProfile === "PARTICIPANT" && !session.demoTraining && !event.demo
    && event.externalProvider === "PANDASCORE" && ["LIVE", "AO_VIVO"].includes(event.status)
    && ["CS2", "VALORANT", "LEAGUE_OF_LEGENDS"].includes(String(sport || event.sportName));
}

export function predictionReadOnly(event: ArenaEvent) {
  const session = sessionStorage.read();
  const profile = session?.demoProfile;
  if(session?.demoTraining) return event.demo || !event.externalProvider || !["LIVE", "AO_VIVO"].includes(event.status);
  if (profile === "ADMIN") return true;
  if (profile === "PARTICIPANT") return !usesPrivateTraining(event) && (!event.demo || Boolean(event.externalProvider || event.externalId || event.demoArchived));
  return Boolean(event.demoManaged);
}
