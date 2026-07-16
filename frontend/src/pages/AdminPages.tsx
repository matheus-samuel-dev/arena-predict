import {
  Activity,
  AlertTriangle,
  BarChart3,
  BookOpen,
  CalendarClock,
  CheckCircle2,
  ClipboardCheck,
  Edit3,
  Filter,
  Layers3,
  Plus,
  Search,
  ShieldCheck,
  Trophy,
  Users,
  Zap,
} from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { brand } from "../app/branding";
import { dateTime, points } from "../app/format";
import { Button, EmptyState, ErrorState, Modal, PageHeader, PageSkeleton, StatusBadge } from "../components/UI";
import { useToast } from "../contexts/ToastContext";
import { useApiResource } from "../hooks/useApiResource";
import { adminApi, asList } from "../services/api";

const PAGE_SIZE = 25;

type AdminRecord = Record<string, unknown> & {
  id?: number | string;
  name?: string;
  title?: string;
  action?: string;
  status?: string;
  active?: boolean;
  createdAt?: string;
  updatedAt?: string;
};

type FieldConfig = {
  key: string;
  label: string;
  type?: "text" | "number" | "select" | "textarea" | "checkbox" | "datetime-local";
  required?: boolean;
  options?: string[];
  defaultValue?: unknown;
  numeric?: boolean;
};

type ResourceConfig = {
  title: string;
  singular: string;
  description: string;
  creatable?: boolean;
  editable?: boolean;
  fields?: FieldConfig[];
};

