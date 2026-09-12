import { Activity, CalendarDays, ChevronDown, Filter, Radio, RefreshCcw, Search, ShieldCheck, SlidersHorizontal, Trophy, Wifi } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { championshipName, dateTime, eventTeams, sportName } from "../app/format";
import { enumLabel } from "../app/presentation";
import { EventCard, isMultiParticipantEvent, MarketList, ParticipantList } from "../components/EventCard";
import { TeamLogo } from "../components/TeamLogo";
import { PredictionComposer } from "../components/PredictionComposer";
import { Button, EmptyState, ErrorState, NoResults, PageHeader, PageSkeleton, StatusBadge } from "../components/UI";
import { useToast } from "../contexts/ToastContext";
import { useApiResource } from "../hooks/useApiResource";
import { asList, catalogApi, eventsApi } from "../services/api";
import type { ArenaEvent, PredictionDraft, PredictionMarket, Sport } from "../types";
import "../markets.css";

const statuses = [
  { value: "", label: "Todos" },
  { value: "OPEN_FOR_PREDICTIONS", label: "Abertos" },
  { value: "LIVE", label: "Ao vivo" },
  { value: "SCHEDULED", label: "Agendados" },
  { value: "FINISHED", label: "Encerrados" },
];

export function filterEventCatalog(events: ArenaEvent[], search: string, featuredOnly: boolean) {
  const term = search.toLocaleLowerCase("pt-BR").trim();
  return events.filter((event) => {
    if (featuredOnly && !event.featured) return false;
    if (!term) return true;
    const [home, away] = eventTeams(event);
    const participantTerms = (event.participants || []).flatMap((item) => [item.competitor.name, item.competitor.code]);
    const competitorTerms = (event.competitors || []).flatMap((item) => [item.name, item.code]);
    return [home.name, away.name, home.code, away.code, ...participantTerms, ...competitorTerms, sportName(event.sport || event.sportName), championshipName(event.championship || event.championshipName), event.title]
      .filter(Boolean)
      .some((value) => String(value).toLocaleLowerCase("pt-BR").includes(term));
  });
}

export function EventsPage() {
  const [params, setParams] = useSearchParams();
  const [draft, setDraft] = useState<PredictionDraft | null>(null);
  const search = params.get("q") || "";
  const status = params.get("status") || "";
  const sport = params.get("sport") || "";
  const featured = params.get("featured") === "true";
  const [searchInput, setSearchInput] = useState(search);

  const { data, loading, error, reload } = useApiResource(
    async () => {
      const [eventResponse, sportResponse] = await Promise.all([
        eventsApi.list({ status: status || undefined, sport: sport || undefined, featured: featured || undefined, size: 48 }),
        catalogApi.sports(),
      ]);
      return { events: asList(eventResponse), sports: asList(sportResponse) };
    },
    [status, sport, featured],
  );

  useEffect(() => setSearchInput(search), [search]);

  const events = useMemo(() => {
    return filterEventCatalog(data?.events || [], search, featured);
  }, [data?.events, search, featured]);

  function update(key: string, value: string) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next, { replace: true });
  }

  function submitSearch(event: React.FormEvent) {
    event.preventDefault();
    update("q", searchInput.trim());
  }

  function clear() {
    setSearchInput("");
    setParams({}, { replace: true });
  }

  if (loading) return <PageSkeleton cards={4} />;
  if (error) return <ErrorState message={error} onRetry={() => reload().catch(() => undefined)} />;

  return (
    <>
      <PageHeader eyebrow="AGENDA MULTIESPORTIVA" title="Eventos" description="Explore partidas de esportes e eSports, compare mercados e registre sua leitura antes do fechamento." />

      <section className="event-toolbar surface">
        <form className="event-search" onSubmit={submitSearch}>
          <Search size={17} />
          <input value={searchInput} onChange={(event) => setSearchInput(event.target.value)} placeholder="Buscar por equipe, campeonato ou modalidade" aria-label="Buscar eventos" />
          <Button size="sm" type="submit">Buscar</Button>
        </form>
        <label className="select-field"><Trophy size={16} /><select value={sport} onChange={(event) => update("sport", event.target.value)} aria-label="Filtrar modalidade"><option value="">Todas as modalidades</option>{(data?.sports || []).map((item: Sport) => <option value={item.slug || item.id} key={item.id}>{item.name}</option>)}</select><ChevronDown size={15} /></label>
        <Button variant="secondary" size="sm" aria-pressed={featured} onClick={() => update("featured", featured ? "" : "true")}><SlidersHorizontal size={16} /> {featured ? "Mostrar todos" : "Só destaques"}</Button>
      </section>

      <div className="filter-tabs" role="group" aria-label="Filtrar eventos por status">
        {statuses.map((item) => <button type="button" aria-pressed={status === item.value} className={status === item.value ? "active" : ""} onClick={() => update("status", item.value)} key={item.value}>{item.value === "LIVE" && <Radio size={13} aria-hidden="true" />}{item.label}</button>)}
      </div>

      <div className="results-summary"><span><Filter size={15} /> {events.length} {events.length === 1 ? "evento encontrado" : "eventos encontrados"}</span>{(search || status || sport || featured) && <button type="button" onClick={clear}>Limpar filtros</button>}</div>

      {events.length ? <div className="events-grid">{events.map((event) => <EventCard event={event} onPredict={setDraft} key={event.id} />)}</div> : <NoResults onClear={clear} />}

      <div className="virtual-footer-note"><ShieldCheck size={15} /> Coeficientes calculam recompensas apenas em pontos virtuais.</div>
      <PredictionComposer draft={draft} currentEvent={data?.events.find((item) => item.id === draft?.event.id) ?? null} onClose={() => setDraft(null)} onCreated={() => reload().catch(() => undefined)} />
    </>
  );
}

