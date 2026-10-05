import { dateTime } from "../app/format";
import { eventSourceLabel } from "../app/sportsData";
import type { ArenaEvent } from "../types";

export function EventDataSource({ event, demoLabel = "Demo" }: { event: ArenaEvent; demoLabel?: string }) {
  const source = eventSourceLabel(event);
  if (!source && !event.resultProcessedAt && !event.resultReviewRequired) return null;
  return <div className="event-data-source">
    {source && <span className={`event-source${source === "Demo" ? " event-source--demo" : ""}`} title={event.lastSyncedAt ? `Última sincronização: ${dateTime(event.lastSyncedAt)}` : undefined}>{source === "Demo" ? demoLabel : source}</span>}
    {event.resultReviewRequired
      ? <span className="event-result-review" title="O provedor enviou uma correção. A pontuação anterior permanece até a revisão administrativa.">Resultado sob revisão</span>
      : event.resultProcessedAt && <span>Resultado processado</span>}
  </div>;
}
