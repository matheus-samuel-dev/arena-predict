import { CheckCircle2, Coins, ShieldCheck, Sparkles, Target, Trophy } from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { championshipName, eventTeams, getWalletBalance, multiplier, points } from "../app/format";
import { useAppData } from "../contexts/AppDataContext";
import { useToast } from "../contexts/ToastContext";
import { createIdempotencyKey, poolsApi, predictionsApi } from "../services/api";
import type { ArenaEvent, Pool, Prediction, PredictionDraft } from "../types";
import { Button, Modal } from "./UI";

const MINIMUM_POINTS = 10;

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

export function PredictionComposer({
  draft,
  onClose,
  onCreated,
}: {
  draft: PredictionDraft | null;
  onClose: () => void;
  onCreated?: (prediction: Prediction) => void;
}) {
  const [stake, setStake] = useState(MINIMUM_POINTS);
  const [submitting, setSubmitting] = useState(false);
  const [confirmed, setConfirmed] = useState<Prediction | null>(null);
  const [availablePools, setAvailablePools] = useState<Pool[]>([]);
  const [poolId, setPoolId] = useState("");
  const [poolsLoading, setPoolsLoading] = useState(false);
  const [poolsError, setPoolsError] = useState("");
  const { wallet, refreshWallet } = useAppData();
  const { notify } = useToast();
  const balance = getWalletBalance(wallet);
  const minimumPoints = resolveMarketMinimumPoints(draft?.market.minimumPoints);
  const rawCoefficient = Number(draft?.option.multiplier || 0);
  const coefficient = Number.isFinite(rawCoefficient) ? rawCoefficient : 0;
  const potential = Math.floor(stake * coefficient);
  const [home, away] = draft ? eventTeams(draft.event) : [{ name: "" }, { name: "" }];

  useEffect(() => {
    if (draft) {
      setConfirmed(null);
      setPoolId("");
    }
  }, [draft]);

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
        if (active) setAvailablePools(values.filter((pool) => pool.joined && poolAcceptsEvent(pool, draft.event)));
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
      setStake(Math.min(Math.max(minimumPoints, Math.floor(balance * 0.05)), Math.max(balance, minimumPoints)));
    }
  }, [draft, balance, minimumPoints, confirmed]);

  const validation = useMemo(() => {
    if (stake < minimumPoints) return `O mínimo deste mercado é ${minimumPoints} pontos.`;
    if (stake > balance) return "Seu saldo de pontos é insuficiente.";
    if (!Number.isInteger(stake)) return "Use uma quantidade inteira de pontos.";
    return null;
  }, [stake, balance, minimumPoints]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!draft || validation) return;
    setSubmitting(true);
    try {
      const created = await predictionsApi.create({
        eventId: draft.event.id,
        marketId: draft.market.id,
        optionId: draft.option.id,
        stakePoints: stake,
        poolId: poolId ? Number(poolId) : undefined,
        idempotencyKey: createIdempotencyKey(),
      });
      setConfirmed(created);
      await refreshWallet().catch(() => undefined);
      onCreated?.(created);
      notify("Palpite confirmado e pontos debitados com segurança.", "success");
    } catch (error) {
      notify(error instanceof Error ? error.message : "Não foi possível confirmar o palpite.", "error");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={Boolean(draft)} onClose={onClose} title={confirmed ? "Palpite confirmado" : "Confirmar palpite"} size="sm">
      {draft && !confirmed && (
        <form className="prediction-composer" onSubmit={submit}>
          <div className="prediction-composer__event">
            <span>{championshipName(draft.event.championship || draft.event.championshipName)}</span>
            <strong>{home.name || home.shortName} <i>vs</i> {away.name || away.shortName}</strong>
          </div>
          <div className="selection-summary">
            <span><Target size={18} /></span>
            <div><small>{draft.market.name}</small><strong>{draft.option.label || draft.option.name}</strong></div>
            <b>{multiplier(draft.option.multiplier)}</b>
          </div>
          <label className="stake-field">
            <span>Pontos virtuais</span>
            <div><Coins size={18} /><input type="number" min={minimumPoints} step="1" max={Math.max(balance, minimumPoints)} value={stake} onChange={(event) => setStake(Number(event.target.value))} /><em>pts</em></div>
            <small>Mínimo do mercado: <strong>{points(minimumPoints)} pts</strong></small>
            <small>Saldo disponível: <strong>{points(balance)} pts</strong></small>
          </label>
          <label className="prediction-pool-field">
            <span>Vincular a um bolão ou liga <small>(opcional)</small></span>
            <div><Trophy size={17} /><select value={poolId} onChange={(event) => setPoolId(event.target.value)} disabled={poolsLoading}><option value="">Palpite individual</option>{availablePools.map((pool) => <option value={String(pool.id)} key={pool.id}>{pool.name}</option>)}</select></div>
            {poolsLoading && <small>Carregando seus grupos...</small>}
            {poolsError && <small className="field-error">{poolsError}</small>}
            {!poolsLoading && !poolsError && availablePools.length === 0 && <small>Entre em um bolão compatível para pontuar no ranking do grupo.</small>}
          </label>
          <div className="quick-stakes" aria-label="Valores rápidos">
            {quickStakeOptions(minimumPoints).map((value) => <button type="button" key={value} onClick={() => setStake(Math.min(value, balance))} disabled={balance < value}>+{value}</button>)}
            <button type="button" onClick={() => setStake(Math.min(balance, Math.max(minimumPoints, Math.floor(balance * 0.25))))} disabled={balance < minimumPoints}>25%</button>
          </div>
          {validation && <p className="field-error" role="alert">{validation}</p>}
          <div className="potential-card">
            <span><Sparkles size={17} /> Potencial de pontos</span>
            <strong>{points(potential)} pts</strong>
            <small>{points(stake)} × {multiplier(coefficient)}</small>
          </div>
          <div className="virtual-disclaimer"><ShieldCheck size={16} /> Pontos sem valor financeiro. Nenhum dinheiro real é utilizado.</div>
          <div className="modal-actions">
            <Button type="button" variant="secondary" onClick={onClose}>Cancelar</Button>
            <Button type="submit" loading={submitting} disabled={Boolean(validation)}>Confirmar palpite</Button>
          </div>
        </form>
      )}
      {confirmed && (
        <div className="prediction-success">
          <span><CheckCircle2 size={34} /></span>
          <h3>Sua leitura está registrada</h3>
          <p>Acompanhe o evento e o processamento da recompensa em “Meus palpites”.</p>
          <div><small>Potencial</small><strong>{points(confirmed.potentialPoints ?? potential)} pts</strong></div>
          <Button onClick={onClose}>Continuar na Arena</Button>
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
