import { eventTeams } from "./format";
import { eventScore } from "./sportsData";
import type { ArenaEvent, PredictionMarket } from "../types";

/** Presentation follows backend evidence; never estimates probabilities in the browser. */
export function pricingSummary(market: PredictionMarket) {
  if (!market.pricing) return market.pricingReason;
  if (market.pricingMode === "STATIC") return "Valores registrados no fechamento do mercado.";
  if (!market.pricing.available) return "Aguardando dados atuais para calcular este mercado com segurança.";
  if (market.pricing.evidenceSource === "PANDASCORE_CONFIRMED_RESULTS") {
    return "Estimativa baseada nos resultados recentes das equipes.";
  }
  if (market.pricing.evidenceSource === "SYMMETRIC_PRIOR") {
    return "Histórico insuficiente para apontar um favorito. Referência equilibrada.";
  }
  return "Estimativa de pontos baseada nos dados disponíveis.";
}

export function seriesProgressSummary(event: ArenaEvent) {
  if (event.status !== "LIVE" || !event.liveScoreAvailable || !event.bestOf
      || ![1, 3, 5].includes(event.bestOf) || event.homeScore == null || event.awayScore == null) return undefined;
  const score = eventScore(event);
  if (!score) return undefined;
  const homeScore = Number(score[0]), awayScore = Number(score[1]);
  if (!Number.isInteger(homeScore) || !Number.isInteger(awayScore) || homeScore < 0 || awayScore < 0) return undefined;
  const [home, away] = eventTeams(event);
  const target = Math.floor(event.bestOf / 2) + 1;
  const leadingScore = Math.max(homeScore, awayScore);
  if (homeScore === awayScore || leadingScore >= target) return undefined;
  const leader = homeScore > awayScore ? home : away;
  const remaining = target - leadingScore;
  return `${leader.name || leader.code} lidera a série e precisa de ${remaining} ${remaining === 1 ? "vitória" : "vitórias"} para encerrá-la.`;
}
