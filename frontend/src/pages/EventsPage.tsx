import { Activity, CalendarDays, ChevronDown, Filter, Radio, Search, ShieldCheck, SlidersHorizontal, Trophy } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { championshipName, eventTeams, sportName } from "../app/format";
import { EventCard, MarketList } from "../components/EventCard";
import { PredictionComposer } from "../components/PredictionComposer";
import { Button, EmptyState, ErrorState, NoResults, PageHeader, PageSkeleton, StatusBadge } from "../components/UI";
import { useApiResource } from "../hooks/useApiResource";
import { asList, catalogApi, eventsApi } from "../services/api";
import type { ArenaEvent, PredictionDraft, Sport } from "../types";

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
    return [home.name, away.name, home.code, away.code, sportName(event.sport || event.sportName), championshipName(event.championship || event.championshipName), event.title]
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
    async () => ({
      events: asList(await eventsApi.list({ status: status || undefined, sport: sport || undefined, featured: featured || undefined, size: 48 })),
      sports: asList(await catalogApi.sports()),
    }),
    [status, sport, featured],
  );

  useEffect(() => setSearchInput(search), [search]);

  const events = useMemo(() => {
    return filterEventCatalog(data?.events || [], search, featured);
  }, [data?.events, search, featured]);

  function update(key: string, value: string) {
    const next = new URLSearchParams(params);
    value ? next.set(key, value) : next.delete(key);
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

      <div className="filter-tabs" role="tablist" aria-label="Status dos eventos">
        {statuses.map((item) => <button type="button" role="tab" aria-selected={status === item.value} className={status === item.value ? "active" : ""} onClick={() => update("status", item.value)} key={item.value}>{item.value === "LIVE" && <Radio size={13} />}{item.label}</button>)}
      </div>

      <div className="results-summary"><span><Filter size={15} /> {events.length} {events.length === 1 ? "evento encontrado" : "eventos encontrados"}</span>{(search || status || sport || featured) && <button type="button" onClick={clear}>Limpar filtros</button>}</div>

      {events.length ? <div className="events-grid">{events.map((event) => <EventCard event={event} onPredict={setDraft} key={event.id} />)}</div> : <NoResults onClear={clear} />}

      <div className="virtual-footer-note"><ShieldCheck size={15} /> Multiplicadores são coeficientes simulados para pontos virtuais, nunca odds financeiras.</div>
      <PredictionComposer draft={draft} onClose={() => setDraft(null)} onCreated={() => reload().catch(() => undefined)} />
    </>
  );
}

export function LiveEventsPage() {
  const [draft, setDraft] = useState<PredictionDraft | null>(null);
  const { data, loading, error, reload, refresh } = useApiResource(async () => asList(await eventsApi.live()), []);

  useEffect(() => {
    const interval = window.setInterval(() => refresh().catch(() => undefined), 30_000);
    return () => window.clearInterval(interval);
  }, [refresh]);

  if (loading) return <PageSkeleton cards={3} />;
  if (error) return <ErrorState message={error} onRetry={() => reload().catch(() => undefined)} />;
  const events = data || [];

  return (
    <>
      <PageHeader
        eyebrow="CENTRAL AO VIVO"
        title="A Arena está em movimento"
        description="Acompanhe placares, períodos, mapas e mercados ativos. Dados simulados são sempre identificados."
        actions={<span className="live-pulse"><i /> Atualização a cada 30s</span>}
      />
      {events.length ? (
        <div className="live-layout">
          <div className="event-list">{events.map((event) => <LiveEventPanel event={event} onPredict={setDraft} key={event.id} />)}</div>
          <aside className="surface live-side-info">
            <span><Activity size={22} /></span>
            <h2>Como funciona</h2>
            <p>O provider interno atualiza placares de demonstração sem sugerir integração externa inexistente.</p>
            <ul><li>Mercados podem ser suspensos durante o evento.</li><li>O horário de fechamento é validado no servidor.</li><li>Resultados oficiais processam recompensas uma única vez.</li></ul>
            <div><ShieldCheck size={16} /> Ambiente de entretenimento com pontos virtuais.</div>
          </aside>
        </div>
      ) : (
        <EmptyState icon={Activity} title="A arena está em intervalo" description="Não há eventos ao vivo agora. A agenda continua disponível para seus próximos palpites." />
      )}
      <PredictionComposer draft={draft} onClose={() => setDraft(null)} onCreated={() => reload().catch(() => undefined)} />
    </>
  );
}

