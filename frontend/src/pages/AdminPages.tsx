import {
  Activity,
  AlertTriangle,
  Award,
  BarChart3,
  Bell,
  BookOpen,
  CalendarDays,
  CalendarClock,
  CheckCircle2,
  CircleUserRound,
  ClipboardCheck,
  Coins,
  Edit3,
  Filter,
  Gamepad2,
  Gauge,
  Layers3,
  Plus,
  Settings2,
  Search,
  ShieldCheck,
  Target,
  Trophy,
  Users,
  X,
  Zap,
  type LucideIcon,
} from "lucide-react";
import { FormEvent, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Link, useParams } from "react-router-dom";
import { brand } from "../app/branding";
import { dateTime, multiplier, points } from "../app/format";
import { auditSummaryLabel, enumLabel, presentationCode } from "../app/presentation";
import { Button, EmptyState, ErrorState, Modal, PageHeader, PageSkeleton, StatusBadge, UserAvatar } from "../components/UI";
import { TeamLogo } from "../components/TeamLogo";
import { useToast } from "../contexts/ToastContext";
import { useApiResource } from "../hooks/useApiResource";
import { adminApi, ApiError, asList, createIdempotencyKey } from "../services/api";
import type { MarketTemplate, PageResponse, ResultField } from "../types";
import "../markets.css";

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
  reference?: "sports" | "championships" | "competitors" | "events" | "users";
};

type ResourceConfig = {
  title: string;
  singular: string;
  description: string;
  creatable?: boolean;
  editable?: boolean;
  fields?: FieldConfig[];
  icon?: LucideIcon;
};

type AdminPagination = {
  number: number;
  totalPages: number;
  totalElements: number;
  first: boolean;
  last: boolean;
} | null;

type AdminLookups = Partial<Record<NonNullable<FieldConfig["reference"]>, AdminRecord[]>>;

type AdminCollection = {
  rows: AdminRecord[];
  pagination: AdminPagination;
  lookups: AdminLookups;
};

const resourceConfig: Record<string, ResourceConfig> = {
  sports: {
    title: "Modalidades",
    singular: "modalidade",
    description: "Organize esportes e eSports que estruturam campeonatos, equipes e eventos.",
    icon: Gamepad2,
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
    icon: Trophy,
    creatable: true,
    editable: true,
    fields: [
      { key: "name", label: "Nome", required: true },
      { key: "slug", label: "Identificador", required: true },
      { key: "sportId", label: "Modalidade", type: "number", required: true, reference: "sports" },
      { key: "season", label: "Temporada", required: true },
      { key: "status", label: "Status", type: "select", options: ["DRAFT", "ACTIVE", "FINISHED", "ARCHIVED"], defaultValue: "ACTIVE" },
      { key: "imageUrl", label: "URL da imagem" },
      { key: "startsAt", label: "Início", type: "datetime-local" },
      { key: "endsAt", label: "Fim", type: "datetime-local" },
    ],
  },
  competitors: {
    title: "Equipes e participantes",
    singular: "participante",
    description: "Equipes, duplas e atletas reutilizáveis em eventos.",
    icon: Users,
    creatable: true,
    editable: true,
    fields: [
      { key: "name", label: "Nome", required: true },
      { key: "code", label: "Código", required: true },
      { key: "sportId", label: "Modalidade", type: "number", required: true, reference: "sports" },
      { key: "country", label: "País" },
      { key: "imageUrl", label: "URL da imagem" },
      { key: "active", label: "Ativo", type: "checkbox", defaultValue: true },
    ],
  },
  events: {
    title: "Eventos",
    singular: "evento",
    description: "Agenda, status, placares e prazos de palpites.",
    icon: CalendarDays,
    creatable: true,
    editable: true,
    fields: [
      { key: "externalKey", label: "Chave externa", required: true },
      { key: "title", label: "Título", required: true },
      { key: "championshipId", label: "Campeonato", type: "number", required: true, reference: "championships" },
      { key: "homeCompetitorId", label: "Mandante ou participante 1", type: "number", reference: "competitors" },
      { key: "awayCompetitorId", label: "Visitante ou participante 2", type: "number", reference: "competitors" },
      { key: "stage", label: "Etapa ou fase" },
      { key: "venue", label: "Local" },
      { key: "broadcast", label: "Transmissão" },
      { key: "imageUrl", label: "URL da imagem" },
      { key: "startsAt", label: "Data e hora", type: "datetime-local", required: true },
      { key: "predictionClosesAt", label: "Limite do palpite", type: "datetime-local", required: true },
      { key: "status", label: "Status", type: "select", options: ["SCHEDULED", "LIVE", "FINISHED", "CANCELLED", "POSTPONED"], defaultValue: "SCHEDULED" },
      { key: "format", label: "Formato", type: "select", options: ["STANDARD", "INDIVIDUAL", "RACE", "BO1", "BO3", "BO5"], defaultValue: "STANDARD" },
      { key: "bestOf", label: "Melhor de", type: "select", options: ["1", "3", "5"], defaultValue: 1, numeric: true },
      { key: "participantsJson", label: "Classificação e participantes", type: "textarea", defaultValue: "[]" },
      { key: "featured", label: "Destaque", type: "checkbox", defaultValue: false },
      { key: "demo", label: "Dados de demonstração", type: "checkbox", defaultValue: true },
    ],
  },
  markets: {
    title: "Mercados de previsão",
    singular: "mercado",
    description: "Mercados por modalidade, disponibilidade pré-jogo e ao vivo, regras de liquidação e multiplicadores demonstrativos.",
    icon: Layers3,
    creatable: true,
    editable: true,
    fields: [
      { key: "eventId", label: "Evento", type: "number", required: true, reference: "events" },
      { key: "name", label: "Nome", required: true },
      { key: "code", label: "Código", required: true },
      { key: "minimumPoints", label: "Mínimo de pontos", type: "number", required: true, defaultValue: 10 },
      { key: "status", label: "Status", type: "select", options: ["DRAFT", "OPEN", "SUSPENDED", "CLOSED", "CANCELLED"], defaultValue: "OPEN" },
      { key: "timingMode", label: "Disponibilidade durante o evento", type: "select", options: ["PRE_MATCH_ONLY", "LIVE_ENABLED", "LIVE_ONLY"], defaultValue: "PRE_MATCH_ONLY" },
      { key: "opensAt", label: "Abertura do mercado", type: "datetime-local" },
      { key: "closesAt", label: "Fechamento do mercado", type: "datetime-local" },
      {
        key: "optionsJson",
        label: "Opções do mercado",
        type: "textarea",
        required: true,
        defaultValue: JSON.stringify([
          { key: "HOME", label: "Participante 1", multiplier: 1.8, active: true },
          { key: "AWAY", label: "Participante 2", multiplier: 2.1, active: true },
        ], null, 2),
      },
    ],
  },
  users: { title: "Usuários", singular: "usuário", description: "Perfis, papéis, saldo virtual e status de acesso.", icon: CircleUserRound },
  pools: { title: "Bolões e ligas", singular: "bolão", description: "Grupos, participantes, períodos e regras exclusivamente virtuais.", icon: Trophy },
  "scoring-rules": { title: "Regras de pontuação", singular: "regra", description: "Critérios vigentes apresentados por explicação, fórmula e unidade.", icon: Gauge },
  achievements: {
    title: "Conquistas", singular: "conquista", description: "Marcos de progressão, raridade e recompensas exclusivamente virtuais.", icon: Award, creatable: true, editable: true,
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
    title: "Desafios", singular: "desafio", description: "Metas mensuráveis com janela temporal e recompensa em pontos virtuais.", icon: Target, creatable: true, editable: true,
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
    title: "Notificações", singular: "notificação", description: "Avisos individuais ou gerais, com destino interno navegável.", icon: Bell, creatable: true,
    fields: [
      { key: "userId", label: "Destinatário (vazio envia a todos)", type: "number", reference: "users" },
      { key: "type", label: "Tipo", type: "select", options: ["ADMIN_NOTICE", "EVENT_STARTED", "EVENT_FINISHED", "RESULT_PROCESSED", "RANKING_CHANGED", "POOL_INVITE"], defaultValue: "ADMIN_NOTICE", required: true },
      { key: "title", label: "Título", required: true }, { key: "message", label: "Mensagem", type: "textarea", required: true },
      { key: "targetUrl", label: "Destino interno", defaultValue: "/notifications" },
    ],
  },
  moderation: { title: "Moderação", singular: "denúncia", description: "Fila operacional de denúncias, contexto e decisões de conduta.", icon: ShieldCheck },
  reports: { title: "Relatórios", singular: "indicador", description: "Indicadores operacionais consolidados da plataforma.", icon: BarChart3 },
  audit: { title: "Auditoria", singular: "registro", description: "Histórico imutável das ações administrativas críticas.", icon: BookOpen },
  settings: { title: "Configurações", singular: "configuração", description: "Parâmetros públicos, segurança e estado do ambiente demonstrativo.", icon: Settings2 },
  results: { title: "Resultados", singular: "evento", description: "Registre os dados da modalidade e liquide seus mercados com regras determinísticas, sem repetir créditos de pontos.", icon: ClipboardCheck },
};

