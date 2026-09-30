import { Activity, AlertTriangle } from "lucide-react";
import { dateTime } from "../app/format";
import { useApiResource } from "../hooks/useApiResource";
import { useVisibleRefresh } from "../hooks/useVisibleRefresh";
import { adminApi } from "../services/api";

const labels = {
  DISABLED: "Sincronização desativada",
  UNCONFIGURED: "Token não configurado",
  ONLINE: "Online",
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
      {data?.status === "RATE_LIMITED" && data.nextAllowedRequestAt && <small>Próxima tentativa permitida: {dateTime(data.nextAllowedRequestAt)}</small>}
    </div>
    {Boolean(data?.reviewRequiredCount) && <span className="event-result-review"><AlertTriangle size={15} /> {data?.reviewRequiredCount} resultado(s) sob revisão</span>}
  </section>;
}