const resourceConfig: Record<string, ResourceConfig> = {
  sports: {
    title: "Modalidades",
    singular: "modalidade",
    description: "Catálogo genérico de esportes e eSports disponíveis.",
    creatable: true,
    editable: true,
    fields: [
      { key: "name", label: "Nome", required: true },
      { key: "code", label: "Código", required: true },
      { key: "category", label: "Categoria", type: "select", required: true, options: ["TRADITIONAL", "ESPORTS", "MOTORSPORT"], defaultValue: "TRADITIONAL" },
      { key: "icon", label: "Ícone" },
      { key: "displayOrder", label: "Ordem", type: "number", defaultValue: 0 },
      { key: "active", label: "Ativo", type: "checkbox", defaultValue: true },
    ],
  },
  championships: {
    title: "Campeonatos",
    singular: "campeonato",
    description: "Temporadas, competições e fases conectadas às modalidades.",
    creatable: true,
    editable: true,
    fields: [
      { key: "name", label: "Nome", required: true },
      { key: "slug", label: "Identificador", required: true },
      { key: "sportId", label: "ID da modalidade", type: "number", required: true },
      { key: "season", label: "Temporada", required: true },
      { key: "status", label: "Status", type: "select", options: ["DRAFT", "ACTIVE", "FINISHED", "ARCHIVED"], defaultValue: "ACTIVE" },
      { key: "imageUrl", label: "URL da imagem" },
      { key: "startsAt", label: "Início", type: "datetime-local" },
      { key: "endsAt", label: "Fim", type: "datetime-local" },
    ],
  },
  competitors: {
    title: "Equipes e participantes",
    singular: "competidor",
    description: "Equipes, duplas e atletas reutilizáveis em eventos.",
    creatable: true,
    editable: true,
    fields: [
      { key: "name", label: "Nome", required: true },
      { key: "code", label: "Código", required: true },
      { key: "sportId", label: "ID da modalidade", type: "number", required: true },
      { key: "country", label: "País" },
      { key: "imageUrl", label: "URL da imagem" },
      { key: "active", label: "Ativo", type: "checkbox", defaultValue: true },
    ],
  },
  events: {
    title: "Eventos",
    singular: "evento",
    description: "Agenda, status, placares e prazos de palpites.",
    creatable: true,
    editable: true,
    fields: [
      { key: "externalKey", label: "Chave externa", required: true },
      { key: "title", label: "Título", required: true },
      { key: "championshipId", label: "ID do campeonato", type: "number", required: true },
      { key: "homeCompetitorId", label: "ID mandante/competidor 1", type: "number" },
      { key: "awayCompetitorId", label: "ID visitante/competidor 2", type: "number" },
      { key: "stage", label: "Etapa ou fase" },
      { key: "venue", label: "Local" },
      { key: "broadcast", label: "Transmissão" },
      { key: "imageUrl", label: "URL da imagem" },
      { key: "startsAt", label: "Data e hora", type: "datetime-local", required: true },
      { key: "predictionClosesAt", label: "Limite do palpite", type: "datetime-local", required: true },
      { key: "status", label: "Status", type: "select", options: ["SCHEDULED", "OPEN_FOR_PREDICTIONS", "LIVE", "FINISHED", "CANCELLED", "POSTPONED"], defaultValue: "SCHEDULED" },
      { key: "format", label: "Formato", type: "select", options: ["STANDARD", "INDIVIDUAL", "RACE", "BO1", "BO3", "BO5"], defaultValue: "STANDARD" },
      { key: "bestOf", label: "Melhor de", type: "select", options: ["1", "3", "5"], defaultValue: 1, numeric: true },
      { key: "participantsJson", label: "Participantes/classificação (JSON)", type: "textarea", defaultValue: "[]" },
      { key: "featured", label: "Destaque", type: "checkbox", defaultValue: false },
      { key: "demo", label: "Dados demo", type: "checkbox", defaultValue: true },
    ],
  },
  markets: {
    title: "Mercados de previsão",
    singular: "mercado",
    description: "Opções e coeficientes simulados para pontos virtuais.",
    creatable: true,
    editable: true,
    fields: [
      { key: "eventId", label: "ID do evento", type: "number", required: true },
      { key: "name", label: "Nome", required: true },
      { key: "code", label: "Código", required: true },
      { key: "minimumPoints", label: "Mínimo de pontos", type: "number", required: true, defaultValue: 10 },
      { key: "status", label: "Status", type: "select", options: ["DRAFT", "OPEN", "SUSPENDED", "CLOSED", "CANCELLED"], defaultValue: "OPEN" },
      {
        key: "optionsJson",
        label: "Opções (JSON)",
        type: "textarea",
        required: true,
        defaultValue: JSON.stringify([
          { key: "HOME", label: "Competidor 1", multiplier: 1.8, active: true },
          { key: "AWAY", label: "Competidor 2", multiplier: 2.1, active: true },
        ], null, 2),
      },
    ],
  },
  users: { title: "Usuários", singular: "usuário", description: "Perfis, papéis, saldo virtual e status de acesso." },
  pools: { title: "Bolões e ligas", singular: "bolão", description: "Grupos, convites, regras e participantes." },
  "scoring-rules": { title: "Regras de pontuação", singular: "regra", description: "Critérios vigentes para pontos, bônus e desempates." },
  achievements: {
    title: "Conquistas", singular: "conquista", description: "Regras reais de progressão e recompensas virtuais.", creatable: true, editable: true,
    fields: [
      { key: "code", label: "Código", required: true }, { key: "name", label: "Nome", required: true },
      { key: "description", label: "Descrição", type: "textarea", required: true },
      { key: "rarity", label: "Raridade", type: "select", options: ["COMMON", "RARE", "EPIC", "LEGENDARY"], defaultValue: "COMMON", required: true },
      { key: "rule", label: "Regra", type: "select", options: ["FIRST_PREDICTION", "FIRST_WIN", "PREDICTION_COUNT", "WON_COUNT", "POOL_MEMBER"], defaultValue: "FIRST_PREDICTION", required: true },
      { key: "target", label: "Meta", type: "number", defaultValue: 1, required: true },
      { key: "pointsReward", label: "Recompensa em pontos", type: "number", defaultValue: 0, required: true },
      { key: "active", label: "Ativa", type: "checkbox", defaultValue: true },
    ],
  },
  challenges: {
    title: "Desafios", singular: "desafio", description: "Metas com janela temporal e recompensa em pontos virtuais.", creatable: true, editable: true,
    fields: [
      { key: "code", label: "Código", required: true }, { key: "name", label: "Nome", required: true },
      { key: "description", label: "Descrição", type: "textarea", required: true },
      { key: "metric", label: "Métrica", type: "select", options: ["PREDICTION_COUNT", "WON_COUNT", "SPORT_VARIETY", "POOL_MEMBER_COUNT"], defaultValue: "PREDICTION_COUNT", required: true },
      { key: "target", label: "Meta", type: "number", defaultValue: 1, required: true },
      { key: "rewardPoints", label: "Recompensa em pontos", type: "number", defaultValue: 0, required: true },
      { key: "startsAt", label: "Início", type: "datetime-local", required: true },
      { key: "expiresAt", label: "Encerramento", type: "datetime-local", required: true },
      { key: "active", label: "Ativo", type: "checkbox", defaultValue: true },
    ],
  },
  notifications: {
    title: "Notificações", singular: "notificação", description: "Avisos individuais ou gerais, com destino navegável.", creatable: true,
    fields: [
      { key: "userId", label: "ID do usuário (vazio envia a todos)", type: "number" },
      { key: "type", label: "Tipo", type: "select", options: ["ADMIN_NOTICE", "EVENT_STARTED", "EVENT_FINISHED", "RESULT_PROCESSED", "RANKING_CHANGED", "POOL_INVITE"], defaultValue: "ADMIN_NOTICE", required: true },
      { key: "title", label: "Título", required: true }, { key: "message", label: "Mensagem", type: "textarea", required: true },
      { key: "targetUrl", label: "Destino interno", defaultValue: "/notifications" },
    ],
  },
  moderation: { title: "Moderação", singular: "denúncia", description: "Fila de denúncias e decisões de conduta." },
  reports: { title: "Relatórios", singular: "indicador", description: "Indicadores operacionais consolidados da plataforma." },
  audit: { title: "Auditoria", singular: "registro", description: "Rastreabilidade de ações sensíveis e movimentações de pontos." },
  settings: { title: "Configurações", singular: "configuração", description: "Parâmetros públicos e estado do modo demonstração." },
  results: { title: "Resultados", singular: "evento", description: "Registro de placares oficiais. A liquidação de cada mercado é uma ação separada." },
};