const resourceLookups: Record<string, NonNullable<FieldConfig["reference"]>[]> = {
  championships: ["sports"],
  competitors: ["sports"],
  events: ["championships", "competitors"],
  markets: ["events"],
  notifications: ["users"],
};

function collectionOf(value: AdminRecord[] | PageResponse<AdminRecord>): Omit<AdminCollection, "lookups"> {
  if (Array.isArray(value)) return { rows: value, pagination: null };
  return {
    rows: asList(value),
    pagination: {
      number: Number(value.number ?? 0),
      totalPages: Number(value.totalPages ?? 1),
      totalElements: Number(value.totalElements ?? value.content.length),
      first: Number(value.number ?? 0) === 0,
      last: Number(value.number ?? 0) + 1 >= Number(value.totalPages ?? 1),
    },
  };
}

async function loadLookups(resource: string): Promise<AdminLookups> {
  const references = resourceLookups[resource] || [];
  const entries = await Promise.all(references.map(async (reference) => {
    const response = await adminApi.list<AdminRecord>(reference, { page: 0, size: 100 });
    return [reference, asList(response)] as const;
  }));
  return Object.fromEntries(entries) as AdminLookups;
}

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
              <div><strong>{auditTitle(item)}</strong><small>{String(item.actor || "Sistema")} · {dateTime(String(item.createdAt || ""))}</small></div>
            </div>
          )) : <EmptyState icon={BookOpen} title="Sem ações recentes" description="Mudanças administrativas aparecerão neste histórico." />}
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
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<AdminRecord | "new" | null>(null);
  const [scoring, setScoring] = useState<AdminRecord | null>(null);
  const [settling, setSettling] = useState<AdminRecord | null>(null);
  const [moderating, setModerating] = useState<AdminRecord | null>(null);
  const [generatingMarkets, setGeneratingMarkets] = useState(false);
  const { data, loading, error, reload } = useApiResource<AdminCollection>(
    async () => {
      const [response, lookups] = await Promise.all([
        adminApi.list<AdminRecord>(resource, { page, size: PAGE_SIZE, search: serverSearch || undefined }),
        loadLookups(resource),
      ]);
      return { ...collectionOf(response), lookups };
    },
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
    setStatusFilter("");
    setPage(0);
    setEditing(null);
    setScoring(null);
    setSettling(null);
    setModerating(null);
    setGeneratingMarkets(false);
  }, [resource]);

  const rows = useMemo(() => {
    const term = search.trim().toLocaleLowerCase("pt-BR");
    return (data?.rows || []).filter((row) => {
      if (statusFilter && recordStatus(row).toUpperCase() !== statusFilter) return false;
      return !term || JSON.stringify(row).toLocaleLowerCase("pt-BR").includes(term);
    });
  }, [data?.rows, search, statusFilter]);

  const availableStatuses = useMemo(() => Array.from(new Set((data?.rows || [])
    .map((row) => recordStatus(row).toUpperCase())
    .filter(Boolean))), [data?.rows]);

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
        actions={config.creatable ? <div className="admin-market-actions">{resource === "markets" && <Button onClick={() => setGeneratingMarkets(true)}><Layers3 size={17} /> Catálogo da modalidade</Button>}<Button variant={resource === "markets" ? "secondary" : "primary"} onClick={() => setEditing("new")}><Plus size={17} /> Criar {config.singular}</Button></div> : undefined}
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
          {availableStatuses.length > 1 && (
            <label className="admin-status-filter">
              <Filter size={16} />
              <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)} aria-label={`Filtrar status em ${config.title}`}>
                <option value="">Todos os status</option>
                {availableStatuses.map((value) => <option value={value} key={value}>{enumLabel(value)}</option>)}
              </select>
            </label>
          )}
          <Button variant="secondary" size="sm" onClick={() => reload().catch(() => undefined)}>Atualizar dados</Button>
        </div>
        {rows.length ? (
          <div className={`admin-table admin-table--${resource}`} role="table" aria-label={config.title}>
            <div className="admin-table__head" role="row">{adminHeaders(resource).map((header) => <span role="columnheader" key={header}>{header}</span>)}</div>
            {rows.map((row, index) => (
              <div className="admin-table__row" role="row" key={String(row.id ?? index)}>
                {adminCells(resource, row, config, index).map((cell, cellIndex) => (
                  <span role="cell" data-label={adminHeaders(resource)[cellIndex]} key={cellIndex}>{cell}</span>
                ))}
                <span role="cell" data-label="Ações" className="admin-row-actions">
                  {resource === "results" && resultRegistrationAvailability(row).allowed && <Button size="sm" onClick={() => setScoring(row)}><ClipboardCheck size={15} /> {resultActionLabel(row)}</Button>}
                  {resource === "results" && !resultRegistrationAvailability(row).allowed && <small className="availability-note" tabIndex={0} title={resultRegistrationAvailability(row).reason} aria-label={`${resultRegistrationAvailability(row).label}. ${resultRegistrationAvailability(row).reason}`}>{resultRegistrationAvailability(row).label}</small>}
                  {resource === "markets" && Boolean(row.templateCode) && !["SETTLED", "CANCELLED"].includes(String(row.status)) && <Link className="admin-result-link" to="/admin/results">Registrar resultado</Link>}
                  {resource === "markets" && !row.templateCode && marketSettlementAvailability(row).allowed && <Button size="sm" variant="secondary" onClick={() => setSettling(row)}><CheckCircle2 size={15} /> Liquidar</Button>}
                  {resource === "markets" && !marketSettlementAvailability(row).allowed && <small className="availability-note" tabIndex={0} title={marketSettlementAvailability(row).reason} aria-label={`${marketSettlementAvailability(row).label}. ${marketSettlementAvailability(row).reason}`}>{marketSettlementAvailability(row).label}</small>}
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
            title={search ? "Nenhum resultado encontrado" : emptyResourceTitle(resource, config)}
            description={search ? "Ajuste a busca para consultar outros registros." : config.creatable ? "Crie o primeiro registro para começar." : "A fila está vazia no momento."}
            action={config.creatable && !search ? <Button onClick={() => setEditing("new")}><Plus size={16} /> Criar registro</Button> : undefined}
          />
        )}
        <footer className="pagination">
          {data?.pagination ? (
            <>
              <Button variant="quiet" size="sm" onClick={() => setPage((value) => Math.max(0, value - 1))} disabled={data.pagination.first}>Anterior</Button>
              <span>Página {data.pagination.number + 1} de {Math.max(1, data.pagination.totalPages)} · {points(data.pagination.totalElements)} registros</span>
              <Button variant="quiet" size="sm" onClick={() => setPage((value) => value + 1)} disabled={data.pagination.last}>Próxima</Button>
            </>
          ) : <span>{points(data?.rows.length || 0)} registros nesta visão</span>}
        </footer>
      </section>
      <ResourceForm resource={resource} config={config} record={editing} lookups={data?.lookups || {}} onClose={() => setEditing(null)} onSaved={() => reload().catch(() => undefined)} />
      <ScoreModal record={scoring} onClose={() => setScoring(null)} onSaved={() => reload().catch(() => undefined)} />
      <SettlementModal record={settling} onClose={() => setSettling(null)} onSettled={() => reload().catch(() => undefined)} />
      <ModerationModal record={moderating} onClose={() => setModerating(null)} onSaved={() => reload().catch(() => undefined)} />
      <GenerateMarketsModal open={generatingMarkets} events={data?.lookups.events || []} onClose={() => setGeneratingMarkets(false)} onSaved={() => reload().catch(() => undefined)} />
    </>
  );
}

function recordLabel(row: AdminRecord, config: ResourceConfig, index: number) {
  return String(row.name || row.title || row.action || `${config.singular} #${row.id || index + 1}`);
}

const feminineResources = new Set(["sports", "scoring-rules", "achievements", "notifications", "settings"]);

function emptyResourceTitle(resource: string, config: ResourceConfig) {
  const feminine = feminineResources.has(resource);
  return `${feminine ? "Nenhuma" : "Nenhum"} ${config.singular} ${feminine ? "encontrada" : "encontrado"}`;
}

function savedResourceMessage(resource: string, config: ResourceConfig, created: boolean) {
  const feminine = feminineResources.has(resource);
  const state = created
    ? (feminine ? "criada" : "criado")
    : (feminine ? "atualizada" : "atualizado");
  return `${capitalize(config.singular)} ${state} com sucesso.`;
}

function countLabel(value: unknown, singular: string, plural: string) {
  const count = Number(value || 0);
  return `${points(count)} ${count === 1 ? singular : plural}`;
}

function auditResource(row: AdminRecord) {
  const type = row.resourceType || row.referenceType;
  const id = row.resourceId || row.referenceId;
  if (!type) return "Recurso da plataforma";
  return `${enumLabel(String(type))}${id != null && id !== "" ? ` #${String(id)}` : ""}`;
}

function auditTitle(row: AdminRecord) {
  const value = String(row.title || row.action || "RECORDED").trim();
  return /^[A-Z\d]+(?:_[A-Z\d]+)+$/.test(value) ? enumLabel(value) : auditSummaryLabel(value);
}

function settingName(row: AdminRecord) {
  const names: Record<string, string> = {
    "demo-live-provider": "Atualizações ao vivo demonstrativas",
    "initial-demo-points": "Bônus inicial de demonstração",
  };
  return names[String(row.id || "")] || String(row.name || "Configuração");
}

function settingValue(value: unknown) {
  const text = String(value ?? "—").trim();
  return /^(?:true|false)$/i.test(text) || /^[A-Z\d]+(?:_[A-Z\d]+)+$/.test(text)
    ? enumLabel(text)
    : text;
}

function resultActionLabel(row: AdminRecord) {
  const classificationMode = ["INDIVIDUAL", "RACE"].includes(String(row.format || "").toUpperCase());
  if (classificationMode) {
    const hasClassification = Array.isArray(row.participants)
      && row.participants.some((item) => item && typeof item === "object" && (item as Record<string, unknown>).position != null);
    return `${hasClassification ? "Revisar" : "Registrar"} classificação`;
  }
  return `${row.homeScore != null ? "Revisar" : "Registrar"} placar`;
}

function recordStatus(row: AdminRecord) {
  return String(row.status || (row.active === false ? "inactive" : "active"));
}

function recordStatusLabel(row: AdminRecord) {
  return enumLabel(String(row.status || (row.active === false ? "INACTIVE" : "ACTIVE")));
}

const adminHeaderMap: Record<string, string[]> = {
  sports: ["Modalidade", "Identificação", "Status", "Atualização", "Ações"],
  championships: ["Campeonato", "Modalidade e temporada", "Status", "Período", "Ações"],
  competitors: ["Equipe ou participante", "Modalidade e país", "Status", "Atualização", "Ações"],
  events: ["Evento", "Campeonato", "Status", "Agenda e fechamento", "Ações"],
  results: ["Evento e resultado", "Campeonato", "Status", "Data do evento", "Ações"],
  markets: ["Mercado", "Evento e opções", "Status", "Liquidação", "Ações"],
  users: ["Usuário", "Perfil e acesso", "Saldo virtual", "Cadastro e atividade", "Ações"],
  pools: ["Bolão ou liga", "Organização", "Participação", "Período", "Ações"],
  "scoring-rules": ["Regra", "Fórmula", "Status", "Unidade", "Ações"],
  challenges: ["Desafio", "Métrica e objetivo", "Recompensa", "Período", "Ações"],
  achievements: ["Conquista", "Critério", "Recompensa", "Status", "Ações"],
  notifications: ["Notificação", "Destinatário e tipo", "Status", "Envio", "Ações"],
  moderation: ["Conteúdo", "Denúncia", "Status", "Recebimento", "Ações"],
  reports: ["Indicador", "Leitura operacional", "Status", "Atualização", "Ações"],
  audit: ["Ação", "Ator e recurso", "Resultado", "Data e hora", "Ações"],
  settings: ["Configuração", "Categoria e valor", "Status", "Atualização", "Ações"],
};

function adminHeaders(resource: string) {
  return adminHeaderMap[resource] || ["Registro", "Contexto", "Status", "Atualização", "Ações"];
}

function adminCells(resource: string, row: AdminRecord, config: ResourceConfig, index: number): ReactNode[] {
  const status = recordStatus(row);
  const statusBadge = <StatusBadge status={status} label={recordStatusLabel(row)} />;
  const updated = displayDate(row.updatedAt || row.createdAt);
  const activeBadge = <StatusBadge status={row.active === false ? "INACTIVE" : "ACTIVE"} />;
  const eventTitle = String(row.eventTitle || nestedText(row.event, "title") || "Evento não informado");

  switch (resource) {
    case "sports":
      return [
        <AdminIdentity icon={Gamepad2} title={String(row.name || "Modalidade")} subtitle={`Código ${presentationCode(row.code)}`} />,
        <AdminDetail primary={enumLabel(String(row.category || "OTHER"))} secondary={`Ordem de exibição ${String(row.displayOrder ?? 0)}`} />,
        activeBadge,
        updated,
      ];
    case "championships":
      return [
        <AdminIdentity icon={Trophy} title={String(row.name || "Campeonato")} subtitle={String(row.slug || "Identificador não informado")} />,
        <AdminDetail primary={String(row.sportName || "Modalidade não informada")} secondary={`Temporada ${String(row.season || "—")}`} />,
        statusBadge,
        periodLabel(row.startsAt, row.endsAt),
      ];
    case "competitors":
      return [
        <AdminIdentity icon={Users} title={String(row.name || "Participante")} subtitle={String(row.code || "Sem código")} image={typeof row.imageUrl === "string" ? row.imageUrl : undefined} logo />,
        <AdminDetail primary={String(row.sportName || nestedText(row.sport, "name") || "Modalidade não informada")} secondary={String(row.country || "País não informado")} />,
        activeBadge,
        updated,
      ];
    case "events":
    case "results": {
      const home = nestedText(row.homeCompetitor, "name") || "Participante 1";
      const away = nestedText(row.awayCompetitor, "name") || "Participante 2";
      const classificationMode = ["INDIVIDUAL", "RACE"].includes(String(row.format || "").toUpperCase());
      const classified = Array.isArray(row.participants)
        ? row.participants.filter((item) => item && typeof item === "object" && (item as Record<string, unknown>).position != null).length
        : 0;
      const score = classificationMode
        ? (classified ? `${classified} participantes classificados` : "Classificação pendente")
        : row.homeScore != null && row.awayScore != null ? `${row.homeScore} × ${row.awayScore}` : "Placar pendente";
      return [
        <AdminIdentity icon={resource === "results" ? ClipboardCheck : CalendarDays} title={String(row.title || `${home} × ${away}`)} subtitle={resource === "results" ? score : classificationMode ? enumLabel(String(row.format)) : `${home} × ${away}`} />,
        <AdminDetail primary={String(row.championship || "Campeonato não informado")} secondary={String(row.sport || row.stage || "Modalidade não informada")} />,
        statusBadge,
        <AdminDetail primary={displayDate(row.startsAt)} secondary={`Palpites até ${displayDate(row.predictionClosesAt)}`} />,
      ];
    }
    case "markets": {
      const options = marketOptions(row);
      const availability = row.availability as { label?: string; reason?: string } | undefined;
      const resultKeys = String(row.resultOptionKey || "").split(",");
      const resultLabel = options.filter((option) => resultKeys.includes(String(option.key))).map((option) => String(option.label)).join(", ");
      return [
        <AdminIdentity icon={Layers3} title={String(row.name || "Mercado")} subtitle={`${String(row.sportName || row.sport || "Modalidade do evento")} · ${String(row.category || "Principais")} · ${row.templateCode ? "Regra da modalidade" : "Regra manual"}`} />,
        <div className="admin-market-meta"><strong>{eventTitle}</strong><details><summary>{options.length} opções · multiplicadores demonstrativos</summary><ul>{options.map((option) => <li key={String(option.key)}>{String(option.label || enumLabel(option.key))}<b>{multiplier(option.multiplier)}</b></li>)}</ul></details></div>,
        <div className="admin-market-meta">{statusBadge}<small title={availability?.reason}>{availability?.label}</small><small>{enumLabel(row.timingMode || "PRE_MATCH_ONLY")}</small><small>Abertura: {row.opensAt ? displayDate(row.opensAt) : "Imediata"}</small><small>Fechamento: {row.closesAt ? displayDate(row.closesAt) : "Regra do evento"}</small></div>,
        <AdminDetail primary={row.settledAt ? displayDate(row.settledAt) : String(row.settlementDescription || "Resultado manual após o encerramento")} secondary={row.resultOptionKey ? `Resultado: ${resultLabel || enumLabel(String(row.resultOptionKey))}` : enumLabel(String(row.eventStatus || "PENDING"))} />,
      ];
    }
    case "users":
      return [
        <span className="admin-user-cell"><UserAvatar name={String(row.name || "Usuário")} avatarUrl={typeof row.avatarUrl === "string" ? row.avatarUrl : undefined} /><span><strong>{String(row.name || "Usuário")}</strong><small>{String(row.email || "E-mail não informado")}</small></span></span>,
        <AdminDetail primary={enumLabel(String(row.role || "PARTICIPANTE"))} secondary={statusBadge} />,
        <span className="admin-points"><Coins size={16} /> <strong>{points(row.pointBalance)} pts</strong><small>sem valor financeiro</small></span>,
        <AdminDetail primary={displayDate(row.createdAt)} secondary={`Atividade ${displayDate(row.updatedAt || row.createdAt)}`} />,
      ];
    case "pools":
      return [
        <AdminIdentity icon={Trophy} title={String(row.name || "Bolão")} subtitle={String(row.description || enumLabel(String(row.poolType || "POOL")))} />,
        <AdminDetail primary={String(row.ownerName || "Criador não informado")} secondary={[row.sportName, row.championshipName].filter(Boolean).join(" · ") || enumLabel(String(row.visibility || "PRIVATE"))} />,
        <AdminDetail primary={`${points(row.participantCount)} de ${points(row.maxParticipants)} participantes`} secondary={statusBadge} />,
        periodLabel(row.startsAt || row.createdAt, row.endsAt),
      ];
    case "scoring-rules":
      return [
        <AdminIdentity icon={Gauge} title={String(row.name || "Regra")} subtitle={String(row.description || "Sem explicação")} />,
        <code className="admin-formula">{String(row.calculation || "Fórmula não informada")}</code>,
        statusBadge,
        <AdminDetail primary={String(row.unit || "pontos virtuais")} secondary="Aplicação automática" />,
      ];
    case "challenges":
      return [
        <AdminIdentity icon={Target} title={String(row.name || "Desafio")} subtitle={String(row.description || "Sem descrição")} />,
        <AdminDetail primary={enumLabel(String(row.metric || "PREDICTION_COUNT"))} secondary={`Objetivo: ${points(row.target)}`} />,
        <span className="admin-points"><Zap size={16} /><strong>{points(row.rewardPoints)} pts</strong><small>{activeBadge}</small></span>,
        periodLabel(row.startsAt, row.expiresAt),
      ];
    case "achievements":
      return [
        <AdminIdentity icon={Award} title={String(row.name || "Conquista")} subtitle={`${enumLabel(String(row.rarity || "COMMON"))} · ${String(row.description || "Sem descrição")}`} />,
        <AdminDetail primary={enumLabel(String(row.rule || "FIRST_PREDICTION"))} secondary={`Meta ${points(row.target)}`} />,
        <span className="admin-points"><Zap size={16} /><strong>{points(row.pointsReward)} pts</strong><small>recompensa virtual</small></span>,
        activeBadge,
      ];
    case "notifications":
      return [
        <AdminIdentity icon={Bell} title={String(row.title || "Notificação")} subtitle={String(row.message || "Sem mensagem")} />,
        <AdminDetail primary={String(row.userName || (row.userId ? `Usuário #${row.userId}` : "Todos os participantes"))} secondary={enumLabel(String(row.type || "ADMIN_NOTICE"))} />,
        <StatusBadge status={row.read ? "READ" : "PENDING"} />,
        displayDate(row.createdAt),
      ];
    case "moderation":
      return [
        <AdminIdentity icon={ShieldCheck} title={String(row.title || "Conteúdo denunciado")} subtitle={String(row.postExcerpt || "Sem prévia disponível")} />,
        <AdminDetail primary={String(row.reason || "Motivo não informado")} secondary={`Por ${String(row.reporterName || "participante")}`} />,
        statusBadge,
        displayDate(row.createdAt),
      ];
    case "reports":
      return [
        <AdminIdentity icon={BarChart3} title={String(row.name || "Indicador")} subtitle={String(row.description || "Leitura operacional")} />,
        <span className="admin-report-value"><strong>{points(row.value)}</strong><small>{String(row.unit || "registros")}</small></span>,
        statusBadge,
        displayDate(row.updatedAt),
      ];
    case "audit":
      return [
        <AdminIdentity icon={BookOpen} title={auditTitle(row)} subtitle={enumLabel(String(row.action || "RECORDED"))} />,
        <AdminDetail primary={String(row.actor || "Sistema")} secondary={auditResource(row)} />,
        statusBadge,
        displayDate(row.createdAt),
      ];
    case "settings":
      return [
        <AdminIdentity icon={Settings2} title={settingName(row)} subtitle={String(row.description || "Parâmetro da aplicação")} />,
        <AdminDetail primary={enumLabel(String(row.category || "GENERAL"))} secondary={settingValue(row.value)} />,
        statusBadge,
        updated,
      ];
    default:
      return [
        <AdminIdentity icon={config.icon || BookOpen} title={recordLabel(row, config, index)} subtitle={`ID ${String(row.id ?? "—")}`} />,
        <AdminDetail primary="Dados cadastrados" secondary="Consulte as ações disponíveis" />,
        statusBadge,
        updated,
      ];
  }
}

function AdminIdentity({ icon: Icon, title, subtitle, image, logo = false }: { icon: LucideIcon; title: string; subtitle: string; image?: string; logo?: boolean }) {
  return (
    <span className="admin-identity">
      {logo ? <TeamLogo name={title} code={subtitle} logoUrl={image} size="sm" /> : image ? <img src={image} alt="" /> : <i><Icon size={18} /></i>}
      <span><strong>{title}</strong><small>{subtitle}</small></span>
    </span>
  );
}

function AdminDetail({ primary, secondary }: { primary: ReactNode; secondary?: ReactNode }) {
  return <span className="admin-detail"><strong>{primary}</strong>{secondary && <small>{secondary}</small>}</span>;
}

function nestedText(value: unknown, key: string) {
  return value && typeof value === "object" && key in value ? String((value as Record<string, unknown>)[key] || "") : "";
}

function displayDate(value: unknown) {
  return value ? dateTime(String(value), true) : "Sem registro";
}

function periodLabel(start: unknown, end: unknown) {
  return <AdminDetail primary={start ? displayDate(start) : "Início não definido"} secondary={end ? `até ${displayDate(end)}` : "Sem encerramento definido"} />;
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
  if (record.templateCode) return { allowed: false, label: "Liquidação em Resultados", reason: "Registre os dados da modalidade em Resultados para calcular todas as seleções deste mercado." };
  if (marketStatus !== "CLOSED") return { allowed: false, label: "Aguardando fechamento", reason: "Feche o mercado antes de liquidá-lo." };
  if (eventStatus !== "FINISHED") return { allowed: false, label: "Evento em andamento", reason: "O evento precisa estar encerrado antes da liquidação." };
  if (!hasOptions) return { allowed: false, label: "Sem opções", reason: "Configure ao menos uma opção ativa." };
  return { allowed: true, label: "Disponível", reason: "Mercado pronto para liquidação." };
}

function resultRegistrationAvailability(record: AdminRecord) {
  const status = String(record.status || "").toUpperCase();
  const classificationMode = ["INDIVIDUAL", "RACE"].includes(String(record.format || "").toUpperCase());
  const resultName = classificationMode ? "classificação" : "placar";
  if (status === "CANCELLED") return { allowed: false, label: "Evento cancelado", reason: `Eventos cancelados não aceitam ${resultName}.` };
  if (status === "POSTPONED") return { allowed: false, label: "Evento adiado", reason: `Eventos adiados não aceitam ${resultName}.` };
  const startsAt = new Date(String(record.startsAt || ""));
  if (["SCHEDULED", "OPEN_FOR_PREDICTIONS"].includes(status)
      && !Number.isNaN(startsAt.getTime()) && startsAt.getTime() > Date.now()) {
    return { allowed: false, label: "Aguardando início", reason: `O evento precisa começar antes do registro de ${resultName}.` };
  }
  if (["SCHEDULED", "OPEN_FOR_PREDICTIONS", "LIVE", "FINISHED"].includes(status)) {
    return { allowed: true, label: "Disponível", reason: `${capitalize(resultName)} disponível para conferência.` };
  }
  return { allowed: false, label: "Indisponível", reason: "O estado atual não permite registrar resultado." };
}

function initialForm(resource: string, config: ResourceConfig, record: AdminRecord | "new" | null) {
  const source = record && record !== "new" ? record : null;
  return Object.fromEntries((config.fields || []).map((field) => {
    let value = source?.[field.key];
    if (source && field.key === "homeCompetitorId") value = nestedId(source.homeCompetitor);
    if (source && field.key === "awayCompetitorId") value = nestedId(source.awayCompetitor);
    if (source && field.key === "optionsJson") value = JSON.stringify(source.options || [], null, 2);
    if (resource === "events" && field.key === "status" && value === "OPEN_FOR_PREDICTIONS") value = "SCHEDULED";
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

function ResourceForm({ resource, config, record, lookups, onClose, onSaved }: {
  resource: string;
  config: ResourceConfig;
  record: AdminRecord | "new" | null;
  lookups: AdminLookups;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<Record<string, unknown>>({});
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const savingRequestRef = useRef(false);
  const [confirmingCancellation, setConfirmingCancellation] = useState(false);
  const { notify } = useToast();

  useEffect(() => {
    setForm(initialForm(resource, config, record));
    setFieldErrors({});
    savingRequestRef.current = false;
    setConfirmingCancellation(false);
  }, [resource, config, record]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (savingRequestRef.current) return;
    const validation = validateResourceForm(resource, config, form);
    setFieldErrors(validation);
    const firstError = Object.values(validation)[0];
    if (firstError) {
      notify(firstError, "error");
      const firstField = Object.keys(validation)[0];
      window.requestAnimationFrame(() => document.getElementById(`admin-${resource}-${firstField}`)?.focus());
      return;
    }

    savingRequestRef.current = true;
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
      if (typeof payload.participantsJson === "string") {
        const parsed = JSON.parse(payload.participantsJson);
        if (!Array.isArray(parsed)) throw new Error("Revise os participantes do evento.");
      }

      const cancellingResource = ["events", "markets"].includes(resource) && record !== "new" && record?.id != null
        && String(payload.status).toUpperCase() === "CANCELLED"
        && String(record.status).toUpperCase() !== "CANCELLED";
      if (cancellingResource && !confirmingCancellation) {
        setConfirmingCancellation(true);
        savingRequestRef.current = false;
        setSaving(false);
        return;
      }

      if (cancellingResource && record?.id != null) {
        const result = await (resource === "events" ? adminApi.cancelEvent(record.id) : adminApi.cancelMarket(record.id));
        notify(`${resource === "events" ? "Evento" : "Mercado"} cancelado; ${countLabel(result.refundedPredictions, "palpite reembolsado", "palpites reembolsados")}.`, "success");
      } else if (record === "new") {
        await adminApi.create(resource, payload);
        notify(savedResourceMessage(resource, config, true), "success");
      } else if (record?.id != null) {
        await adminApi.update(resource, record.id, payload);
        notify(savedResourceMessage(resource, config, false), "success");
      }
      onClose();
      onSaved();
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors) {
        setFieldErrors(error.fieldErrors);
        const firstField = Object.keys(error.fieldErrors)[0];
        window.requestAnimationFrame(() => document.getElementById(`admin-${resource}-${firstField}`)?.focus());
      }
      notify(error instanceof SyntaxError ? "Revise as opções ou os participantes informados." : error instanceof Error ? error.message : "Não foi possível salvar.", "error");
    } finally {
      savingRequestRef.current = false;
      setSaving(false);
    }
  }

  return (
    <Modal open={Boolean(record)} onClose={() => !saving && onClose()} title={`${record === "new" ? "Criar" : "Editar"} ${config.singular}`}>
      <form className="stack-form" onSubmit={submit}>
        {config.fields?.map((field) => {
          const fieldId = `admin-${resource}-${field.key}`;
          const errorId = `${fieldId}-error`;
          const error = fieldErrors[field.key];
          const commonProps = { id: fieldId, "aria-invalid": Boolean(error), "aria-describedby": error ? errorId : undefined };
          if (field.key === "optionsJson" || field.key === "participantsJson") {
            return (
              <div className="stack-field" key={field.key}>
                <span>{field.label}{field.required && <em aria-hidden="true"> *</em>}</span>
                {field.key === "optionsJson" ? (
                  <MarketOptionsBuilder id={fieldId} value={String(form[field.key] ?? "[]")} onChange={(value) => setForm((current) => ({ ...current, [field.key]: value }))} />
                ) : (
                  <EventParticipantsBuilder id={fieldId} value={String(form[field.key] ?? "[]")} competitors={lookups.competitors || []} onChange={(value) => setForm((current) => ({ ...current, [field.key]: value }))} />
                )}
                {error && <small className="field-error" id={errorId}>{error}</small>}
              </div>
            );
          }
          return (
            <label className={field.type === "checkbox" ? "toggle-row" : ""} htmlFor={fieldId} key={field.key}>
              <span>{field.label}{field.required && <em aria-hidden="true"> *</em>}</span>
              {field.reference ? (
                <select {...commonProps} value={String(form[field.key] ?? "")} required={field.required} onChange={(event) => setForm((value) => ({ ...value, [field.key]: event.target.value ? Number(event.target.value) : "" }))}>
                  <option value="">{field.required ? "Selecione uma opção" : "Nenhum / todos"}</option>
                  {(lookups[field.reference] || []).map((option) => <option value={String(option.id)} key={String(option.id)}>{lookupLabel(field.reference!, option)}</option>)}
                </select>
              ) : field.type === "select" ? (
                <select {...commonProps} value={String(form[field.key] ?? "")} required={field.required} onChange={(event) => { const next = field.numeric ? Number(event.target.value) : event.target.value; setForm((value) => ({ ...value, [field.key]: next })); if (["events", "markets"].includes(resource) && field.key === "status" && next !== "CANCELLED") setConfirmingCancellation(false); }}>
                  {field.options?.map((option) => <option key={option} value={option}>{enumLabel(option)}</option>)}
                </select>
              ) : field.type === "textarea" ? (
                <textarea {...commonProps} required={field.required} value={String(form[field.key] ?? "")} onChange={(event) => setForm((value) => ({ ...value, [field.key]: event.target.value }))} />
              ) : field.type === "checkbox" ? (
                <input {...commonProps} type="checkbox" checked={Boolean(form[field.key])} onChange={(event) => setForm((value) => ({ ...value, [field.key]: event.target.checked }))} />
              ) : (
                <input
                  {...commonProps}
                  type={field.type || "text"}
                  required={field.required}
                  step={field.type === "number" ? 1 : undefined}
                  value={String(form[field.key] ?? "")}
                  onChange={(event) => setForm((value) => ({ ...value, [field.key]: field.type === "number" && event.target.value !== "" ? Number(event.target.value) : event.target.value }))}
                />
              )}
              {error && <small className="field-error" id={errorId}>{error}</small>}
            </label>
          );
        })}
        {confirmingCancellation && <div className="virtual-disclaimer"><AlertTriangle size={16} /> Confirme novamente: {resource === "events" ? "o evento e seus mercados serão cancelados" : "este mercado será cancelado"} e os palpites ativos correspondentes serão reembolsados em pontos.</div>}
        <div className="modal-actions"><Button type="button" variant="secondary" onClick={onClose} disabled={saving}>Voltar</Button><Button type="submit" loading={saving}>{confirmingCancellation ? "Confirmar cancelamento" : "Salvar"}</Button></div>
      </form>
    </Modal>
  );
}

function isMissing(value: unknown) {
  return value === undefined || value === null || value === "" || (typeof value === "string" && !value.trim());
}

export function validateResourceForm(resource: string, config: ResourceConfig, form: Record<string, unknown>) {
  const errors: Record<string, string> = {};
  for (const field of config.fields || []) {
    const value = form[field.key];
    if (field.required && isMissing(value)) errors[field.key] = `Preencha “${field.label}”.`;
    if (field.type === "number" && !isMissing(value) && (!Number.isFinite(Number(value)) || Number(value) < 0)) {
      errors[field.key] = `${field.label} deve ser um número válido e não negativo.`;
    }
  }

  const chronologicalPairs: Array<[string, string, string]> = resource === "championships"
    ? [["startsAt", "endsAt", "O fim do campeonato deve ser posterior ao início."]]
    : resource === "events"
      ? [["predictionClosesAt", "startsAt", "O fechamento dos palpites deve ocorrer antes do início do evento."]]
      : resource === "challenges"
        ? [["startsAt", "expiresAt", "O encerramento do desafio deve ser posterior ao início."]]
        : [];
  for (const [firstKey, secondKey, message] of chronologicalPairs) {
    const first = form[firstKey] ? new Date(String(form[firstKey])).getTime() : Number.NaN;
    const second = form[secondKey] ? new Date(String(form[secondKey])).getTime() : Number.NaN;
    const invalid = first >= second;
    if (Number.isFinite(first) && Number.isFinite(second) && invalid) errors[resource === "events" ? firstKey : secondKey] = message;
  }

  if (resource === "events" && !isMissing(form.homeCompetitorId) && String(form.homeCompetitorId) === String(form.awayCompetitorId)) {
    errors.awayCompetitorId = "Selecione participantes diferentes para o evento.";
  }
  if (resource === "notifications" && !isMissing(form.targetUrl)) {
    const targetUrl = String(form.targetUrl).trim();
    if (!targetUrl.startsWith("/") || targetUrl.startsWith("//")) errors.targetUrl = "Informe um destino interno iniciado por /, como /notifications.";
  }

  for (const key of ["optionsJson", "participantsJson"]) {
    if (!(key in form)) continue;
    try {
      const parsed = JSON.parse(String(form[key] || "[]"));
      if (!Array.isArray(parsed) || (key === "optionsJson" && parsed.length === 0)) {
        errors[key] = key === "optionsJson" ? "Adicione ao menos uma opção ao mercado." : "Revise os participantes informados.";
      } else if (key === "optionsJson") {
        const optionKeys = parsed.map((item) => String(item?.key || "").trim().toUpperCase());
        const invalidOption = parsed.some((item) => !String(item?.key || "").trim()
          || !String(item?.label || "").trim()
          || !Number.isFinite(Number(item?.multiplier))
          || Number(item.multiplier) < 1);
        if (invalidOption) errors[key] = "Preencha chave, rótulo e multiplicador válido em todas as opções.";
        else if (new Set(optionKeys).size !== optionKeys.length) errors[key] = "Cada opção precisa ter uma chave exclusiva.";
      } else {
        const participantIds = parsed.map((item) => String(item?.competitorId || "")).filter(Boolean);
        if (new Set(participantIds).size !== participantIds.length) errors[key] = "Cada participante pode aparecer apenas uma vez.";
      }
    } catch {
      errors[key] = key === "optionsJson" ? "Revise as opções do mercado." : "Revise os participantes do evento.";
    }
  }
  return errors;
}

function lookupLabel(reference: NonNullable<FieldConfig["reference"]>, option: AdminRecord) {
  if (reference === "sports") return `${String(option.name || "Modalidade")} · ${String(option.code || enumLabel(String(option.category || "")))}`;
  if (reference === "championships") return `${String(option.name || "Campeonato")} · ${String(option.sportName || option.season || "")}`;
  if (reference === "competitors") return `${String(option.name || "Participante")} · ${String(option.code || option.country || "")}`;
  if (reference === "events") return `${String(option.title || "Evento")} · ${displayDate(option.startsAt)}`;
  if (reference === "users") return `${String(option.name || "Usuário")} · ${String(option.email || "")}`;
  return String(option.name || option.title || option.id || "Registro");
}

function parseBuilderItems(value: string) {
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === "object") : [];
  } catch {
    return [];
  }
}

function MarketOptionsBuilder({ id, value, onChange }: { id: string; value: string; onChange: (value: string) => void }) {
  const items = parseBuilderItems(value);
  const commit = (next: Record<string, unknown>[]) => onChange(JSON.stringify(next));
  const update = (index: number, key: string, nextValue: unknown) => commit(items.map((item, itemIndex) => itemIndex === index ? { ...item, [key]: nextValue } : item));
  return (
    <div className="admin-builder" id={id} tabIndex={-1}>
      <div className="admin-builder__head"><span>Opções publicadas</span><small>Os multiplicadores calculam somente pontos virtuais.</small></div>
      {items.map((item, index) => (
        <div className="admin-builder__row" key={`${String(item.key || "option")}-${index}`}>
          <label><span>Chave</span><input value={String(item.key || "")} onChange={(event) => update(index, "key", event.target.value.toUpperCase().replace(/\s+/g, "_"))} required /></label>
          <label><span>Rótulo</span><input value={String(item.label || "")} onChange={(event) => update(index, "label", event.target.value)} required /></label>
          <label><span>Multiplicador</span><input type="number" min="1" max="100" step="0.01" value={String(item.multiplier ?? 1)} onChange={(event) => update(index, "multiplier", Number(event.target.value))} required /></label>
          <label className="admin-builder__toggle"><span>Ativa</span><input type="checkbox" checked={item.active !== false} onChange={(event) => update(index, "active", event.target.checked)} /></label>
          <button type="button" className="icon-button" onClick={() => commit(items.filter((_, itemIndex) => itemIndex !== index))} aria-label={`Remover opção ${String(item.label || index + 1)}`}><X size={17} /></button>
        </div>
      ))}
      <Button type="button" variant="secondary" size="sm" onClick={() => commit([...items, { key: `OPTION_${items.length + 1}`, label: "Nova opção", multiplier: 1.5, active: true }])}><Plus size={16} /> Adicionar opção</Button>
    </div>
  );
}

function EventParticipantsBuilder({ id, value, competitors, onChange }: { id: string; value: string; competitors: AdminRecord[]; onChange: (value: string) => void }) {
  const items = parseBuilderItems(value);
  const commit = (next: Record<string, unknown>[]) => onChange(JSON.stringify(next.map((item, index) => ({ ...item, displayOrder: index }))));
  const update = (index: number, key: string, nextValue: unknown) => commit(items.map((item, itemIndex) => itemIndex === index ? { ...item, [key]: nextValue } : item));
  const usedIds = new Set(items.map((item) => String(item.competitorId || "")));
  return (
    <div className="admin-builder" id={id} tabIndex={-1}>
      <div className="admin-builder__head"><span>Classificação ou participantes adicionais</span><small>Use para corridas, torneios individuais e eventos com mais de dois participantes.</small></div>
      {items.map((item, index) => (
        <div className="admin-builder__row admin-builder__row--participants" key={`${String(item.competitorId || "participant")}-${index}`}>
          <label><span>Participante</span><select value={String(item.competitorId || "")} onChange={(event) => update(index, "competitorId", Number(event.target.value))} required><option value="">Selecione</option>{competitors.map((competitor) => <option value={String(competitor.id)} key={String(competitor.id)}>{lookupLabel("competitors", competitor)}</option>)}</select></label>
          <label><span>Posição</span><input type="number" min="1" step="1" value={String(item.position || "")} onChange={(event) => update(index, "position", event.target.value ? Number(event.target.value) : null)} /></label>
          <label><span>Placar ou tempo</span><input value={String(item.scoreLabel || "")} onChange={(event) => update(index, "scoreLabel", event.target.value)} /></label>
          <button type="button" className="icon-button" onClick={() => commit(items.filter((_, itemIndex) => itemIndex !== index))} aria-label={`Remover participante ${index + 1}`}><X size={17} /></button>
        </div>
      ))}
      <Button type="button" variant="secondary" size="sm" disabled={!competitors.some((item) => !usedIds.has(String(item.id)))} onClick={() => { const available = competitors.find((item) => !usedIds.has(String(item.id))); if (available?.id != null) commit([...items, { competitorId: Number(available.id), displayOrder: items.length, position: null, scoreLabel: "" }]); }}><Plus size={16} /> Adicionar participante</Button>
    </div>
  );
}

function capitalize(value: string) {
  return value.charAt(0).toLocaleUpperCase("pt-BR") + value.slice(1);
}

type ClassificationDraft = {
  competitorId: number | string;
  displayOrder: number;
  name: string;
  position: number | "";
  scoreLabel: string;
};

function classificationDraft(record: AdminRecord | null): ClassificationDraft[] {
  if (!record || !Array.isArray(record.participants)) return [];
  return record.participants.flatMap((value, index) => {
    if (!value || typeof value !== "object") return [];
    const participant = value as Record<string, unknown>;
    const competitor = participant.competitor && typeof participant.competitor === "object"
      ? participant.competitor as Record<string, unknown>
      : null;
    const competitorId = participant.competitorId ?? competitor?.id;
    if (competitorId == null || competitorId === "") return [];
    const currentPosition = Number(participant.position);
    const draft: ClassificationDraft = {
      competitorId: competitorId as number | string,
      displayOrder: Number(participant.displayOrder ?? index),
      name: String(competitor?.name || `Participante ${index + 1}`),
      position: Number.isInteger(currentPosition) && currentPosition > 0 ? currentPosition : "",
      scoreLabel: String(participant.scoreLabel || ""),
    };
    return [draft];
  }).sort((left, right) => left.displayOrder - right.displayOrder);
}

function GenerateMarketsModal({ open, events, onClose, onSaved }: { open: boolean; events: AdminRecord[]; onClose: () => void; onSaved: () => void }) {
  const [eventId, setEventId] = useState("");
  const [templates, setTemplates] = useState<MarketTemplate[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const { notify } = useToast();
  useEffect(() => { if (!open) { setEventId(""); setTemplates([]); setError(""); } }, [open]);
  useEffect(() => {
    if (!open || !eventId) return;
    let active = true;
    setLoading(true);
    setTemplates([]);
    setError("");
    adminApi.marketTemplates(eventId).then((values) => { if (active) setTemplates(values); }).catch((reason) => { if (active) setError(reason instanceof Error ? reason.message : "Não foi possível carregar o catálogo."); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [open, eventId]);
  async function generate(event: FormEvent) {
    event.preventDefault();
    if (!eventId || saving || loading || !templates.length) return;
    setSaving(true);
    setError("");
    try {
      await adminApi.generateMarkets(eventId);
      notify("Catálogo da modalidade aplicado. Mercados existentes foram preservados.", "success");
      onClose();
      onSaved();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Não foi possível gerar os mercados."); }
    finally { setSaving(false); }
  }
  return <Modal open={open} onClose={() => !saving && onClose()} title="Catálogo de mercados da modalidade">
    <form className="stack-form" onSubmit={generate}>
      <p>Selecione o evento para revisar os mercados com regras de resultado da modalidade e multiplicadores demonstrativos persistidos.</p>
      <label><span>Evento *</span><select required value={eventId} disabled={saving} onChange={(event) => setEventId(event.target.value)}><option value="">Selecione um evento</option>{events.filter((event) => !["FINISHED", "CANCELLED"].includes(String(event.status))).map((event) => <option key={String(event.id)} value={String(event.id)}>{String(event.title || event.name)}</option>)}</select></label>
      {loading && <p role="status">Carregando catálogo da modalidade...</p>}
      <div className="admin-template-list">{templates.map((template) => <article key={template.code}><strong>{template.name}</strong><small>{template.category} · {enumLabel(template.timingMode)}</small>{template.settlementDescription && <p>{template.settlementDescription}</p>}</article>)}</div>
      {error && <p role="alert" className="field-error">{error}</p>}
      <div className="modal-actions"><Button type="button" variant="secondary" onClick={onClose} disabled={saving}>Voltar</Button><Button type="submit" loading={saving} disabled={!eventId || loading || !templates.length}>Aplicar catálogo</Button></div>
    </form>
  </Modal>;
}

export function resultFieldsOf(value: unknown): ResultField[] {
  if (!Array.isArray(value)) return [];
  return value.filter((field): field is ResultField => Boolean(field) && typeof field === "object" && typeof field.key === "string" && typeof field.label === "string" && ["number", "select"].includes(field.type));
}

export function validateResultFields(schema: ResultField[], values: Record<string, string>, settling: boolean) {
  const errors: Record<string, string> = {};
  for (const field of schema) {
    const value = String(values[field.key] ?? "").trim();
    if (!value) {
      if (settling && field.required) errors[field.key] = `Informe “${field.label}” para liquidar os mercados.`;
      continue;
    }
    if (field.type === "number" && (!Number.isFinite(Number(value)) || Number(value) < 0 || Number(value) > 10_000 || !Number.isInteger(Number(value)))) errors[field.key] = `Informe um número inteiro de 0 a 10.000 em “${field.label}”.`;
    if (field.type === "select" && !field.options?.some((option) => option.value === value)) errors[field.key] = `Selecione uma opção válida em “${field.label}”.`;
  }
  return errors;
}

export function ScoreModal({ record, onClose, onSaved }: {
  record: AdminRecord | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const classificationMode = ["INDIVIDUAL", "RACE"].includes(String(record?.format || "").toUpperCase());
  const [homeScore, setHomeScore] = useState(0);
  const [awayScore, setAwayScore] = useState(0);
  const [classification, setClassification] = useState<ClassificationDraft[]>([]);
  const [finishEvent, setFinishEvent] = useState(true);
  const [settleMarkets, setSettleMarkets] = useState(true);
  const [resultData, setResultData] = useState<Record<string, string>>({});
  const [resultErrors, setResultErrors] = useState<Record<string, string>>({});
  const [confirmed, setConfirmed] = useState(false);
  const [validationError, setValidationError] = useState("");
  const [saving, setSaving] = useState(false);
  const operationKey = useRef(createIdempotencyKey());
  const savingRef = useRef(false);
  const { notify } = useToast();
  const resultSchema = resultFieldsOf(record?.resultSchema);
  const resultGroups = Array.from(new Set(resultSchema.map((field) => field.group || "Dados do evento")));

  useEffect(() => {
    setHomeScore(Number(record?.homeScore ?? 0));
    setAwayScore(Number(record?.awayScore ?? 0));
    setClassification(classificationDraft(record));
    setFinishEvent(true);
    setSettleMarkets(true);
    setResultData(record?.resultData && typeof record.resultData === "object" ? record.resultData as Record<string, string> : {});
    setResultErrors({});
    savingRef.current = false;
    setConfirmed(false);
    setValidationError("");
    operationKey.current = createIdempotencyKey();
  }, [record]);

  async function saveScore(event: FormEvent) {
    event.preventDefault();
    if (record?.id == null || savingRef.current) return;
    const fieldErrors = validateResultFields(resultSchema, resultData, settleMarkets && finishEvent);
    setResultErrors(fieldErrors);
    if (Object.keys(fieldErrors).length) {
      setValidationError("Preencha os dados necessários para liquidar os mercados desta modalidade.");
      const firstInput = document.getElementById(`result-${Object.keys(fieldErrors)[0]}`);
      firstInput?.closest("details")?.setAttribute("open", "");
      firstInput?.focus();
      return;
    }
    if (classificationMode && !classification.length) {
      setValidationError("Este evento não possui participantes disponíveis para classificação.");
      return;
    }
    if (classificationMode && classification.some((item) => !Number.isInteger(item.position) || Number(item.position) < 1)) {
      setValidationError("Informe uma posição inteira e positiva para cada participante.");
      return;
    }
    if (classificationMode && new Set(classification.map((item) => Number(item.position))).size !== classification.length) {
      setValidationError("Cada participante deve ocupar uma posição diferente.");
      return;
    }
    if (classificationMode && finishEvent && classification.some((item) => Number(item.position) > classification.length)) {
      setValidationError(`A classificação final deve ocupar todas as posições de 1 a ${classification.length}.`);
      return;
    }
    if (classificationMode && classification.some((item) => item.scoreLabel.trim().length > 80)) {
      setValidationError("A marca ou resultado deve ter no máximo 80 caracteres.");
      return;
    }
    if (!classificationMode && (!Number.isInteger(homeScore) || !Number.isInteger(awayScore) || homeScore < 0 || awayScore < 0 || homeScore > 1_000 || awayScore > 1_000)) {
      setValidationError("Informe placares inteiros de 0 a 1.000.");
      return;
    }
    if (!confirmed) {
      setValidationError(`Confirme a conferência ${classificationMode ? "da classificação" : "do placar"} antes de salvar.`);
      return;
    }
    setValidationError("");
    savingRef.current = true;
    setSaving(true);
    try {
      if (classificationMode) {
        await adminApi.recordEventClassification(record.id, {
          participants: classification.map((item) => ({
            competitorId: Number(item.competitorId),
            displayOrder: item.displayOrder,
            position: Number(item.position),
            scoreLabel: item.scoreLabel.trim() || null,
          })),
          finishEvent,
          resultData,
          settleMarkets: settleMarkets && finishEvent,
        }, operationKey.current);
      } else {
        await adminApi.recordEventResult(record.id, { homeScore, awayScore, finishEvent, resultData, settleMarkets: settleMarkets && finishEvent }, operationKey.current);
      }
      notify(settleMarkets && finishEvent ? "Resultado registrado e mercados processados. Os créditos não são repetidos." : "Resultado salvo. Complete os dados e liquide os mercados quando estiverem conferidos.", "success");
      onClose();
      onSaved();
    } catch (error) {
      const message = error instanceof Error ? error.message : `Não foi possível registrar ${classificationMode ? "a classificação" : "o placar"}.`;
      setValidationError(message);
      notify(message, "error");
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  return (
    <Modal
      open={Boolean(record)}
      onClose={() => !savingRef.current && onClose()}
      title={classificationMode ? "Registrar classificação oficial" : "Registrar resultado da modalidade"}
      size={classificationMode || resultSchema.length ? "lg" : "sm"}
    >
      <form className="result-form" onSubmit={saveScore}>
        <fieldset className="result-form__fields" disabled={saving}>
        <div className="result-form__event">
          <span className="eyebrow">{classificationMode ? "CLASSIFICAÇÃO DO EVENTO" : "RESULTADO DO EVENTO"}</span>
          <h2>{String(record?.title || "Evento selecionado")}</h2>
          <StatusBadge status={String(record?.status || "SCHEDULED")} />
        </div>
        {classificationMode ? (
          <div className="admin-builder">
            <div className="admin-builder__head"><span>Ordem oficial</span><small>Informe posições únicas e, quando existir, a marca, o tempo ou o resultado de cada participante.</small></div>
            {classification.map((item, index) => (
              <div className="admin-builder__row admin-builder__row--participants" key={String(item.competitorId)}>
                <label><span>Participante</span><input value={item.name} readOnly tabIndex={-1} /></label>
                <label htmlFor={`classification-position-${item.competitorId}`}><span>Posição *</span><input id={`classification-position-${item.competitorId}`} aria-label={`Posição de ${item.name}`} autoFocus={index === 0} type="number" min="1" step="1" required value={item.position} onChange={(event) => { const value = event.target.value ? Number(event.target.value) : ""; setClassification((current) => current.map((entry, entryIndex) => entryIndex === index ? { ...entry, position: value } : entry)); setValidationError(""); }} /></label>
                <label htmlFor={`classification-score-${item.competitorId}`}><span>Marca ou resultado</span><input id={`classification-score-${item.competitorId}`} aria-label={`Marca ou resultado de ${item.name}`} maxLength={80} value={item.scoreLabel} placeholder="Ex.: 1:32.450 ou 18 pts" onChange={(event) => { setClassification((current) => current.map((entry, entryIndex) => entryIndex === index ? { ...entry, scoreLabel: event.target.value } : entry)); setValidationError(""); }} /></label>
              </div>
            ))}
          </div>
        ) : (
          <div className="result-form__score">
            <span><label htmlFor="home-score">{nestedText(record?.homeCompetitor, "name") || "Participante 1"}</label><input id="home-score" aria-label={`Placar de ${nestedText(record?.homeCompetitor, "name") || "participante 1"}`} type="number" min="0" step="1" value={homeScore} onChange={(event) => { setHomeScore(Number(event.target.value)); setValidationError(""); }} /></span>
            <b>×</b>
            <span><label htmlFor="away-score">{nestedText(record?.awayCompetitor, "name") || "Participante 2"}</label><input id="away-score" aria-label={`Placar de ${nestedText(record?.awayCompetitor, "name") || "participante 2"}`} type="number" min="0" step="1" value={awayScore} onChange={(event) => { setAwayScore(Number(event.target.value)); setValidationError(""); }} /></span>
          </div>
        )}
        {resultGroups.length > 0 && <div className="result-groups">{resultGroups.map((group, groupIndex) => <details key={group} open={groupIndex === 0 || undefined}>
          <summary>{group}<span>{resultSchema.filter((field) => (field.group || "Dados do evento") === group).length} campos</span></summary>
          <div className="result-schema-fields">{resultSchema.filter((field) => (field.group || "Dados do evento") === group).map((field) => <label key={field.key} htmlFor={`result-${field.key}`}><span>{field.label}{field.required && settleMarkets && finishEvent ? " *" : ""}</span>
            {field.type === "select" ? <select id={`result-${field.key}`} value={resultData[field.key] ?? ""} disabled={saving} aria-invalid={Boolean(resultErrors[field.key])} aria-describedby={`result-help-${field.key}`} onChange={(event) => { setResultData((current) => ({ ...current, [field.key]: event.target.value })); setResultErrors((current) => ({ ...current, [field.key]: "" })); }}><option value="">Não informado</option>{field.options?.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select> : <input id={`result-${field.key}`} type="number" min="0" step="1" value={resultData[field.key] ?? ""} disabled={saving} aria-invalid={Boolean(resultErrors[field.key])} aria-describedby={`result-help-${field.key}`} onChange={(event) => { setResultData((current) => ({ ...current, [field.key]: event.target.value })); setResultErrors((current) => ({ ...current, [field.key]: "" })); }} />}
            <small id={`result-help-${field.key}`} className={resultErrors[field.key] ? "field-error" : ""}>{resultErrors[field.key] || field.description || "Dado utilizado na liquidação dos mercados do evento."}</small>
          </label>)}</div>
        </details>)}</div>}
        <label className="toggle-row"><span>Marcar evento como encerrado</span><input type="checkbox" checked={finishEvent} onChange={(event) => setFinishEvent(event.target.checked)} /></label>
        <label className="toggle-row"><span>Liquidar mercados com este resultado</span><input type="checkbox" checked={settleMarkets && finishEvent} disabled={!finishEvent || saving} onChange={(event) => setSettleMarkets(event.target.checked)} /></label>
        <p><AlertTriangle size={16} /> {settleMarkets && finishEvent ? "Todos os dados necessários devem estar preenchidos. O servidor calcula vencedores, perdedores e pontos em uma única operação, sem créditos duplicados." : "Você pode salvar os dados disponíveis e concluir a liquidação depois. Nenhum mercado será pago nesta etapa."}</p>
        <label className="result-confirmation"><input type="checkbox" checked={confirmed} onChange={(event) => { setConfirmed(event.target.checked); setValidationError(""); }} /><span><strong>Revisei o evento e {classificationMode ? "a classificação" : "o placar"}</strong><small>Entendo que a ação será registrada na auditoria administrativa.</small></span></label>
        {validationError && <p className="field-error" role="alert">{validationError}</p>}
        <div className="modal-actions"><Button type="button" variant="secondary" onClick={onClose} disabled={saving}>Cancelar</Button><Button type="submit" loading={saving} disabled={!confirmed}>Salvar {classificationMode ? "classificação" : "placar"}</Button></div>
        </fieldset>
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
    if (record?.id == null || !correctOptionKey || !availability.allowed || saving) return;
    setSaving(true);
    try {
      const result = await adminApi.settleMarket(record.id, correctOptionKey);
      const summary = result.alreadySettled
        ? "Este mercado já estava liquidado; nenhum ponto foi processado novamente."
        : `Mercado liquidado: ${countLabel(result.winners, "vencedor", "vencedores")}, ${countLabel(result.losers, "perdedor", "perdedores")} e ${countLabel(result.rewardedPoints, "ponto creditado", "pontos creditados")}.`;
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
    <Modal open={Boolean(record)} onClose={() => !saving && onClose()} title="Liquidar mercado" size="sm">
      <form className="stack-form" onSubmit={settle}>
        <div><h2>{String(record?.name || "Mercado")}</h2><p>Selecione a opção oficial correta. Apenas mercados fechados de eventos encerrados podem ser processados.</p></div>
        {options.length ? (
          <label>
            <span>Opção correta *</span>
            <select value={correctOptionKey} onChange={(event) => setCorrectOptionKey(event.target.value)} required>
              {options.map((option) => (
                <option value={String(option.key)} key={String(option.id || option.key)}>{String(option.label || enumLabel(String(option.key)))}</option>
              ))}
            </select>
          </label>
        ) : <EmptyState icon={AlertTriangle} title="Mercado sem opções ativas" description="Edite o mercado e configure ao menos uma opção antes da liquidação." />}
        <div className="virtual-footer-note"><ShieldCheck size={15} /> A liquidação movimenta somente pontos virtuais, sem qualquer valor financeiro.</div>
        {!availability.allowed && <p className="field-error" role="alert">{availability.reason}</p>}
        <div className="modal-actions"><Button type="button" variant="secondary" onClick={onClose} disabled={saving}>Cancelar</Button><Button type="submit" loading={saving} disabled={!correctOptionKey || !availability.allowed}>Confirmar liquidação</Button></div>
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
    if (record?.id == null || saving) return;
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
    <Modal open={Boolean(record)} onClose={() => !saving && onClose()} title="Revisar denúncia" size="sm">
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
            <span><AlertTriangle size={17} /><span><strong>Ocultar publicação</strong><small>Remove o conteúdo da comunidade após a confirmação.</small></span></span>
            <input type="checkbox" checked={hidePost} onChange={(event) => { setHidePost(event.target.checked); if (event.target.checked) setStatus("REVIEWED"); }} />
          </label>
        )}
        <div className="virtual-disclaimer"><ShieldCheck size={16} /> A decisão fica registrada na auditoria administrativa.</div>
        <div className="modal-actions"><Button type="button" variant="secondary" onClick={onClose} disabled={saving}>Cancelar</Button><Button type="submit" loading={saving}>Confirmar decisão</Button></div>
      </form>
    </Modal>
  );
}
