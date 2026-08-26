import { Ban, CheckCircle2, Clock3, Coins, Filter, RotateCcw, ShieldCheck, Target, Trophy, XCircle } from "lucide-react";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { dateTime, multiplier, points, predictionStatusLabel } from "../app/format";
import { Button, EmptyState, ErrorState, Modal, PageHeader, PageSkeleton, StatusBadge } from "../components/UI";
import { useToast } from "../contexts/ToastContext";
import { useAppData } from "../contexts/AppDataContext";
import { useApiResource } from "../hooks/useApiResource";
import { asList, predictionsApi, walletApi } from "../services/api";
import type { Prediction, WalletTransaction } from "../types";

const predictionFilters = [
  { value: "", label: "Todos" },
  { value: "ACTIVE", label: "Ativos" },
  { value: "WON", label: "Vencedores" },
  { value: "LOST", label: "Perdedores / reembolsados" },
  { value: "CANCELLED", label: "Cancelados" },
];

const predictionStatusGroups: Record<string, string[]> = {
  ACTIVE: ["ACTIVE", "ATIVO", "PENDING", "PENDENTE"],
  WON: ["WON", "VENCEDOR"],
  LOST: ["LOST", "PERDEDOR", "REFUNDED", "REEMBOLSADO"],
  CANCELLED: ["CANCELLED", "CANCELADO"],
};

export function predictionMatchesFilter(status: string | undefined, filter: string) {
  if (!filter) return true;
  return predictionStatusGroups[filter]?.includes(String(status || "").toUpperCase()) ?? false;
}

function isCancellable(prediction: Prediction) {
  return prediction.canCancel !== false && ["ACTIVE", "ATIVO", "PENDING", "PENDENTE"].includes(String(prediction.status).toUpperCase());
}