export function LiveEventsPage() {
  const [draft, setDraft] = useState<PredictionDraft | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState(() => new Date());
  const [refreshing, setRefreshing] = useState(false);
  const { notify } = useToast();
  const { data, loading, error, reload, refresh } = useApiResource(async () => asList(await eventsApi.live()), []);

  const refreshLive = useCallback(async (manual = false) => {
    if (document.visibilityState === "hidden") return;
    setRefreshing(true);
    try {
      await refresh();
      setLastUpdatedAt(new Date());
      if (manual) notify("Central ao vivo atualizada.", "success");
    } catch (reason) {
      if (manual) notify(reason instanceof Error ? reason.message : "Não foi possível atualizar os eventos ao vivo.", "error");
      throw reason;
    } finally {
      setRefreshing(false);
    }
  }, [notify, refresh]);

  useEffect(() => {
    const interval = window.setInterval(() => refreshLive().catch(() => undefined), 30_000);
    const onVisibility = () => document.visibilityState === "visible" && refreshLive().catch(() => undefined);
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      window.clearInterval(interval);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [refreshLive]);

  if (loading) return <PageSkeleton cards={3} />;
  if (error) return <ErrorState message={error} onRetry={() => reload().catch(() => undefined)} />;
  const events = data || [];

  return (
    <>
      <PageHeader
        eyebrow="CENTRAL AO VIVO"
        title="A Arena está em movimento"
        description="Acompanhe placares, períodos, mapas e mercados em atualização."
        actions={(
          <div className="live-refresh" aria-live="polite">
            <span><span className="live-pulse"><i /> Atualização automática a cada 30 s</span><small><Wifi size={13} /> Última leitura {dateTime(lastUpdatedAt.toISOString())}</small></span>
            <Button variant="secondary" size="sm" loading={refreshing} onClick={() => refreshLive(true).catch(() => undefined)}><RefreshCcw size={15} /> Atualizar agora</Button>
          </div>
        )}
      />
      {events.length ? (
        <div className="live-layout">
          <div className="event-list">{events.map((event) => <LiveEventPanel event={event} onPredict={setDraft} key={event.id} />)}</div>
          <aside className="surface live-side-info">
            <span><Activity size={22} /></span>
            <h2>Como funciona</h2>
            <p>Algumas opções continuam disponíveis durante o evento; outras fecham conforme a disputa avança.</p>
            <ul><li>Mercados podem ser suspensos em momentos críticos.</li><li>Abertura e fechamento são controlados pelo servidor.</li><li>Resultados processam recompensas uma única vez.</li></ul>
            <div><ShieldCheck size={16} /> Ambiente de entretenimento com pontos virtuais.</div>
          </aside>
        </div>
      ) : (
        <EmptyState icon={Activity} title="A arena está em intervalo" description="Não há eventos ao vivo agora. A agenda continua disponível para seus próximos palpites." />
      )}
      <PredictionComposer draft={draft} currentEvent={events.find((item) => item.id === draft?.event.id) ?? null} onClose={() => setDraft(null)} onCreated={() => reload().catch(() => undefined)} />
    </>
  );
}

function LiveEventPanel({ event, onPredict }: { event: ArenaEvent; onPredict: (draft: PredictionDraft) => void }) {
  const [home, away] = eventTeams(event);
  const multiParticipant = isMultiParticipantEvent(event);
  const previewMarkets = [...(event.markets || [])].sort((left, right) => Number(Boolean(right.availability?.allowed)) - Number(Boolean(left.availability?.allowed))).slice(0, 2);
  return (
    <article className="surface live-event-panel">
      <header><div><span className="sport-chip">{sportName(event.sport || event.sportName)}</span><strong>{multiParticipant ? event.title || enumLabel(event.format) : championshipName(event.championship || event.championshipName)}</strong>{multiParticipant && <small>{championshipName(event.championship || event.championshipName)}</small>}</div><span className="live-pulse"><i /> {event.liveClock || event.clock || event.period || "Ao vivo"}</span></header>
      {multiParticipant ? <ParticipantList event={event} limit={12} /> : <div className="live-scoreboard">
        <div><TeamLogo name={home.name || home.code} code={home.code} logoUrl={home.logoUrl || home.imageUrl} size="md" /><strong>{home.name || home.code}</strong></div>
        <b>{home.score ?? 0}<i>:</i>{away.score ?? 0}<small>{enumLabel(event.format || event.phase || "LIVE")}</small></b>
        <div><TeamLogo name={away.name || away.code} code={away.code} logoUrl={away.logoUrl || away.imageUrl} size="md" /><strong>{away.name || away.code}</strong></div>
      </div>}
      {event.statistics && <div className="live-stats">{Object.entries(event.statistics).slice(0, 4).map(([label, value]) => <div key={label}><small>{label}</small><strong>{value}</strong></div>)}</div>}
      {previewMarkets.length ? <MarketList event={event} markets={previewMarkets} onPredict={onPredict} /> : <StatusBadge status="closed" label="Mercados ainda não publicados" />}
      <footer className="live-event-panel__markets"><span>{event.predictionAvailabilityLabel || "Consulte a disponibilidade nos mercados"}</span><Link to={`/events/${event.id}`}>Explorar todos ({event.markets?.length || 0})</Link></footer>
    </article>
  );
}

export function EventDetailsPage() {
  const { id = "" } = useParams();
  const [draft, setDraft] = useState<PredictionDraft | null>(null);
  const { data: event, loading, error, reload, refresh } = useApiResource(() => eventsApi.get(id), [id]);
  useEffect(() => { const timer = window.setInterval(() => { if (!document.hidden) refresh().catch(() => undefined); }, 30000); return () => window.clearInterval(timer); }, [refresh]);
  if (loading) return <PageSkeleton cards={3} />;
  if (error || !event) return <ErrorState message={error || "Evento não encontrado."} onRetry={() => reload().catch(() => undefined)} />;
  const [home, away] = eventTeams(event);
  const multiParticipant = isMultiParticipantEvent(event);
  const participantCount = event.participants?.length || event.competitors?.length || 0;
  const phaseIsDemo = /^demonstra(?:ção|cao)$/i.test(String(event.phase || ""));
  const broadcastLabel = /\b(?:demo|demonstra)/i.test(String(event.broadcast || "")) ? "Atualização interna" : event.broadcast;
  const eventTitle = multiParticipant
    ? event.title || `${championshipName(event.championship || event.championshipName)} · ${participantCount ? `${participantCount} participantes` : enumLabel(event.format)}`
    : `${home.name || home.code} × ${away.name || away.code}`;
  return (
    <>
      <PageHeader eyebrow={`${sportName(event.sport || event.sportName)} · ${championshipName(event.championship || event.championshipName)}`} title={eventTitle} description={multiParticipant ? "Acompanhe participantes, classificação e mercados disponíveis deste evento." : "Compare os mercados disponíveis e acompanhe todas as informações do evento."} actions={<StatusBadge status={event.status === "OPEN_FOR_PREDICTIONS" ? "SCHEDULED" : event.status} />} />
      <EventCard event={event} compact />
      <section className="event-detail-grid">
        <div>{multiParticipant && <section className="surface chart-panel"><h2>Participantes e classificação</h2><p>{["LIVE", "FINISHED"].includes(String(event.status).toUpperCase()) ? "Posições e marcas atualizadas para este evento." : "Lista confirmada pela organização para esta disputa."}</p><ParticipantList event={event} limit={100} /></section>}<h2>Mercados de previsão</h2>{event.markets?.length ? <CategorizedMarkets event={event} onPredict={setDraft} /> : <EmptyState icon={CalendarDays} title="Mercados ainda não publicados" description="A organização adicionará as opções antes do início do evento." />}</div>
        <aside className="surface event-info"><h2>Informações</h2><dl><div><dt>Local</dt><dd>{event.venue || "A definir"}</dd></div><div><dt>Transmissão</dt><dd>{broadcastLabel || "Consulte a programação oficial"}</dd></div><div><dt>Formato</dt><dd>{event.format ? enumLabel(event.format) : "Padrão da modalidade"}</dd></div>{multiParticipant && <div><dt>Participantes</dt><dd>{participantCount || "A definir"}</dd></div>}{!phaseIsDemo && <div><dt>Fase</dt><dd>{event.phase ? enumLabel(event.phase) : "Fase regular"}</dd></div>}</dl><div className="virtual-disclaimer"><ShieldCheck size={16} /> Todos os coeficientes calculam somente recompensas em pontos.</div></aside>
      </section>
      <PredictionComposer draft={draft} currentEvent={event} onClose={() => setDraft(null)} onCreated={() => reload().catch(() => undefined)} />
    </>
  );
}

export function groupEventMarkets(markets: PredictionMarket[]) {
  return markets.reduce<Array<{ name: string; markets: PredictionMarket[] }>>((groups, market) => {
    const name = market.category?.trim() || "Principais";
    const group = groups.find((item) => item.name === name);
    if (group) group.markets.push(market);
    else groups.push({ name, markets: [market] });
    return groups;
  }, []);
}

export function CategorizedMarkets({ event, onPredict }: { event: ArenaEvent; onPredict: (draft: PredictionDraft) => void }) {
  const groups = groupEventMarkets(event.markets || []);
  const [selectedCategory, setSelectedCategory] = useState("");
  const selected = groups.find((group) => group.name === selectedCategory) || groups[0];
  return <section className="event-markets" aria-label="Mercados por categoria">
    <div className="market-availability-summary" aria-live="polite"><strong>{event.predictionAvailabilityLabel || "Consulte os estados dos mercados"}</strong><span>{event.availableMarketCount ?? 0} mercados abertos · pontos exclusivamente virtuais</span></div>
    <div className="market-category-filters" role="group" aria-label="Selecionar categoria de mercado">
      {groups.map((group) => <button type="button" key={group.name} aria-pressed={selected?.name === group.name} aria-controls="selected-market-category" onClick={() => setSelectedCategory(group.name)}>{group.name}<span>{group.markets.length}</span></button>)}
    </div>
    {selected && <div id="selected-market-category" role="region" aria-label={`Mercados: ${selected.name}`}><MarketList event={event} markets={selected.markets} onPredict={onPredict} /></div>}
  </section>;
}
