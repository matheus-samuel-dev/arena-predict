import { Activity, AlertTriangle } from "lucide-react";
import { dateTime } from "../app/format";
import { useApiResource } from "../hooks/useApiResource";
import { useVisibleRefresh } from "../hooks/useVisibleRefresh";
import { adminApi } from "../services/api";
import { sportName } from "../app/format";

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
  const { data: response, loading, error, refresh } = useApiResource(async () => {
    const [status, providers] = await Promise.all([adminApi.sportsSyncStatus(),adminApi.sportsProviders().catch(() => [])]);
    return { status, providers: Array.isArray(providers) ? providers : [] };
  }, []);
  const data = response?.status;
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
      {Boolean(response?.providers.length) && <details><summary>Provedores por modalidade ({response?.providers.length})</summary>
        {response?.providers.map(provider => <div key={provider.id} className="sports-provider-status">
          <strong>{provider.sync.provider}</strong>
          <small>{provider.sync.supportedSports?.map(sportName).join(" · ")}</small>
          <small>{provider.readiness === "READY_FOR_CREDENTIAL" ? "Preparado · aguardando credencial" : labels[provider.sync.status]}</small>
          {!provider.sync.configured && <small>Configurar {provider.credentialVariable} no backend e reiniciar o serviço.</small>}
          <small>{provider.sync.enabled ? "Sincronização habilitada" : "Sincronização desativada"}</small>
          <small>{provider.sync.lastSuccessAt ? `Última sincronização: ${dateTime(provider.sync.lastSuccessAt)}` : "Nenhuma sincronização real confirmada"}</small>
          {provider.sync.lastAttemptAt && <small>Última tentativa: {dateTime(provider.sync.lastAttemptAt)}</small>}
          {provider.sync.nextSyncAt && <small>Próxima verificação: {dateTime(provider.sync.nextSyncAt)}</small>}
          {provider.sync.lastRunCompletedAt && <small>{provider.sync.insertedCount || 0} inseridos · {provider.sync.updatedCount || 0} atualizados · {provider.sync.skippedCount || 0} sem alterações ou adiados</small>}
        </div>)}
      </details>}
    </div>
    {Boolean(data?.reviewRequiredCount) && <span className="event-result-review"><AlertTriangle size={15} /> {data?.reviewRequiredCount} resultado(s) sob revisão</span>}
  </section>;
}