export function PredictionsPage() {
  const [filter, setFilter] = useState("");
  const [cancelTarget, setCancelTarget] = useState<Prediction | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const { notify } = useToast();
  const { refreshWallet, refreshNotifications } = useAppData();
  const { data, loading, error, reload } = useApiResource(async () => asList(await predictionsApi.list({ size: 50 })), []);

  const predictions = useMemo(() => {
    return (data || []).filter((item) => predictionMatchesFilter(item.status, filter));
  }, [data, filter]);

  async function cancelPrediction() {
    if (!cancelTarget || cancelling) return;
    setCancelling(true);
    try {
      await predictionsApi.cancel(cancelTarget.id);
      notify("Palpite cancelado. Os pontos elegíveis foram reembolsados.", "success");
      setCancelTarget(null);
      await Promise.allSettled([reload(), refreshWallet(), refreshNotifications()]);
    } catch (reason) {
      notify(reason instanceof Error ? reason.message : "Não foi possível cancelar o palpite.", "error");
    } finally {
      setCancelling(false);
    }
  }

  if (loading) return <PageSkeleton cards={4} />;
  if (error) return <ErrorState message={error} onRetry={() => reload().catch(() => undefined)} />;

  const all = data || [];
  const active = all.filter((item) => ["ACTIVE", "ATIVO", "PENDING", "PENDENTE"].includes(String(item.status).toUpperCase())).length;
  const won = all.filter((item) => ["WON", "VENCEDOR"].includes(String(item.status).toUpperCase())).length;
  const used = all.reduce((sum, item) => sum + Number(item.stakePoints ?? item.points ?? 0), 0);
  const rewards = all.reduce((sum, item) => sum + Number(item.rewardedPoints ?? item.rewardPoints ?? 0), 0);

  return (
    <>
      <PageHeader eyebrow="MINHA ATIVIDADE" title="Meus palpites" description="Acompanhe cada leitura, resultado e recompensa em pontos virtuais." actions={<Link className="button button--primary button--md" to="/events"><Target size={17} /> Novo palpite</Link>} />

      <section className="metric-grid">
        {[
          { label: "Palpites ativos", value: active, icon: Clock3, tone: "violet" },
          { label: "Palpites vencedores", value: won, icon: Trophy, tone: "green" },
          { label: "Pontos utilizados", value: `${points(used)} pts`, icon: Coins, tone: "blue" },
          { label: "Recompensas", value: `${points(rewards)} pts`, icon: CheckCircle2, tone: "orange" },
        ].map(({ label, value, icon: Icon, tone }) => <article className={`surface metric-card metric-card--${tone}`} key={label}><span className="metric-card__icon"><Icon size={21} /></span><div><small>{label}</small><strong>{value}</strong></div></article>)}
      </section>

      <div className="filter-tabs" role="group" aria-label="Filtrar palpites por status">
        {predictionFilters.map((item) => <button type="button" aria-pressed={filter === item.value} className={filter === item.value ? "active" : ""} onClick={() => setFilter(item.value)} key={item.value}>{item.label}<span>{all.filter((prediction) => predictionMatchesFilter(prediction.status, item.value)).length}</span></button>)}
      </div>

      {predictions.length ? (
        <div className="prediction-history">
          {predictions.map((prediction) => (
            <article className="surface prediction-history__row" key={prediction.id}>
              <span className={`prediction-state-icon prediction-state-icon--${String(prediction.status).toLowerCase()}`}>{["WON", "VENCEDOR"].includes(String(prediction.status).toUpperCase()) ? <CheckCircle2 size={21} /> : ["LOST", "PERDEDOR"].includes(String(prediction.status).toUpperCase()) ? <XCircle size={21} /> : ["REFUNDED", "REEMBOLSADO"].includes(String(prediction.status).toUpperCase()) ? <RotateCcw size={21} /> : ["CANCELLED", "CANCELADO"].includes(String(prediction.status).toUpperCase()) ? <Ban size={21} /> : <Target size={21} />}</span>
              <div className="prediction-history__event"><small>{prediction.marketName || "Mercado de previsão"}</small><strong>{prediction.eventTitle || `Evento #${prediction.eventId}`}</strong><span>{prediction.optionLabel || prediction.optionName || "Opção selecionada"}</span></div>
              <div className="prediction-history__numbers"><small>Pontos</small><strong>{points(prediction.stakePoints ?? prediction.points)} pts</strong></div>
              <div className="prediction-history__numbers"><small>Coeficiente</small><strong>{multiplier(prediction.multiplier)}</strong></div>
              <div className="prediction-history__numbers"><small>Potencial</small><strong>{points(prediction.potentialPoints || 0)} pts</strong></div>
              <div className="prediction-history__numbers prediction-history__reward"><small>Recompensa</small><strong>{Number(prediction.rewardedPoints ?? prediction.rewardPoints ?? 0) > 0 ? `${points(prediction.rewardedPoints ?? prediction.rewardPoints)} pts` : "—"}</strong></div>
              <div className="prediction-history__status"><StatusBadge status={prediction.status} label={predictionStatusLabel(prediction.status)} /><small>{dateTime(prediction.placedAt || prediction.createdAt)}</small></div>
              {isCancellable(prediction) && <Button variant="quiet" size="sm" onClick={() => setCancelTarget(prediction)}><RotateCcw size={15} /> Cancelar</Button>}
            </article>
          ))}
        </div>
      ) : (
        <EmptyState icon={Filter} title={filter ? "Nenhum palpite neste status" : "Sua lista de palpites está vazia"} description={filter ? "Escolha outro filtro para consultar seu histórico." : "Explore os eventos abertos e registre sua primeira leitura."} action={!filter ? <Link className="button button--primary button--md" to="/events">Explorar eventos</Link> : <Button variant="secondary" onClick={() => setFilter("")}>Mostrar todos</Button>} />
      )}

      <div className="virtual-footer-note"><ShieldCheck size={15} /> Palpites utilizam pontos virtuais e não representam apostas financeiras.</div>

      <Modal open={Boolean(cancelTarget)} onClose={() => !cancelling && setCancelTarget(null)} title="Cancelar palpite" size="sm">
        <div className="confirm-content"><span><RotateCcw size={25} /></span><p>O cancelamento só será concluído se o evento ainda estiver dentro do prazo configurado. Deseja continuar?</p><div className="modal-actions"><Button variant="secondary" onClick={() => setCancelTarget(null)} disabled={cancelling}>Manter palpite</Button><Button variant="danger" onClick={cancelPrediction} loading={cancelling}>Confirmar cancelamento</Button></div></div>
      </Modal>
    </>
  );
}

