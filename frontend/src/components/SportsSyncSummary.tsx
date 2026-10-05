import { Activity, AlertTriangle } from "lucide-react";
import { dateTime } from "../app/format";
import { useApiResource } from "../hooks/useApiResource";
import { useVisibleRefresh } from "../hooks/useVisibleRefresh";
import { adminApi } from "../services/api";

const labels = {
  DISABLED: "Sincronização desativada",
  UNCONFIGURED: "Token não configurado",
  CONFIGURED: "Configurado; aguardando primeira sincronização",
  SYNCING: "Sincronização em andamento",
  ONLINE: "Online",
  DEGRADED: "Sem sincronização recente confirmada",
  UNAVAILABLE: "Provedor temporariamente indisponível",
  RATE_LIMITED: "Aguardando quota do provedor",
};

export function SportsSyncSummary() {
  const { data, loading, error, refresh } = useApiResource(() => adminApi.sportsSyncStatus(), []);
  useVisibleRefresh(refresh);
  return <section className="surface sports-sync-summary" aria-label="Dados esportivos">
    <Activity size={18} aria-hidden="true" />
    <div><strong>Dados esportivos{data?.provider && ` · ${data.provider.toUpperCase() === "PANDASCORE" ? "PandaScore" : data.provider}`}</strong>
      <span>{loading ? "Consultando integração…" : error ? "Status da integração indisponível" : data ? labels[data.status] || data.message : "Aguardando integração"}</span>
      {data?.lastSuccessAt && <small>Última sincronização: {dateTime(data.lastSuccessAt)}</small>}
      {data && !data.lastSuccessAt && <small>Nenhuma sincronização concluída</small>}
      {data?.lastAttemptAt && <small>Última tentativa: {dateTime(data.lastAttemptAt)}</small>}
      {data?.lastSchedulerTickAt && <small>Scheduler ativo · última verificação: {dateTime(data.lastSchedulerTickAt)}</small>}
      {data?.nextSyncAt && <small>Próxima sincronização prevista: {dateTime(data.nextSyncAt)}</small>}
      {data?.lastRunCompletedAt && <small>Última execução: {data.receivedCount || 0} recebido(s), {data.insertedCount || 0} inserido(s), {data.updatedCount || 0} atualizado(s), {data.skippedCount || 0} ignorado(s), {data.failedCount || 0} falha(s){data.durationMs != null && ` · ${data.durationMs} ms`}</small>}
      {data?.lastHttpStatus != null && <small>Último HTTP observado do provedor: {data.lastHttpStatus}</small>}
      {data?.lastErrorReason && <small>{data.message || "Falha na última sincronização."}</small>}
      {data?.status === "RATE_LIMITED" && data.nextAllowedRequestAt && <small>Próxima tentativa permitida: {dateTime(data.nextAllowedRequestAt)}</small>}
    </div>
    {Boolean(data?.reviewRequiredCount) && <span className="event-result-review"><AlertTriangle size={15} /> {data?.reviewRequiredCount} resultado(s) sob revisão</span>}
  </section>;
}
