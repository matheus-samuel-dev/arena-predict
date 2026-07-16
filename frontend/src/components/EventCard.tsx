import { CalendarClock, ChevronRight, Clock3, MapPin, Radio, ShieldCheck, Sparkles } from "lucide-react";
import { Link } from "react-router-dom";
import { championshipName, dateTime, eventStatusLabel, eventTeams, multiplier, sportName } from "../app/format";
import type { ArenaEvent, PredictionDraft, PredictionMarket } from "../types";
import { StatusBadge } from "./UI";

function teamMark(name?: string) {
  return (name || "AP")
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

export function EventCard({
  event,
  onPredict,
  compact = false,
}: {
  event: ArenaEvent;
  onPredict?: (draft: PredictionDraft) => void;
  compact?: boolean;
}) {
  const [home, away] = eventTeams(event);
  const isLive = ["LIVE", "AO_VIVO"].includes(String(event.status).toUpperCase());
  const predictionOpen = isPredictionOpen(event);
  const primaryMarket = predictionOpen ? event.markets?.find(isMarketOpen) : undefined;

  return (
    <article className={`event-card ${compact ? "event-card--compact" : ""}`}>
      <header className="event-card__head">
        <div>
          <span className="sport-chip">{sportName(event.sport || event.sportName)}</span>
          <strong>{championshipName(event.championship || event.championshipName)}</strong>
          {event.phase && <small>{event.phase}</small>}
        </div>
        <StatusBadge status={event.status} label={eventStatusLabel(event.status)} />
      </header>

      <div className="event-card__matchup">
        <div className="competitor competitor--home">
          <span className="team-mark">{teamMark(home.name || home.shortName || home.code)}</span>
          <strong>{home.shortName || home.name || home.code}</strong>
        </div>
        <div className="match-center">
          {isLive ? (
            <>
              <span className="live-clock"><Radio size={13} /> {event.liveClock || event.clock || event.period || "AO VIVO"}</span>
              <b>{home.score ?? "0"}<i>:</i>{away.score ?? "0"}</b>
              {(event.demoLiveData || event.demo) && <small>dados demo</small>}
            </>
          ) : (
            <>
              <span><CalendarClock size={14} /> {dateTime(event.startsAt)}</span>
              <b className="versus">VS</b>
              <small>{event.format || event.venue || "Evento programado"}</small>
            </>
          )}
        </div>
        <div className="competitor competitor--away">
          <span className="team-mark team-mark--alt">{teamMark(away.name || away.shortName || away.code)}</span>
          <strong>{away.shortName || away.name || away.code}</strong>
        </div>
      </div>

      {!compact && primaryMarket && (
        <div className="market-preview">
          <div className="market-preview__title"><span>{primaryMarket.name}</span><small>Multiplicador simulado</small></div>
          <div className="market-options">
            {primaryMarket.options?.slice(0, 3).map((option) => (
              <button
                type="button"
                key={option.id}
                disabled={!onPredict || !isOptionOpen(option)}
                onClick={() => onPredict?.({ event, market: primaryMarket, option })}
                aria-label={`${option.label || option.name}, multiplicador ${multiplier(option.multiplier)}`}
              >
                <span>{option.label || option.name}</span><strong>{multiplier(option.multiplier)}</strong>
              </button>
            ))}
          </div>
        </div>
      )}

      <footer className="event-card__foot">
        <span><Clock3 size={14} /> Palpites até {dateTime(event.predictionDeadline || event.predictionClosesAt || event.startsAt)}</span>
        <Link to={`/events/${event.id}`}>Detalhes <ChevronRight size={15} /></Link>
      </footer>
      {event.featured && <span className="featured-corner" title="Evento em destaque"><Sparkles size={13} /></span>}
      {isLive && (event.demoLiveData || event.demo) && <span className="demo-live-label"><ShieldCheck size={12} /> Simulação</span>}
    </article>
  );
}

export function FeaturedEventCard({ event, onPredict }: { event: ArenaEvent; onPredict: (draft: PredictionDraft) => void }) {
  const [home, away] = eventTeams(event);
  const market = isPredictionOpen(event) ? event.markets?.find(isMarketOpen) : undefined;
  const option = market?.options?.find(isOptionOpen);
  return (
    <article className="featured-event">
      <div className="featured-event__ambient" />
      <header>
        <span>{sportName(event.sport || event.sportName)}</span>
        <StatusBadge status={event.status} label={eventStatusLabel(event.status)} />
      </header>
      <div className="featured-event__league">{championshipName(event.championship || event.championshipName)}{event.phase ? ` · ${event.phase}` : ""}</div>
      <div className="featured-event__teams">
        <div><span className="team-mark">{teamMark(home.name || home.code)}</span><strong>{home.shortName || home.name || home.code}</strong></div>
        <b>VS</b>
        <div><span className="team-mark team-mark--alt">{teamMark(away.name || away.code)}</span><strong>{away.shortName || away.name || away.code}</strong></div>
      </div>
      <footer>
        <span><MapPin size={14} /> {event.venue || dateTime(event.startsAt)}</span>
        {market && option ? (
          <button type="button" onClick={() => onPredict({ event, market, option })}>Fazer palpite <ChevronRight size={16} /></button>
        ) : (
          <Link to={`/events/${event.id}`}>Ver mercados <ChevronRight size={16} /></Link>
        )}
      </footer>
    </article>
  );
}

export function MarketList({
  event,
  markets,
  onPredict,
}: {
  event: ArenaEvent;
  markets: PredictionMarket[];
  onPredict: (draft: PredictionDraft) => void;
}) {
  return (
    <div className="market-list">
      {markets.map((market) => {
        const disabled = !isPredictionOpen(event) || !isMarketOpen(market);
        return (
          <section className="surface market-block" key={market.id}>
            <div className="market-block__head">
              <div><h3>{market.name}</h3><p>Escolha uma opção. O coeficiente calcula somente pontos virtuais.</p></div>
              {disabled && <StatusBadge status="closed" label="Indisponível" />}
            </div>
            <div className="market-block__options">
              {market.options?.map((option) => (
                <button type="button" key={option.id} disabled={disabled || !isOptionOpen(option)} onClick={() => onPredict({ event, market, option })}>
                  <span>{option.label || option.name}</span><strong>{multiplier(option.multiplier)}</strong>
                </button>
              ))}
            </div>
          </section>
        );
      })}
    </div>
  );
}

export function isPredictionOpen(event: ArenaEvent) {
  if (String(event.status).toUpperCase() !== "OPEN_FOR_PREDICTIONS") return false;
  const closesAt = new Date(event.predictionClosesAt || event.predictionDeadline || event.startsAt).getTime();
  return Number.isFinite(closesAt) && closesAt > Date.now();
}

function isMarketOpen(market: PredictionMarket) {
  return String(market.status).toUpperCase() === "OPEN";
}

function isOptionOpen(option: PredictionMarket["options"][number]) {
  return option.active !== false && !option.suspended;
}