export function AdminDashboardPage() {
  const { data, loading, error, reload } = useApiResource(async () => {
    const [dashboard, audit] = await Promise.all([
      adminApi.dashboard(),
      adminApi.list<AdminRecord>("audit", { page: 0, size: 6 }),
    ]);
    return { dashboard, audit: asList(audit) };
  }, []);

  if (loading) return <PageSkeleton cards={8} />;
  if (error || !data) {
    return <ErrorState message={error || "Painel administrativo indisponível."} onRetry={() => reload().catch(() => undefined)} />;
  }

  const dashboard = data.dashboard as typeof data.dashboard & { activePredictions?: number };
  const metrics = [
    { label: "Usuários cadastrados", value: points(dashboard.users), icon: Users, tone: "violet" },
    { label: "Eventos ativos", value: points(Number(dashboard.liveEvents || 0) + Number(dashboard.upcomingEvents || 0)), icon: Activity, tone: "green" },
    { label: "Palpites ativos", value: points(dashboard.activePredictions), icon: ClipboardCheck, tone: "blue" },
    { label: "Pontos movimentados (total)", value: `${points(dashboard.pointsMoved)} pts`, icon: Zap, tone: "orange" },
    { label: "Mercados abertos", value: points(dashboard.openMarkets), icon: Layers3, tone: "violet" },
    { label: "Bolões ativos", value: points(dashboard.activePools), icon: Trophy, tone: "blue" },
    { label: "Aguardando resultado", value: points(dashboard.eventsAwaitingResult), icon: CalendarClock, tone: "orange" },
  ];

  return (
    <>
      <PageHeader
        eyebrow={`OPERAÇÃO ${brand.name.toLocaleUpperCase("pt-BR")}`}
        title="Painel administrativo"
        description="Saúde da plataforma, agenda crítica e pontos que exigem decisão."
        actions={<Button variant="secondary" onClick={() => reload().catch(() => undefined)}>Atualizar painel</Button>}
      />
      <section className="metric-grid admin-metrics">
        {metrics.map(({ label, value, icon: Icon, tone }) => (
          <article className={`surface metric-card metric-card--${tone}`} key={label}>
            <span className="metric-card__icon"><Icon size={21} /></span>
            <div><small>{label}</small><strong>{value}</strong></div>
          </article>
        ))}
      </section>
      <section className="admin-dashboard-grid">
        <article className="surface operations-panel">
          <div className="section-header"><div><h2>Fila operacional</h2><p>Prioridades calculadas a partir do estado real da aplicação.</p></div></div>
          {[
            { label: "Eventos aguardando resultado", value: dashboard.eventsAwaitingResult, icon: CalendarClock, route: "/admin/results" },
            { label: "Mercados em operação", value: dashboard.openMarkets, icon: Layers3, route: "/admin/markets" },
          ].map(({ label, value, icon: Icon, route }) => (
            <Link to={route} key={label}>
              <span><Icon size={18} /></span>
              <div><strong>{label}</strong><small>Abrir área responsável</small></div>
              <b>{points(value)}</b>
            </Link>
          ))}
        </article>
        <article className="surface audit-preview">
          <div className="section-header"><div><h2>Atividade recente</h2><p>Últimas ações administrativas auditadas.</p></div></div>
          {data.audit.length ? data.audit.map((item) => (
            <div key={item.id}>
              <span><BookOpen size={16} /></span>
              <div><strong>{String(item.title || item.action || "Movimentação auditada")}</strong><small>{String(item.actor || "Sistema")} · {dateTime(String(item.createdAt || ""))}</small></div>
            </div>
          )) : <EmptyState icon={BookOpen} title="Sem ações recentes" description="Mudanças administrativas aparecerão nesta timeline." />}
        </article>
      </section>
      <div className="virtual-footer-note"><ShieldCheck size={15} /> Operações de pontos são auditáveis e não representam movimentação financeira.</div>
    </>
  );
}

