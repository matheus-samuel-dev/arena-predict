import { CalendarClock, ChevronRight, Clock3, MapPin, Radio, ShieldCheck, Sparkles, Users } from "lucide-react";
import { Link } from "react-router-dom";
import { championshipName, dateTime, eventStatusLabel, eventTeams, multiplier, sportName } from "../app/format";
import { enumLabel } from "../app/presentation";
import type { ArenaEvent, EventCompetitor, PredictionDraft, PredictionMarket } from "../types";
import { StatusBadge } from "./UI";

function teamMark(name?: string) {
  return (name || "AP")
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

export interface EventParticipantView {
  id: number | string;
  competitor: EventCompetitor;
  displayOrder: number;
  position?: number | null;
  scoreLabel?: string | number | null;
}

export function eventParticipantViews(event: ArenaEvent): EventParticipantView[] {
  const declared = (event.participants || []).map((participant, index) => ({
    id: participant.id ?? participant.competitor.id ?? `${event.id}-participant-${index}`,
    competitor: participant.competitor,
    displayOrder: participant.displayOrder ?? index,
    position: participant.position,
    scoreLabel: participant.scoreLabel ?? participant.competitor.score,
  }));
  const competitors = declared.length ? declared : (event.competitors || []).map((competitor, index) => ({
    id: competitor.id ?? `${event.id}-competitor-${index}`,
    competitor,
    displayOrder: index,
    position: null,
    scoreLabel: competitor.score,
  }));
  if (competitors.length) {
    return competitors.sort((left, right) => {
      if (left.position != null && right.position != null) return left.position - right.position;
      if (left.position != null) return -1;
      if (right.position != null) return 1;
      return left.displayOrder - right.displayOrder;
    });
  }

  const [home, away] = eventTeams(event);
  return [home, away]
    .filter((competitor) => competitor.name || competitor.code)
    .map((competitor, index) => ({
      id: `${event.id}-pair-${index}`,
      competitor: { ...competitor, name: competitor.name || competitor.code || `Participante ${index + 1}` },
      displayOrder: index,
      position: null,
      scoreLabel: competitor.score,
    }));
}

export function isMultiParticipantEvent(event: ArenaEvent) {
  const format = String(event.format || "").toUpperCase();
  return ["INDIVIDUAL", "RACE"].includes(format) || eventParticipantViews(event).length > 2;
}

export function ParticipantList({ event, compact = false, limit = 6 }: { event: ArenaEvent; compact?: boolean; limit?: number }) {
  const participants = eventParticipantViews(event);
  const visible = participants.slice(0, compact ? Math.min(limit, 4) : limit);
  return (
    <div className={`mini-ranking event-participant-list ${compact ? "event-participant-list--compact" : ""}`} role="list" aria-label="Participantes e classificação">
      {visible.map((participant, index) => {
        const name = participant.competitor.name || participant.competitor.code || `Participante ${index + 1}`;
        return (
          <div role="listitem" key={participant.id}>
            <b>{participant.position != null ? `#${participant.position}` : index + 1}</b>
            <span className="team-mark">{teamMark(name)}</span>
            <span><strong>{name}</strong><small>{participant.competitor.code && participant.competitor.code !== name ? participant.competitor.code : "Participante confirmado"}</small></span>
            <em>{participant.scoreLabel != null && participant.scoreLabel !== "" ? participant.scoreLabel : "—"}</em>
          </div>
        );
      })}
      {participants.length > visible.length && <small className="event-participant-list__more"><Users size={14} /> +{participants.length - visible.length} participantes no detalhe</small>}
    </div>
  );
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
  const multiParticipant = isMultiParticipantEvent(event);
  const predictionOpen = isPredictionOpen(event);
  const primaryMarket = predictionOpen ? event.markets?.find(isMarketOpen) : undefined;

  return (
    <article className={`event-card ${compact ? "event-card--compact" : ""}`}>
      <header className="event-card__head">
        <div>
          <span className="sport-chip">{sportName(event.sport || event.sportName)}</span>
          <strong>{championshipName(event.championship || event.championshipName)}</strong>
          {multiParticipant && event.title && <small>{event.title}</small>}
          {event.phase && <small>{enumLabel(event.phase)}</small>}
        </div>
        <StatusBadge status={event.status} label={eventStatusLabel(event.status)} />
      </header>

      {multiParticipant ? (
        <ParticipantList event={event} compact={compact} />
      ) : <div className="event-card__matchup">
        <div className="competitor competitor--home">
          <span className="team-mark">{teamMark(home.name || home.shortName || home.code)}</span>
          <strong>{home.shortName || home.name || home.code}</strong>
        </div>
        <div className="match-center">
          {isLive ? (
            <>
              <span className="live-clock"><Radio size={13} /> {event.liveClock || event.clock || event.period || "Ao vivo"}</span>
              <b>{home.score ?? "0"}<i>:</i>{away.score ?? "0"}</b>
              {(event.demoLiveData || event.demo) && <small>dados demonstrativos</small>}
            </>
          ) : (
            <>
              <span><CalendarClock size={14} /> {dateTime(event.startsAt)}</span>
              <b className="versus" aria-label="versus">×</b>
              <small>{event.format ? enumLabel(event.format) : event.venue || "Evento programado"}</small>
            </>
          )}
        </div>
        <div className="competitor competitor--away">
          <span className="team-mark team-mark--alt">{teamMark(away.name || away.shortName || away.code)}</span>
          <strong>{away.shortName || away.name || away.code}</strong>
        </div>
      </div>}

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

export function FeaturedEventCard({ event }: { event: ArenaEvent }) {
  const [home, away] = eventTeams(event);
  const multiParticipant = isMultiParticipantEvent(event);
  return (
    <article className="featured-event">
      <div className="featured-event__ambient" />
      <header>
        <span>{sportName(event.sport || event.sportName)}</span>
        <StatusBadge status={event.status} label={eventStatusLabel(event.status)} />
      </header>
      <div className="featured-event__league">{championshipName(event.championship || event.championshipName)}{multiParticipant && event.title ? ` · ${event.title}` : event.phase ? ` · ${enumLabel(event.phase)}` : ""}</div>
      {multiParticipant ? <ParticipantList event={event} compact limit={4} /> : <div className="featured-event__teams">
        <div><span className="team-mark">{teamMark(home.name || home.code)}</span><strong>{home.shortName || home.name || home.code}</strong></div>
        <b aria-label="versus">×</b>
        <div><span className="team-mark team-mark--alt">{teamMark(away.name || away.code)}</span><strong>{away.shortName || away.name || away.code}</strong></div>
      </div>}
      <footer>
        <span><MapPin size={14} /> {event.venue || dateTime(event.startsAt)}</span>
        <Link to={`/events/${event.id}`}>{isPredictionOpen(event) ? "Escolher uma opção" : "Ver detalhes"} <ChevronRight size={16} /></Link>
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
