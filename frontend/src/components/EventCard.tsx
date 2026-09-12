import { CalendarClock, CheckCircle2, ChevronDown, ChevronRight, Clock3, Info, MapPin, Radio, Sparkles, Users } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { championshipName, dateTime, eventStatusLabel, eventTeams, multiplier, sportName } from "../app/format";
import { enumLabel } from "../app/presentation";
import type { ArenaEvent, EventCompetitor, PredictionDraft, PredictionMarket } from "../types";
import { StatusBadge } from "./UI";
import { TeamLogo } from "./TeamLogo";

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
            <TeamLogo name={name} code={participant.competitor.code} logoUrl={participant.competitor.logoUrl || participant.competitor.imageUrl} size="sm" />
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
  showPreview = true,
}: {
  event: ArenaEvent;
  onPredict?: (draft: PredictionDraft) => void;
  compact?: boolean;
  showPreview?: boolean;
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
          {event.phase && !/^demonstra(?:ção|cao)$/i.test(String(event.phase)) && <small>{enumLabel(event.phase)}</small>}
        </div>
        <StatusBadge status={event.status === "OPEN_FOR_PREDICTIONS" ? "SCHEDULED" : event.status} label={eventStatusLabel(event.status === "OPEN_FOR_PREDICTIONS" ? "SCHEDULED" : event.status)} />
      </header>

      {multiParticipant ? (
        <ParticipantList event={event} compact limit={4} />
      ) : <div className="event-card__matchup">
        <div className="competitor competitor--home">
          <TeamLogo name={home.name || home.shortName || home.code} code={home.code} logoUrl={home.logoUrl || home.imageUrl} size="md" />
          <strong>{home.shortName || home.name || home.code}</strong>
        </div>
        <div className="match-center">
          {isLive ? (
            <>
              <span className="live-clock"><Radio size={13} /> {event.liveClock || event.clock || event.period || "Ao vivo"}</span>
              <b>{home.score ?? "0"}<i>:</i>{away.score ?? "0"}</b>
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
          <TeamLogo name={away.name || away.shortName || away.code} code={away.code} logoUrl={away.logoUrl || away.imageUrl} size="md" />
          <strong>{away.shortName || away.name || away.code}</strong>
        </div>
      </div>}

      {!compact && showPreview && primaryMarket && (
        <div className="market-preview">
          <div className="market-preview__title"><span>{marketDisplayName(primaryMarket)}</span><small>Multiplicador</small></div>
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
        <span className={predictionOpen ? "event-availability event-availability--open" : "event-availability"}><Clock3 size={14} /> {event.predictionAvailabilityLabel || "Mercados ainda não publicados"}</span>
        <Link to={`/events/${event.id}`}>{predictionOpen ? "Explorar opções" : "Ver detalhes"} <ChevronRight size={15} /></Link>
      </footer>
      {event.featured && <span className="featured-corner" title="Evento em destaque"><Sparkles size={13} /></span>}
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
        <StatusBadge status={event.status === "OPEN_FOR_PREDICTIONS" ? "SCHEDULED" : event.status} label={eventStatusLabel(event.status === "OPEN_FOR_PREDICTIONS" ? "SCHEDULED" : event.status)} />
      </header>
      <div className="featured-event__league">{championshipName(event.championship || event.championshipName)}{multiParticipant && event.title ? ` · ${event.title}` : event.phase ? ` · ${enumLabel(event.phase)}` : ""}</div>
      {multiParticipant ? <ParticipantList event={event} compact limit={4} /> : <div className="featured-event__teams">
        <div><TeamLogo name={home.name || home.code} code={home.code} logoUrl={home.logoUrl || home.imageUrl} size="lg" /><strong>{home.shortName || home.name || home.code}</strong></div>
        <b aria-label="versus">×</b>
        <div><TeamLogo name={away.name || away.code} code={away.code} logoUrl={away.logoUrl || away.imageUrl} size="lg" /><strong>{away.shortName || away.name || away.code}</strong></div>
      </div>}
      <footer>
        <span><MapPin size={14} /> {event.venue || dateTime(event.startsAt)}</span>
        <Link to={`/events/${event.id}`}>{isPredictionOpen(event) ? "Explorar opções" : "Ver detalhes"} <ChevronRight size={16} /></Link>
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
  const [selectedOptionId, setSelectedOptionId] = useState<string | null>(null);
  return (
    <div className="market-list">
      {markets.map((market) => {
        const disabled = !isMarketOpen(market);
        return (
          <section className="surface market-block" key={market.id}>
            <div className="market-block__head">
              <div><h3>{marketDisplayName(market)}</h3><p>{marketPrimarySummary(market)}</p></div>
              <StatusBadge status={marketAvailabilityStatus(market)} label={market.availability?.label || "Aguardando publicação"} tooltip={marketAvailabilityTooltip(market)} />
            </div>
            <div className="market-block__options">
              {market.options?.map((option) => (
                <button
                  type="button"
                  key={option.id}
                  disabled={disabled || !isOptionOpen(option)}
                  aria-pressed={selectedOptionId === String(option.id)}
                  onClick={() => {
                    setSelectedOptionId(String(option.id));
                    onPredict({ event, market, option });
                  }}
                >
                  <span>{option.label || option.name}{!isOptionOpen(option) && <small> · Opção suspensa</small>}</span>
                  {selectedOptionId === String(option.id) && <CheckCircle2 className="market-option__check" size={15} aria-hidden="true" />}
                  <strong>{multiplier(option.multiplier)}</strong>
                </button>
              ))}
            </div>
            <div className="market-block__secondary">
              <span>{enumLabel(market.timingMode || "PRE_MATCH_ONLY")}</span>
              {market.closesAt && <span>Fecha em {dateTime(market.closesAt)}</span>}
              <MarketRuleDisclosure market={market} />
            </div>
          </section>
        );
      })}
    </div>
  );
}

export function marketDisplayName(market: PredictionMarket) {
  const code = String(market.templateCode || market.code || "").toUpperCase();
  const normalized = market.name.toLocaleLowerCase("pt-BR");
  if (code === "WINNER" || normalized.startsWith("resultado da partida")) return "Pré-jogo · Resultado final";
  if (code === "LIVE_RESULT" || code === "AUTO_LIVE_RESULT" || normalized.startsWith("resultado ao vivo")) return "Ao vivo · Resultado final";
  return market.name;
}

export function marketPrimarySummary(market: PredictionMarket) {
  if (!market.availability?.allowed) {
    return market.availability?.reason || market.availability?.label || "Consulte o estado deste mercado.";
  }
  if (market.timingMode === "PRE_MATCH_ONLY") return "Disponível até o início do evento.";
  if (market.timingMode === "LIVE_ONLY") return "Disponível durante o evento, enquanto permanecer aberto.";
  return "Disponível antes e durante o evento, enquanto permanecer aberto.";
}

export function marketAvailabilityStatus(market: PredictionMarket) {
  if (market.availability?.allowed) return "OPEN";
  const context = `${market.status || ""} ${market.availability?.code || ""} ${market.availability?.label || ""} ${market.availability?.reason || ""}`.toLocaleLowerCase("pt-BR");
  if (/cancel|reembols/.test(context)) return "CANCELED";
  if (/suspens|paus/.test(context)) return "SUSPENDED";
  if (/process/.test(context)) return "PROCESSING";
  if (/\babre\b|aguardando|próximo período/.test(context)) return "PENDING";
  return "CLOSED";
}

export function marketAvailabilityTooltip(market: PredictionMarket) {
  const reason = market.availability?.reason?.trim();
  if (!reason) return undefined;
  if (/multiplicadores? demonstrativos?|pontos exclusivamente virtuais/i.test(reason)) {
    return market.availability?.allowed
      ? "Disponibilidade controlada pelo servidor."
      : marketPrimarySummary(market);
  }
  return reason;
}

export function MarketRuleDisclosure({ market }: { market: PredictionMarket }) {
  const availability = market.availability?.reason;
  const primarySummary = marketPrimarySummary(market);
  const availabilityDetail = availability && availability !== primarySummary && !/multiplicadores? demonstrativos?/i.test(availability)
    ? availability
    : `Estado atual: ${market.availability?.label || enumLabel(market.status)}.`;
  return (
    <details className="market-details">
      <summary><Info size={15} aria-hidden="true" /><span>Entenda o mercado</span><ChevronDown size={14} aria-hidden="true" /></summary>
      <dl>
        <div><dt>Como funciona</dt><dd>{market.settlementDescription || "Escolha uma opção válida antes do fechamento indicado."}</dd></div>
        <div><dt>Disponibilidade</dt><dd>{availabilityDetail}</dd></div>
        <div><dt>Fechamento</dt><dd>{market.closesAt ? dateTime(market.closesAt) : "Definido pelo estado do evento."}</dd></div>
        <div><dt>Empate ou cancelamento</dt><dd>Empates seguem a regra acima. Se o mercado for cancelado, os pontos utilizados são devolvidos.</dd></div>
      </dl>
    </details>
  );
}

export function isPredictionOpen(event: ArenaEvent) {
  return event.availableMarketCount != null
    ? event.availableMarketCount > 0
    : Boolean(event.markets?.some(isMarketOpen));
}

export function isMarketOpen(market: PredictionMarket) {
  return market.availability?.allowed === true;
}

function isOptionOpen(option: PredictionMarket["options"][number]) {
  return option.active !== false && !option.suspended;
}
