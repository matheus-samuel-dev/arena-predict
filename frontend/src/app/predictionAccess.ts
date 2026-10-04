import type { ArenaEvent } from "../types";
import { sessionStorage } from "../services/api";

/** UI mirrors server ownership rules; the backend remains authoritative. */
export function predictionReadOnly(event: ArenaEvent) {
  const profile = sessionStorage.read()?.demoProfile;
  if (profile === "ADMIN") return true;
  if (profile === "PARTICIPANT") return !event.demo || Boolean(event.externalProvider || event.externalId || event.demoArchived);
  return Boolean(event.demoManaged);
}