function LiveEventPanel({ event, onPredict }: { event: ArenaEvent; onPredict: (draft: PredictionDraft) => void }) {
  const [home, away] = eventTeams(event);
  return (
    <article className="surface live-event-panel">
      <header><div><span className="sport-chip">{sportName(event.sport || event.sportName)}</span><strong>{championshipName(event.championship || event.championshipName)}</strong></div><span className="live-pulse"><i /> {event.liveClock || event.clock || event.period || "AO VIVO"}</span></header>
      <div className="live-scoreboard">
        <div><span className="team-mark">{String(home.name || home.code || "AP").slice(0, 2).toUpperCase()}</span><strong>{home.name || home.code}</strong></div>
        <b>{home.score ?? 0}<i>:</i>{away.score ?? 0}<small>{event.format || event.phase || "Em andamento"}</small></b>
        <div><span className="team-mark team-mark--alt">{String(away.name || away.code || "AP").slice(0, 2).toUpperCase()}</span><strong>{away.name || away.code}</strong></div>
      </div>
      {event.statistics && <div className="live-stats">{Object.entries(event.statistics).slice(0, 4).map(([label, value]) => <div key={label}><small>{label}</small><strong>{value}</strong></div>)}</div>}
      {(event.demoLiveData || event.demo) && <div className="demo-data-note"><ShieldCheck size={14} /> Placar atualizado pelo provider interno de demonstração.</div>}
      {event.markets?.length ? <MarketList event={event} markets={event.markets.slice(0, 2)} onPredict={onPredict} /> : <StatusBadge status="suspended" label="Mercados temporariamente suspensos" />}
    </article>
  );
}

export function EventDetailsPage() {
  const { id = "" } = useParams();
  const [draft, setDraft] = useState<PredictionDraft | null>(null);
  const { data: event, loading, error, reload } = useApiResource(() => eventsApi.get(id), [id]);
  if (loading) return <PageSkeleton cards={3} />;
  if (error || !event) return <ErrorState message={error || "Evento não encontrado."} onRetry={() => reload().catch(() => undefined)} />;
  const [home, away] = eventTeams(event);
  return (
    <>
      <PageHeader eyebrow={`${sportName(event.sport || event.sportName)} · ${championshipName(event.championship || event.championshipName)}`} title={`${home.name || home.code} vs ${away.name || away.code}`} description="Compare os mercados disponíveis e acompanhe todas as informações do evento." actions={<StatusBadge status={event.status} />} />
      <EventCard event={event} onPredict={setDraft} />
      <section className="event-detail-grid">
        <div><h2>Mercados de previsão</h2>{event.markets?.length ? <MarketList event={event} markets={event.markets} onPredict={setDraft} /> : <EmptyState icon={CalendarDays} title="Mercados ainda não publicados" description="A organização adicionará as opções antes do início do evento." />}</div>
        <aside className="surface event-info"><h2>Informações</h2><dl><div><dt>Local</dt><dd>{event.venue || "A definir"}</dd></div><div><dt>Transmissão</dt><dd>{event.broadcast || "Consulte a programação oficial"}</dd></div><div><dt>Formato</dt><dd>{event.format || "Padrão da modalidade"}</dd></div><div><dt>Fase</dt><dd>{event.phase || "Fase regular"}</dd></div></dl><div className="virtual-disclaimer"><ShieldCheck size={16} /> Todos os coeficientes calculam somente recompensas em pontos.</div></aside>
      </section>
      <PredictionComposer draft={draft} onClose={() => setDraft(null)} onCreated={() => reload().catch(() => undefined)} />
    </>
  );
}
