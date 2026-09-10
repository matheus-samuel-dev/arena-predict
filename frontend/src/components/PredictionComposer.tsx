import { CheckCircle2, Coins, ShieldCheck, Sparkles, Target, Trophy } from "lucide-react";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { championshipName, eventTeams, getWalletBalance, multiplier, points } from "../app/format";
import { useAppData } from "../contexts/AppDataContext";
import { useToast } from "../contexts/ToastContext";
import { createIdempotencyKey, poolsApi, predictionsApi } from "../services/api";
import type { ArenaEvent, Pool, Prediction, PredictionDraft } from "../types";
import { eventParticipantViews, isMultiParticipantEvent } from "./EventCard";
import { Button, Modal } from "./UI";

const MINIMUM_POINTS = 10;
export const MAXIMUM_STAKE_POINTS = 20_000;
export function calculatePotentialPoints(stake: number, coefficient: number) {
  // The API supports three multiplier decimals. Integer arithmetic matches BigDecimal flooring.
  return Math.floor(stake * Math.round(coefficient * 1000) / 1000);
}

export function resolveMarketMinimumPoints(value: unknown) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? Math.ceil(parsed) : MINIMUM_POINTS;
}

export function quickStakeOptions(minimumPoints: number) {
  return Array.from(new Set([
    minimumPoints,
    Math.max(50, minimumPoints * 2),
    Math.max(100, minimumPoints * 5),
  ])).map(Math.ceil);
}

export function validatePredictionStake(stake: number, minimumPoints: number, balance: number) {
  if (!Number.isFinite(stake) || !Number.isInteger(stake)) return "Use uma quantidade inteira de pontos.";
  if (minimumPoints > MAXIMUM_STAKE_POINTS) return "Este mercado possui um limite mínimo inválido e está indisponível para palpites.";
  if (stake < minimumPoints) return `O mínimo deste mercado é ${minimumPoints} pontos.`;
  if (stake > MAXIMUM_STAKE_POINTS) return `O máximo por palpite é ${points(MAXIMUM_STAKE_POINTS)} pontos.`;
  if (stake > balance) return "Seu saldo de pontos é insuficiente.";
  return null;
}