export function PointsPage() {
  const { data, loading, error, reload } = useApiResource(async () => {
    const [wallet, transactions] = await Promise.all([walletApi.get(), walletApi.transactions()]);
    return { wallet, transactions: asList(transactions) };
  }, []);
  if (loading) return <PageSkeleton cards={3} />;
  if (error || !data) return <ErrorState message={error || "Carteira indisponível."} onRetry={() => reload().catch(() => undefined)} />;
  const balance = Number(data.wallet.balancePoints ?? data.wallet.availablePoints ?? data.wallet.balance ?? 0);
  const transactions = data.transactions as WalletTransaction[];
  return (
    <>
      <PageHeader eyebrow="ECONOMIA VIRTUAL" title="Seus pontos" description="Saldo, recompensas e todas as movimentações da sua experiência na Arena." />
      <section className="points-hero">
        <div className="points-hero__glow" />
        <span><Coins size={25} /></span><div><small>Saldo disponível</small><strong>{points(balance)} <em>pts</em></strong><p>{data.wallet.virtualPointsNotice || "Pontos exclusivamente virtuais, sem depósito, saque ou conversão financeira."}</p></div>
      </section>
      <section className="metric-grid metric-grid--three">
        <article className="surface metric-card metric-card--green"><span className="metric-card__icon"><Trophy size={21} /></span><div><small>Pontos ganhos</small><strong>{points(data.wallet.earnedPoints ?? data.wallet.lifetimeEarned ?? 0)} pts</strong></div></article>
        <article className="surface metric-card metric-card--violet"><span className="metric-card__icon"><Target size={21} /></span><div><small>Pontos utilizados</small><strong>{points(data.wallet.usedPoints ?? data.wallet.lifetimeUsed ?? 0)} pts</strong></div></article>
        <article className="surface metric-card metric-card--blue"><span className="metric-card__icon"><ShieldCheck size={21} /></span><div><small>Tipo de saldo</small><strong>100% virtual</strong></div></article>
      </section>
      <section className="surface transaction-panel">
        <div className="section-header"><div><h2>Extrato de pontos</h2><p>Registro completo de débitos, recompensas, bônus e reembolsos.</p></div></div>
        {transactions.length ? <div className="transaction-list">{transactions.map((item) => { const amount = Number(item.points ?? item.amount ?? 0); return <div key={item.id}><span className={amount >= 0 ? "positive" : "negative"}>{amount >= 0 ? "+" : "−"}</span><div><strong>{item.description || transactionLabel(item.type)}</strong><small>{dateTime(item.createdAt, true)}</small></div><b className={amount >= 0 ? "positive" : "negative"}>{amount >= 0 ? "+" : "−"}{points(Math.abs(amount))} pts</b>{item.balanceAfter !== undefined && <em>Saldo {points(item.balanceAfter)}</em>}</div>; })}</div> : <EmptyState icon={Coins} title="Nenhuma movimentação" description="Bônus, palpites e recompensas aparecerão neste extrato." />}
      </section>
    </>
  );
}

function transactionLabel(type: string) {
  const labels: Record<string, string> = { INITIAL_BONUS: "Bônus inicial", PREDICTION_PLACED: "Palpite realizado", PREDICTION_WON: "Palpite vencedor", REFUND: "Reembolso", CHALLENGE_COMPLETED: "Desafio concluído", ACHIEVEMENT: "Conquista", RANKING_REWARD: "Recompensa de ranking", ADMIN_ADJUSTMENT: "Ajuste administrativo" };
  return labels[String(type).toUpperCase()] || "Movimentação de pontos";
}
