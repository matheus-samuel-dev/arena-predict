import { dateTime } from "../app/format";
import { eventSourceLabel, isExternalEvent, isStaleLiveScore } from "../app/sportsData";
import type { ArenaEvent } from "../types";

export function EventDataSource({ event, demoLabel = "Demo" }: { event: ArenaEvent; demoLabel?: string }) {
  const source = eventSourceLabel(event);
  if (!source && !event.resultProcessedAt && !event.resultReviewRequired) return null;
  return <div className="event-data-source">
    {source && <span className={`event-source${source === "Demo" ? " event-source--demo" : ""}`} title={event.lastSyncedAt ? `Última sincronização: ${dateTime(event.lastSyncedAt)}` : undefined}>{source === "Demo" ? demoLabel : source}</span>}
    {isExternalEvent(event) && event.lastSyncedAt && <span title={`Último snapshot do provedor: ${dateTime(event.lastSyncedAt)}. A leitura desta página é independente.`}>{event.dataQuality === "DELAYED" ? "Dados possivelmente defasados" : "Snapshot sincronizado"}</span>}
    {event.status === "LIVE" && isExternalEvent(event) && event.liveScoreAvailable && event.lastSyncedAt && <span>{isStaleLiveScore(event) ? "Placar aguardando atualização" : "Placar da série consultado"} · {dateTime(event.lastSyncedAt)} · sujeito ao atraso do fornecedor</span>}
    {event.resultReviewRequired
      ? <span className="event-result-review" title="O provedor enviou uma correção. A pontuação anterior permanece até a revisão administrativa.">Resultado sob revisão</span>
      : event.resultProcessedAt && <span>Resultado processado</span>}
  </div>;
}