export function PredictionComposer({
  draft,
  currentEvent,
  onClose,
  onCreated,
}: {
  draft: PredictionDraft | null;
  currentEvent?: ArenaEvent | null;
  onClose: () => void;
  onCreated?: (prediction: Prediction) => void;
}) {
  const [stake, setStake] = useState(MINIMUM_POINTS);
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const [submitError, setSubmitError] = useState("");
  const [confirmed, setConfirmed] = useState<Prediction | null>(null);
  const [availablePools, setAvailablePools] = useState<Pool[]>([]);
  const [poolId, setPoolId] = useState("");
  const [poolsLoading, setPoolsLoading] = useState(false);
  const [poolsError, setPoolsError] = useState("");
  const idempotencyKey = useRef(createIdempotencyKey());
  const selectionKey = draft ? `${draft.event.id}:${draft.market.id}:${draft.option.id}` : "";
  const currentMarket = currentEvent !== undefined ? currentEvent?.markets?.find((market) => market.id === draft?.market.id) : draft?.market;
  const currentOption = currentEvent !== undefined ? currentMarket?.options.find((option) => option.id === draft?.option.id) : draft?.option;
  const selectionAllowed = currentMarket?.availability?.allowed === true && Boolean(currentOption) && currentOption?.active !== false;
  const { wallet, refreshWallet, refreshNotifications } = useAppData();
  const { notify } = useToast();
  const balance = getWalletBalance(wallet);
  const minimumPoints = resolveMarketMinimumPoints(currentMarket?.minimumPoints);
  const maximumSelectable = Math.min(Math.max(0, balance), MAXIMUM_STAKE_POINTS);
  const rawCoefficient = Number(currentOption?.multiplier || 0);
  const coefficient = Number.isFinite(rawCoefficient) ? rawCoefficient : 0;
  const potential = calculatePotentialPoints(stake, coefficient);
  const [home, away] = draft ? eventTeams(draft.event) : [{ name: "" }, { name: "" }];
  const matchupLabel = draft && isMultiParticipantEvent(draft.event)
    ? draft.event.title || `${eventParticipantViews(draft.event).length} participantes`
    : `${home.name || home.shortName} × ${away.name || away.shortName}`;

  useEffect(() => {
    if (draft) {
      setConfirmed(null);
      setPoolId("");
      setSubmitError("");
      submittingRef.current = false;
      idempotencyKey.current = createIdempotencyKey();
    }
  }, [selectionKey]);

  useEffect(() => {
    if (!draft) {
      setAvailablePools([]);
      setPoolsError("");
      return undefined;
    }
    let active = true;
    setPoolsLoading(true);
    setPoolsError("");
    poolsApi.list()
      .then((values) => {
        if (active) setAvailablePools(values.filter((pool) => pool.poolType !== "LEAGUE" && pool.joined && poolAcceptsEvent(pool, draft.event)));
      })
      .catch(() => {
        if (active) {
          setAvailablePools([]);
          setPoolsError("Não foi possível carregar seus bolões agora.");
        }
      })
      .finally(() => active && setPoolsLoading(false));
    return () => { active = false; };
  }, [draft?.event.id]);

  useEffect(() => {
    if (draft && !confirmed) {
      setStake(Math.min(Math.max(minimumPoints, Math.floor(balance * 0.05)), Math.max(maximumSelectable, minimumPoints)));
    }
  }, [selectionKey, balance, minimumPoints, maximumSelectable, confirmed]);

  const validation = useMemo(
    () => validatePredictionStake(stake, minimumPoints, balance),
    [stake, balance, minimumPoints],
  );

  function closeComposer() {
    if (submittingRef.current) return;
    const created = confirmed;
    onClose();
    // Refresh the parent only after the success receipt has been acknowledged.
    // Reloading immediately would unmount this dialog, hide the confirmation
    // and create a fresh idempotency key while the same action remained visible.
    if (created) onCreated?.(created);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!draft || validation || submittingRef.current || !selectionAllowed) return;
    submittingRef.current = true;
    setSubmitting(true);
    setSubmitError("");
    try {
      const created = await predictionsApi.create({
        eventId: draft.event.id,
        marketId: draft.market.id,
        optionId: draft.option.id,
        stakePoints: stake,
        poolId: poolId ? Number(poolId) : undefined,
        // A retry after a network failure is the same intent and must reuse
        // the key so it can never debit the virtual wallet twice.
        idempotencyKey: idempotencyKey.current,
      });
      setConfirmed(created);
      await Promise.allSettled([refreshWallet(), refreshNotifications()]);
      notify("Palpite confirmado e pontos debitados com segurança.", "success");
    } catch (error) {
      const message = error instanceof Error ? error.message : "Não foi possível confirmar o palpite.";
      setSubmitError(message);
      notify(message, "error");
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  return (
    <Modal open={Boolean(draft)} onClose={closeComposer} title={confirmed ? "Palpite confirmado" : "Confirmar palpite"} size="sm">
      {draft && !confirmed && (
        <form className="prediction-composer" onSubmit={submit}>
          <div className="prediction-composer__event">
            <span>{championshipName(draft.event.championship || draft.event.championshipName)}</span>
            <strong>{matchupLabel}</strong>
          </div>
          <div className="selection-summary">
            <span><Target size={18} /></span>
            <div><small>{draft.market.name}</small><strong>{draft.option.label || draft.option.name}</strong></div>
            <b title="Multiplicador demonstrativo">{multiplier(coefficient)}</b>
          </div>
          <p className="prediction-multiplier-note">Multiplicador demonstrativo · registrado junto ao seu palpite</p>
          <label className="stake-field" htmlFor="prediction-stake">
            <span>Pontos virtuais</span>
            <div><Coins size={18} /><input id="prediction-stake" aria-describedby="prediction-stake-help prediction-stake-error" aria-invalid={Boolean(validation)} disabled={submitting} type="number" min={minimumPoints} step="1" max={Math.max(maximumSelectable, minimumPoints)} value={stake} onChange={(event) => setStake(Number(event.target.value))} /><em>pts</em></div>
            <small>Mínimo do mercado: <strong>{points(minimumPoints)} pts</strong></small>
            <small>Máximo por palpite: <strong>{points(MAXIMUM_STAKE_POINTS)} pts</strong></small>
            <small id="prediction-stake-help">Saldo disponível: <strong>{points(balance)} pts</strong></small>
          </label>
          <label className="prediction-pool-field">
            <span>Vincular a um bolão <small>(opcional)</small></span>
            <div><Trophy size={17} /><select value={poolId} onChange={(event) => setPoolId(event.target.value)} disabled={poolsLoading || submitting}><option value="">Palpite individual</option>{availablePools.map((pool) => <option value={String(pool.id)} key={pool.id}>{pool.name}</option>)}</select></div>
            {poolsLoading && <small>Carregando seus grupos...</small>}
            {poolsError && <small className="field-error">{poolsError}</small>}
            {!poolsLoading && !poolsError && availablePools.length === 0 && <small>Entre em um bolão compatível para pontuar no ranking do grupo.</small>}
          </label>
          <div className="quick-stakes" aria-label="Valores rápidos">
            {quickStakeOptions(minimumPoints).map((value) => <button type="button" key={value} onClick={() => setStake(Math.min(value, balance, MAXIMUM_STAKE_POINTS))} disabled={submitting || balance < value || value > MAXIMUM_STAKE_POINTS}>{points(value)} pts</button>)}
            <button type="button" onClick={() => setStake(Math.min(balance, MAXIMUM_STAKE_POINTS, Math.max(minimumPoints, Math.floor(balance * 0.25))))} disabled={submitting || balance < minimumPoints}>25%</button>
          </div>
          <div id="prediction-stake-error">{validation && <p className="field-error" role="alert">{validation}</p>}</div>
          {!selectionAllowed && <p className="field-error" role="alert">{currentOption?.active === false ? "Esta opção está temporariamente suspensa." : !currentOption ? "A seleção não aparece na leitura atual. Atualize o evento e escolha uma opção." : currentMarket?.availability?.reason || currentMarket?.availability?.label || "Atualize os mercados para conferir a disponibilidade."}</p>}
          {submitError && <p className="field-error" role="alert">{submitError}</p>}
          <div className="potential-card">
            <span><Sparkles size={17} /> Potencial de pontos</span>
            <strong>{points(potential)} pts</strong>
            <small>{points(stake)} × {multiplier(coefficient)}</small>
          </div>
          <div className="virtual-disclaimer"><ShieldCheck size={16} /> Pontos sem valor financeiro. Nenhum dinheiro real é utilizado.</div>
          <div className="modal-actions">
            <Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>Cancelar</Button>
            <Button type="submit" loading={submitting} disabled={Boolean(validation) || !selectionAllowed}>Confirmar palpite</Button>
          </div>
        </form>
      )}
      {confirmed && (
        <div className="prediction-success">
          <span><CheckCircle2 size={34} /></span>
          <h3>Sua leitura está registrada</h3>
          <p>Acompanhe o evento e o processamento da recompensa em “Meus palpites”.</p>
          <dl className="prediction-receipt"><div><dt>Pontos debitados</dt><dd>{points(confirmed.stakePoints ?? confirmed.points ?? stake)} pts</dd></div><div><dt>Multiplicador registrado</dt><dd>{multiplier(confirmed.multiplier ?? coefficient)}</dd></div><div><dt>Palpite</dt><dd>#{confirmed.id}</dd></div></dl>
          <div><small>Potencial</small><strong>{points(confirmed.potentialPoints ?? potential)} pts</strong></div>
          <Button onClick={closeComposer}>Continuar na Arena</Button>
        </div>
      )}
    </Modal>
  );
}

function poolAcceptsEvent(pool: Pool, event: ArenaEvent) {
  const status = String(pool.status || "OPEN").toUpperCase();
  if (!["OPEN", "IN_PROGRESS"].includes(status)) return false;
  const poolSportId = entityId(pool.sport);
  const eventSportId = entityId(event.sport);
  if (poolSportId && eventSportId && poolSportId !== eventSportId) return false;
  const poolChampionshipId = entityId(pool.championship);
  const eventChampionshipId = String(event.championshipId ?? entityId(event.championship));
  return !(poolChampionshipId && eventChampionshipId && poolChampionshipId !== eventChampionshipId);
}

function entityId(value: unknown) {
  if (!value || typeof value !== "object" || !("id" in value)) return "";
  return String((value as { id?: unknown }).id ?? "");
}