export function AdminResourcePage() {
  const { resource: routeResource = "events" } = useParams();
  const resource = Object.prototype.hasOwnProperty.call(resourceConfig, routeResource) ? routeResource : "events";
  const config = resourceConfig[resource];
  const [search, setSearch] = useState("");
  const [serverSearch, setServerSearch] = useState("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<AdminRecord | "new" | null>(null);
  const [scoring, setScoring] = useState<AdminRecord | null>(null);
  const [settling, setSettling] = useState<AdminRecord | null>(null);
  const [moderating, setModerating] = useState<AdminRecord | null>(null);
  const { data, loading, error, reload } = useApiResource(
    async () => asList(await adminApi.list<AdminRecord>(resource, { page, size: PAGE_SIZE, search: serverSearch || undefined })),
    [resource, page, serverSearch],
  );

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setPage(0);
      setServerSearch(search.trim());
    }, 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    setSearch("");
    setServerSearch("");
    setPage(0);
    setEditing(null);
    setScoring(null);
    setSettling(null);
    setModerating(null);
  }, [resource]);

  const rows = useMemo(() => {
    const term = search.trim().toLocaleLowerCase("pt-BR");
    if (!term) return data || [];
    return (data || []).filter((row) => JSON.stringify(row).toLocaleLowerCase("pt-BR").includes(term));
  }, [data, search]);

  if (loading) return <PageSkeleton cards={4} />;
  if (error) {
    return (
      <>
        <PageHeader eyebrow="ADMINISTRAÇÃO" title={config.title} description={config.description} />
        <ErrorState message={error} onRetry={() => reload().catch(() => undefined)} />
      </>
    );
  }

  return (
    <>
      <PageHeader
        eyebrow="ADMINISTRAÇÃO"
        title={config.title}
        description={config.description}
        actions={config.creatable ? <Button onClick={() => setEditing("new")}><Plus size={17} /> Criar {config.singular}</Button> : undefined}
      />
      <section className="surface admin-list-panel">
        <div className="admin-list-toolbar">
          <label>
            <Search size={17} />
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={`Buscar ${config.title.toLocaleLowerCase("pt-BR")}`}
              aria-label={`Buscar em ${config.title}`}
            />
          </label>
          <Button variant="secondary" size="sm" onClick={() => reload().catch(() => undefined)}><Filter size={16} /> Atualizar</Button>
        </div>
        {rows.length ? (
          <div className="admin-table">
            <div className="admin-table__head"><span>Registro</span><span>Detalhes</span><span>Status</span><span>Atualizado</span><span>Ações</span></div>
            {rows.map((row, index) => (
              <div className="admin-table__row" key={String(row.id ?? index)}>
                <span data-label="Registro"><b>{recordLabel(row, config, index)}</b><small>ID {String(row.id ?? "—")}</small></span>
                <span data-label="Detalhes">{summarize(row)}</span>
                <span data-label="Status"><StatusBadge status={recordStatus(row)} label={recordStatusLabel(row)} /></span>
                <span data-label="Atualizado">{dateTime(String(row.updatedAt || row.createdAt || ""), true)}</span>
                <span data-label="Ações" className="admin-row-actions">
                  {resource === "results" && <Button size="sm" onClick={() => setScoring(row)}><ClipboardCheck size={15} /> Registrar placar</Button>}
                  {resource === "markets" && marketSettlementAvailability(row).allowed && <Button size="sm" variant="secondary" onClick={() => setSettling(row)}><CheckCircle2 size={15} /> Liquidar</Button>}
                  {resource === "markets" && !marketSettlementAvailability(row).allowed && <small title={marketSettlementAvailability(row).reason}>{marketSettlementAvailability(row).label}</small>}
                  {resource === "moderation" && <Button size="sm" variant="secondary" onClick={() => setModerating(row)}><ShieldCheck size={15} /> Revisar</Button>}
                  {config.editable && !(resource === "markets" && String(row.status).toUpperCase() === "SETTLED") && <button type="button" onClick={() => setEditing(row)} aria-label={`Editar ${recordLabel(row, config, index)}`}><Edit3 size={16} /></button>}
                  {!config.editable && resource !== "results" && resource !== "moderation" && <small>Somente leitura</small>}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <EmptyState
            icon={config.creatable ? Plus : BarChart3}
            title={search ? "Nenhum resultado encontrado" : `Nenhum ${config.singular} encontrado`}
            description={search ? "Ajuste a busca para consultar outros registros." : config.creatable ? "Crie o primeiro registro para começar." : "A fila está vazia no momento."}
            action={config.creatable && !search ? <Button onClick={() => setEditing("new")}><Plus size={16} /> Criar registro</Button> : undefined}
          />
        )}
        <footer className="pagination">
          <Button variant="quiet" size="sm" onClick={() => setPage((value) => Math.max(0, value - 1))} disabled={page === 0}>Anterior</Button>
          <span>Página {page + 1}</span>
          <Button variant="quiet" size="sm" onClick={() => setPage((value) => value + 1)} disabled={(data?.length || 0) < PAGE_SIZE}>Próxima</Button>
        </footer>
      </section>
      <ResourceForm resource={resource} config={config} record={editing} onClose={() => setEditing(null)} onSaved={() => reload().catch(() => undefined)} />
      <ScoreModal record={scoring} onClose={() => setScoring(null)} onSaved={() => reload().catch(() => undefined)} />
      <SettlementModal record={settling} onClose={() => setSettling(null)} onSettled={() => reload().catch(() => undefined)} />
      <ModerationModal record={moderating} onClose={() => setModerating(null)} onSaved={() => reload().catch(() => undefined)} />
    </>
  );
}

function recordLabel(row: AdminRecord, config: ResourceConfig, index: number) {
  return String(row.name || row.title || row.action || `${config.singular} #${row.id || index + 1}`);
}

function recordStatus(row: AdminRecord) {
  return String(row.status || (row.active === false ? "inactive" : "active"));
}

function recordStatusLabel(row: AdminRecord) {
  return String(row.status || (row.active === false ? "Inativo" : "Ativo"));
}

function summarize(row: AdminRecord) {
  if (typeof row.details === "string" && row.details.trim()) return row.details;
  const ignored = new Set(["id", "name", "title", "action", "status", "active", "createdAt", "updatedAt"]);
  const entries = Object.entries(row)
    .filter(([key, value]) => !ignored.has(key) && value !== "" && value != null && ["string", "number", "boolean"].includes(typeof value))
    .slice(0, 3);
  return entries.length ? entries.map(([key, value]) => `${humanize(key)}: ${String(value)}`).join(" · ") : "Sem detalhes adicionais";
}

function humanize(value: string) {
  return value.replace(/([A-Z])/g, " $1").replace(/^./, (char) => char.toUpperCase());
}

function marketOptions(record: AdminRecord | null) {
  if (!record || !Array.isArray(record.options)) return [];
  return record.options.filter((option): option is Record<string, unknown> => Boolean(option) && typeof option === "object");
}

export function marketSettlementAvailability(record: AdminRecord) {
  const marketStatus = String(record.status || "").toUpperCase();
  const nestedEvent = record.event && typeof record.event === "object" ? record.event as Record<string, unknown> : null;
  const eventStatus = String(record.eventStatus || nestedEvent?.status || "").toUpperCase();
  const hasOptions = marketOptions(record).some((option) => option.active !== false && String(option.key || "").trim());
  if (marketStatus === "SETTLED") return { allowed: false, label: "Finalizado", reason: "Este mercado já foi liquidado." };
  if (marketStatus === "CANCELLED") return { allowed: false, label: "Cancelado", reason: "Mercados cancelados não podem ser liquidados." };
  if (marketStatus !== "CLOSED") return { allowed: false, label: "Aguardando fechamento", reason: "Feche o mercado antes de liquidá-lo." };
  if (eventStatus !== "FINISHED") return { allowed: false, label: "Evento em andamento", reason: "O evento precisa estar encerrado antes da liquidação." };
  if (!hasOptions) return { allowed: false, label: "Sem opções", reason: "Configure ao menos uma opção ativa." };
  return { allowed: true, label: "Disponível", reason: "Mercado pronto para liquidação." };
}

function initialForm(resource: string, config: ResourceConfig, record: AdminRecord | "new" | null) {
  const source = record && record !== "new" ? record : null;
  return Object.fromEntries((config.fields || []).map((field) => {
    let value = source?.[field.key];
    if (source && field.key === "homeCompetitorId") value = nestedId(source.homeCompetitor);
    if (source && field.key === "awayCompetitorId") value = nestedId(source.awayCompetitor);
    if (source && field.key === "optionsJson") value = JSON.stringify(source.options || [], null, 2);
    if (source && field.key === "participantsJson") value = JSON.stringify((source.participants as Array<Record<string, unknown>> || []).map((item) => ({
      competitorId: item.competitorId || nestedId(item.competitor),
      displayOrder: item.displayOrder,
      position: item.position,
      scoreLabel: item.scoreLabel,
    })), null, 2);
    if (field.type === "datetime-local" && value) value = toDateTimeLocal(value);
    if (value === undefined || value === null) value = field.defaultValue ?? (field.type === "checkbox" ? false : "");
    if (resource === "events" && field.key === "externalKey" && record === "new" && !value) value = `arena-${Date.now()}`;
    return [field.key, value];
  }));
}

function nestedId(value: unknown) {
  return value && typeof value === "object" && "id" in value ? (value as { id?: unknown }).id : "";
}

function toDateTimeLocal(value: unknown) {
  const date = new Date(String(value));
  if (Number.isNaN(date.getTime())) return "";
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function ResourceForm({ resource, config, record, onClose, onSaved }: {
  resource: string;
  config: ResourceConfig;
  record: AdminRecord | "new" | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<Record<string, unknown>>({});
  const [saving, setSaving] = useState(false);
  const [confirmingCancellation, setConfirmingCancellation] = useState(false);
  const { notify } = useToast();

  useEffect(() => {
    setForm(initialForm(resource, config, record));
    setConfirmingCancellation(false);
  }, [resource, config, record]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const missing = config.fields?.find((field) => field.required && isMissing(form[field.key]));
    if (missing) {
      notify(`Preencha “${missing.label}”.`, "error");
      return;
    }

    setSaving(true);
    try {
      const payload = Object.fromEntries((config.fields || [])
        .map((field) => [field.key, form[field.key]])
        .filter(([, value]) => value !== "" && value !== undefined));

      if (typeof payload.optionsJson === "string") {
        const parsed = JSON.parse(payload.optionsJson);
        if (!Array.isArray(parsed) || parsed.length === 0) throw new Error("Informe ao menos uma opção no JSON do mercado.");
        payload.options = parsed;
        delete payload.optionsJson;
      }

      const cancellingEvent = resource === "events" && record !== "new" && record?.id != null
        && String(payload.status).toUpperCase() === "CANCELLED"
        && String(record.status).toUpperCase() !== "CANCELLED";
      if (cancellingEvent && !confirmingCancellation) {
        setConfirmingCancellation(true);
        setSaving(false);
        return;
      }

      if (cancellingEvent && record?.id != null) {
        const result = await adminApi.cancelEvent(record.id);
        notify(`Evento cancelado; ${result.refundedPredictions} palpite(s) reembolsado(s).`, "success");
      } else if (record === "new") {
        await adminApi.create(resource, payload);
        notify(`${capitalize(config.singular)} criado com sucesso.`, "success");
      } else if (record?.id != null) {
        await adminApi.update(resource, record.id, payload);
        notify(`${capitalize(config.singular)} atualizado com sucesso.`, "success");
      }
      onClose();
      onSaved();
    } catch (error) {
      notify(error instanceof SyntaxError ? "O JSON de opções é inválido." : error instanceof Error ? error.message : "Não foi possível salvar.", "error");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal open={Boolean(record)} onClose={onClose} title={`${record === "new" ? "Criar" : "Editar"} ${config.singular}`}>
      <form className="stack-form" onSubmit={submit}>
        {config.fields?.map((field) => (
          <label className={field.type === "checkbox" ? "toggle-row" : ""} key={field.key}>
            <span>{field.label}{field.required && " *"}</span>
            {field.type === "select" ? (
              <select value={String(form[field.key] ?? "")} onChange={(event) => { const next = field.numeric ? Number(event.target.value) : event.target.value; setForm((value) => ({ ...value, [field.key]: next })); if (resource === "events" && field.key === "status" && next !== "CANCELLED") setConfirmingCancellation(false); }}>
                {field.options?.map((option) => <option key={option} value={option}>{option}</option>)}
              </select>
            ) : field.type === "textarea" ? (
              <textarea value={String(form[field.key] ?? "")} onChange={(event) => setForm((value) => ({ ...value, [field.key]: event.target.value }))} />
            ) : field.type === "checkbox" ? (
              <input type="checkbox" checked={Boolean(form[field.key])} onChange={(event) => setForm((value) => ({ ...value, [field.key]: event.target.checked }))} />
            ) : (
              <input
                type={field.type || "text"}
                value={String(form[field.key] ?? "")}
                onChange={(event) => setForm((value) => ({ ...value, [field.key]: field.type === "number" && event.target.value !== "" ? Number(event.target.value) : event.target.value }))}
              />
            )}
          </label>
        ))}
        {confirmingCancellation && <div className="virtual-disclaimer"><AlertTriangle size={16} /> Confirme novamente: o evento será cancelado, mercados ativos serão encerrados e todos os palpites ativos serão reembolsados em pontos.</div>}
        <div className="modal-actions"><Button type="button" variant="secondary" onClick={onClose}>Voltar</Button><Button type="submit" loading={saving}>{confirmingCancellation ? "Confirmar cancelamento" : "Salvar"}</Button></div>
      </form>
    </Modal>
  );
}

function isMissing(value: unknown) {
  return value === undefined || value === null || value === "" || (typeof value === "string" && !value.trim());
}

function capitalize(value: string) {
  return value.charAt(0).toLocaleUpperCase("pt-BR") + value.slice(1);
}

function ScoreModal({ record, onClose, onSaved }: {
  record: AdminRecord | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [homeScore, setHomeScore] = useState(0);
  const [awayScore, setAwayScore] = useState(0);
  const [finishEvent, setFinishEvent] = useState(true);
  const [saving, setSaving] = useState(false);
  const { notify } = useToast();

  useEffect(() => {
    setHomeScore(Number(record?.homeScore ?? 0));
    setAwayScore(Number(record?.awayScore ?? 0));
    setFinishEvent(true);
  }, [record]);

  async function saveScore(event: FormEvent) {
    event.preventDefault();
    if (record?.id == null) return;
    setSaving(true);
    try {
      await adminApi.recordEventResult(record.id, { homeScore, awayScore, finishEvent });
      notify("Placar registrado. Os mercados não foram liquidados automaticamente.", "success");
      onClose();
      onSaved();
    } catch (error) {
      notify(error instanceof Error ? error.message : "Não foi possível registrar o placar.", "error");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal open={Boolean(record)} onClose={onClose} title="Registrar placar oficial" size="sm">
      <form className="result-form" onSubmit={saveScore}>
        <div>
          <span><input aria-label="Placar do competidor 1" type="number" min="0" value={homeScore} onChange={(event) => setHomeScore(Number(event.target.value))} /></span>
          <b>×</b>
          <span><input aria-label="Placar do competidor 2" type="number" min="0" value={awayScore} onChange={(event) => setAwayScore(Number(event.target.value))} /></span>
        </div>
        <label className="toggle-row"><span>Marcar evento como encerrado</span><input type="checkbox" checked={finishEvent} onChange={(event) => setFinishEvent(event.target.checked)} /></label>
        <p><AlertTriangle size={16} /> Salvar o placar atualiza somente o evento. Para processar palpites, recompensas e notificações, liquide cada mercado na área “Mercados”.</p>
        <div className="modal-actions"><Button type="button" variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" loading={saving}>Salvar placar</Button></div>
      </form>
    </Modal>
  );
}

function SettlementModal({ record, onClose, onSettled }: {
  record: AdminRecord | null;
  onClose: () => void;
  onSettled: () => void;
}) {
  const options = marketOptions(record).filter((option) => option.active !== false);
  const [correctOptionKey, setCorrectOptionKey] = useState("");
  const [saving, setSaving] = useState(false);
  const { notify } = useToast();
  const availability = record ? marketSettlementAvailability(record) : { allowed: false, label: "Indisponível", reason: "Selecione um mercado." };

  useEffect(() => {
    const first = marketOptions(record).find((option) => option.active !== false && String(option.key || "").trim());
    setCorrectOptionKey(first ? String(first.key) : "");
  }, [record]);

  async function settle(event: FormEvent) {
    event.preventDefault();
    if (record?.id == null || !correctOptionKey || !availability.allowed) return;
    setSaving(true);
    try {
      const result = await adminApi.settleMarket(record.id, correctOptionKey);
      const summary = result.alreadySettled
        ? "Este mercado já estava liquidado; nenhum ponto foi processado novamente."
        : `Mercado liquidado: ${points(result.winners)} vencedor(es), ${points(result.losers)} perdedor(es) e ${points(result.rewardedPoints)} pontos creditados.`;
      notify(summary, result.alreadySettled ? "info" : "success");
      onClose();
      onSettled();
    } catch (error) {
      notify(error instanceof Error ? error.message : "Não foi possível liquidar o mercado.", "error");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal open={Boolean(record)} onClose={onClose} title="Liquidar mercado" size="sm">
      <form className="stack-form" onSubmit={settle}>
        <div><h2>{String(record?.name || "Mercado")}</h2><p>Selecione a opção oficial correta. Apenas mercados fechados de eventos encerrados podem ser processados.</p></div>
        {options.length ? (
          <label>
            <span>Opção correta *</span>
            <select value={correctOptionKey} onChange={(event) => setCorrectOptionKey(event.target.value)} required>
              {options.map((option) => (
                <option value={String(option.key)} key={String(option.id || option.key)}>{String(option.label || option.key)} ({String(option.key)})</option>
              ))}
            </select>
          </label>
        ) : <EmptyState icon={AlertTriangle} title="Mercado sem opções ativas" description="Edite o mercado e configure ao menos uma opção antes da liquidação." />}
        <div className="virtual-footer-note"><ShieldCheck size={15} /> A liquidação movimenta somente pontos virtuais, sem qualquer valor financeiro.</div>
        {!availability.allowed && <p className="field-error" role="alert">{availability.reason}</p>}
        <div className="modal-actions"><Button type="button" variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" loading={saving} disabled={!correctOptionKey || !availability.allowed}>Confirmar liquidação</Button></div>
      </form>
    </Modal>
  );
}

function ModerationModal({ record, onClose, onSaved }: {
  record: AdminRecord | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [status, setStatus] = useState<"REVIEWED" | "DISMISSED">("REVIEWED");
  const [moderatorNote, setModeratorNote] = useState("");
  const [hidePost, setHidePost] = useState(false);
  const [saving, setSaving] = useState(false);
  const { notify } = useToast();

  useEffect(() => {
    setStatus("REVIEWED");
    setModeratorNote("");
    setHidePost(false);
  }, [record]);

  async function moderate(event: FormEvent) {
    event.preventDefault();
    if (record?.id == null) return;
    setSaving(true);
    try {
      await adminApi.moderateReport(record.id, { status, moderatorNote: moderatorNote.trim() || undefined });
      if (hidePost && record.postId != null) await adminApi.hideCommunityPost(String(record.postId));
      notify(hidePost ? "Denúncia revisada e publicação ocultada." : status === "DISMISSED" ? "Denúncia descartada." : "Denúncia marcada como revisada.", "success");
      onClose();
      onSaved();
    } catch (error) {
      notify(error instanceof Error ? error.message : "Não foi possível concluir a moderação.", "error");
      onSaved();
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal open={Boolean(record)} onClose={onClose} title="Revisar denúncia" size="sm">
      <form className="stack-form" onSubmit={moderate}>
        <div><h2>{String(record?.title || "Conteúdo denunciado")}</h2><p>{String(record?.postExcerpt || record?.reason || "Analise o conteúdo e registre uma decisão.")}</p></div>
        <label>
          <span>Decisão</span>
          <select value={status} onChange={(event) => setStatus(event.target.value as "REVIEWED" | "DISMISSED")}>
            <option value="REVIEWED">Revisada</option>
            <option value="DISMISSED">Descartada</option>
          </select>
        </label>
        <label><span>Nota da moderação</span><textarea maxLength={500} value={moderatorNote} onChange={(event) => setModeratorNote(event.target.value)} placeholder="Registre o motivo da decisão." /></label>
        {record?.postId != null && (
          <label className="toggle-row">
            <span><AlertTriangle size={17} /><span><strong>Ocultar publicação</strong><small>Remove o conteúdo do feed após a confirmação.</small></span></span>
            <input type="checkbox" checked={hidePost} onChange={(event) => { setHidePost(event.target.checked); if (event.target.checked) setStatus("REVIEWED"); }} />
          </label>
        )}
        <div className="virtual-disclaimer"><ShieldCheck size={16} /> A decisão fica registrada na auditoria administrativa.</div>
        <div className="modal-actions"><Button type="button" variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" loading={saving}>Confirmar decisão</Button></div>
      </form>
    </Modal>
  );
}
