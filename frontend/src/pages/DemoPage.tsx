import { ArrowRight, CheckCircle2, Gamepad2, Play, RefreshCcw, RotateCcw, ShieldCheck, Target, Trophy, Users } from "lucide-react";
import { useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { dateTime, eventTeams, multiplier, points } from "../app/format";
import { EventCard } from "../components/EventCard";
import { PredictionComposer } from "../components/PredictionComposer";
import { Button, EmptyState, ErrorState, Modal, PageHeader, PageSkeleton, StatusBadge } from "../components/UI";
import { useAppData } from "../contexts/AppDataContext";
import { useAuth } from "../contexts/AuthContext";
import { useToast } from "../contexts/ToastContext";
import { useDemoScenario } from "../hooks/useDemoScenario";
import { useVisibleRefresh } from "../hooks/useVisibleRefresh";
import { demoApi } from "../services/api";
import type { DemoProfile, PredictionDraft } from "../types";
import "../demo.css";

type Action = "start" | "result" | "reset";
const actions: Record<Action, string> = { start: "Iniciar partida Demo", result: "Simular resultado", reset: "Começar nova rodada" };
const finals = [[2, 0], [2, 1], [0, 2], [1, 2]] as const;

export function DemoPage() {
  const { user, session, demoLogin, authenticating } = useAuth();
  const navigate = useNavigate();
  const { refreshWallet, refreshNotifications } = useAppData();
  const { notify } = useToast();
  const guided = Boolean(user?.demoProfile) && !session?.demoTraining;
  const { data, loading, error, refreshing, refresh, accept } = useDemoScenario(user?.userId, guided);
  const [draft, setDraft] = useState<PredictionDraft | null>(null);
  const [confirmation, setConfirmation] = useState<{ action: Action; generation: number; eventId: number | string } | null>(null);
  const [score, setScore] = useState("2:0");
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [actionError, setActionError] = useState("");
  const [success, setSuccess] = useState("");
  useVisibleRefresh(refresh, 15_000, guided && !busy && !authenticating);

  async function switchProfile(profile: DemoProfile, training = false) {
    if (busyRef.current || authenticating) return;
    setDraft(null); setConfirmation(null); setActionError("");
    try { await (training ? demoLogin(profile,true) : demoLogin(profile)); if(training) navigate("/live"); }
    catch (reason) { notify(reason instanceof Error ? reason.message : "Não foi possível trocar o perfil Demo.", "error"); }
  }

  function ask(action: Action) {
    if (!data?.canManage || busyRef.current) return;
    setActionError(""); setSuccess(""); setScore("2:0");
    setConfirmation({ action, generation: data.generation, eventId: data.event.id });
  }

  async function confirm() {
    if (!data?.canManage || !confirmation || busyRef.current || confirmation.generation !== data.generation || confirmation.eventId !== data.event.id) return;
    busyRef.current = true; setBusy(true); setActionError("");
    try {
      const [homeScore, awayScore] = score.split(":").map(Number);
      const result = confirmation.action === "start" ? await demoApi.start(confirmation.eventId)
        : confirmation.action === "result" ? await demoApi.result(confirmation.eventId, homeScore, awayScore)
          : await demoApi.reset(confirmation.generation);
      accept(result); setDraft(null); setConfirmation(null);
      const message = confirmation.action === "start" ? "Partida Demo ao vivo. Os palpites desta rodada estão encerrados."
        : confirmation.action === "result" ? "Resultado simulado e processado. Palpites e ranking foram atualizados pelo servidor."
          : "Nova rodada Demo disponível. O histórico e os lançamentos de pontos foram preservados.";
      setSuccess(message); notify(message, "success");
      await Promise.allSettled([refreshWallet(), refreshNotifications()]);
    } catch (reason) {
      setActionError(reason instanceof Error ? reason.message : "Não foi possível concluir a ação. Atualize a rodada e tente novamente.");
      // A timeout may follow a committed command. Reconcile before offering another attempt.
      await refresh().catch(() => undefined);
    } finally { busyRef.current = false; setBusy(false); }
  }

  if (!user?.demoProfile) return <div className="demo-journey"><PageHeader eyebrow="LABORATÓRIO ARENA" title="Experimente a Arena" description="Entre com uma conta Demo para experimentar a rodada compartilhada." /><section className="surface demo-profiles"><div><h2>Escolha um perfil Demo</h2><p>Participante Demo registra palpites; Administrador Demo conduz a rodada. A troca usa uma conta compartilhada de demonstração.</p></div><div role="group" aria-label="Trocar perfil Demo"><Button disabled={authenticating} onClick={() => void switchProfile("PARTICIPANT")}><Target size={17} /> Participante Demo</Button><Button variant="secondary" disabled={authenticating} onClick={() => void switchProfile("ADMIN")}><ShieldCheck size={17} /> Administrador Demo</Button></div></section></div>;
  if(session?.demoTraining) return <section className="surface"><PageHeader eyebrow="DEMONSTRAÇÃO" title="Seu treino ao vivo" description="Carteira e palpites exclusivos desta sessão. As partidas reais seguem o resultado oficial." /><Link className="button button--primary" to="/live">Escolher partida ao vivo</Link><p>A demonstração guiada possui rodada e saldo compartilhados, separados deste treino.</p><Button variant="secondary" disabled={authenticating} onClick={() => void switchProfile("PARTICIPANT")}>Usar demonstração guiada compartilhada</Button></section>;
  if (loading) return <PageSkeleton cards={3} />;
  if (!data) return <ErrorState message={error || "Demonstração indisponível neste ambiente."} onRetry={() => void refresh().catch(() => undefined)} />;
  const event = data.event;
  const [home, away] = eventTeams(event);
  const scheduled = ["SCHEDULED", "OPEN_FOR_PREDICTIONS"].includes(event.status);
  const finished = event.status === "FINISHED";
  const cancelled = event.status === "CANCELLED";
  const live = event.status === "LIVE";
  const scoreMarket = event.markets?.find((market) => (market.templateCode || market.code) === "SERIES_SCORE");
  const staleConfirmation = Boolean(confirmation && (confirmation.generation !== data.generation || confirmation.eventId !== event.id));
  const actionUnavailable = confirmation?.action === "start" ? !scheduled : confirmation?.action === "result" ? event.status !== "LIVE" : false;
  const canPredict = Boolean(data.canPredict && scheduled && scoreMarket?.availability?.allowed && !busy);
  const step = cancelled ? 0 : finished ? 4 : live ? 3 : data.predictions.some((prediction) => String(prediction.eventId) === String(event.id)) ? 2 : 1;
  const roundTitle = scheduled ? "Qual será o placar da série?" : finished ? "Rodada concluída" : cancelled ? "Rodada cancelada" : live ? "A partida está ao vivo" : "Rodada indisponível";
  const roundDescription = scheduled ? "BO3: vence quem conquistar dois mapas. Selecione uma opção para confirmar com pontos virtuais."
    : finished ? "Confira abaixo os palpites processados e a classificação atualizada."
      : cancelled ? "Os palpites ativos foram reembolsados. Comece uma nova rodada para experimentar o fluxo completo."
        : live ? "Os palpites pré-jogo estão fechados. O administrador pode simular o placar final."
          : "Acompanhe o estado da partida ou comece uma nova rodada com Administrador Demo.";
  return <div className="demo-journey">
    <PageHeader eyebrow={`LABORATÓRIO ARENA · RODADA ${data.generation}`} title="Experimente a Arena" description="Escolha um placar, acompanhe a partida e veja o resultado transformar o ranking." actions={<Button variant="secondary" size="sm" loading={refreshing} disabled={busy} onClick={() => void refresh().catch(() => undefined)}><RefreshCcw size={16} /> Atualizar</Button>} />
    <aside className="demo-shared-notice" role="note"><Users size={20} /><div><strong>Uma demonstração compartilhada</strong><p>{data.notice || "As contas e a rodada Demo são compartilhadas entre visitantes. Outro visitante pode iniciar ou finalizar esta partida."} Todos os pontos são virtuais.</p></div></aside>
    <Button variant="secondary" disabled={busy || authenticating} onClick={() => void switchProfile("PARTICIPANT",true)}>Entrar no treino ao vivo</Button>
    <section className="surface demo-profiles" aria-label="Perfil da demonstração"><div><span className="eyebrow">SEU PAPEL NESTA JORNADA</span><h2>{user?.demoProfile === "ADMIN" ? "Administrador Demo" : user?.demoProfile === "PARTICIPANT" ? "Participante Demo" : "Escolha um perfil Demo"}</h2><p>O participante registra o palpite. O administrador conduz somente a rodada demonstrativa.</p></div><div role="group" aria-label="Trocar perfil Demo"><Button variant={user?.demoProfile === "PARTICIPANT" ? "primary" : "secondary"} aria-pressed={user?.demoProfile === "PARTICIPANT"} disabled={busy || authenticating || user?.demoProfile === "PARTICIPANT"} onClick={() => void switchProfile("PARTICIPANT")}><Target size={17} /> Participante Demo</Button><Button variant={user?.demoProfile === "ADMIN" ? "primary" : "secondary"} aria-pressed={user?.demoProfile === "ADMIN"} disabled={busy || authenticating || user?.demoProfile === "ADMIN"} onClick={() => void switchProfile("ADMIN")}><ShieldCheck size={17} /> Administrador Demo</Button></div></section>
    <ol className="demo-steps" aria-label="Etapas da demonstração">{["Escolha seu placar", "Inicie a partida", "Simule o resultado", "Confira o ranking"].map((label, index) => <li key={label} aria-current={step === index + 1 ? "step" : undefined} className={step > index + 1 ? "complete" : ""}><span>{step > index + 1 ? <CheckCircle2 size={17} /> : index + 1}</span>{label}</li>)}</ol>
    {error && <div className="demo-feedback demo-feedback--error" role="alert">{error} Os últimos dados continuam visíveis.</div>}
    {success && <div className="demo-feedback" role="status"><CheckCircle2 size={18} /> {success}</div>}
    <div className="demo-main-grid"><section aria-label="Partida da rodada Demo"><EventCard event={event} showPreview={false} />
      <section className="surface demo-action-panel" aria-label="Ações da rodada"><div className="section-header"><div><h2>{roundTitle}</h2><p>{roundDescription}</p></div></div>
        {scheduled && scoreMarket && <div className="demo-score-options" role="group" aria-label="Selecionar palpite de placar">{scoreMarket.options.map((option) => <button type="button" key={option.id} disabled={!canPredict || option.active === false || option.suspended} onClick={() => setDraft({ event, market: scoreMarket, option })}><strong>{option.label || option.name}</strong><small>{multiplier(option.multiplier)} em pontos</small><ArrowRight size={16} /></button>)}</div>}
        {scheduled && !scoreMarket && <p role="status">Aguardando publicação das opções desta rodada.</p>}
        {scheduled && !data.canPredict && <p className="demo-guidance">Troque para Participante Demo para registrar um palpite antes de iniciar.</p>}
        {data.canManage ? <div className="demo-admin-actions">{scheduled && <Button disabled={busy} onClick={() => ask("start")}><Play size={17} /> Iniciar partida Demo</Button>}{event.status === "LIVE" && <Button disabled={busy} onClick={() => ask("result")}><Gamepad2 size={17} /> Simular resultado</Button>}<Button variant="secondary" disabled={busy} onClick={() => ask("reset")}><RotateCcw size={17} /> Começar nova rodada</Button></div> : <><p className="demo-guidance"><ShieldCheck size={17} /> {finished || cancelled ? "Esta rodada terminou. Prepare outra rodada demonstrativa para fazer seu palpite." : "Após seu palpite, troque para Administrador Demo para conduzir a partida."}</p>{(finished || cancelled) && <Button variant="secondary" disabled={busy || authenticating} onClick={() => void switchProfile("ADMIN")}>Preparar nova rodada Demo</Button>}</>}
      </section></section>
      <section className="surface demo-ranking" aria-labelledby="demo-ranking-title"><div className="section-header"><div><h2 id="demo-ranking-title"><Trophy size={19} /> Ranking da competição Demo</h2><p>Classificação calculada pelo servidor, com o histórico de exemplo e a rodada atual.</p></div></div>{data.ranking.length ? <div role="table" aria-label="Ranking da competição Demo"><div className="demo-ranking-row demo-ranking-row--head" role="row"><span role="columnheader">Posição</span><span role="columnheader">Participante</span><span role="columnheader">Pontos</span></div>{data.ranking.map((row) => <div className={`demo-ranking-row${row.currentUser ? " current" : ""}`} role="row" key={row.userId ?? row.position}><span role="cell">#{row.position}</span><strong role="cell">{row.name || row.participant || row.playerName || "Participante"}{row.currentUser && <small>Você</small>}</strong><b role="cell">{points(row.points)}<small>{row.hits ?? row.correctPredictions ?? 0} acertos</small></b></div>)}</div> : <EmptyState title="Ranking em formação" description="A classificação aparecerá quando houver resultados processados." />}</section></div>
    <section className="surface demo-predictions" aria-labelledby="demo-predictions-title"><div className="section-header"><div><h2 id="demo-predictions-title">Meus palpites nesta competição</h2><p>{user?.demoProfile === "ADMIN" ? "Troque para Participante Demo para acompanhar os palpites da conta compartilhada." : "Valores e resultados retornados pelo servidor. Os pontos gerais da conta permanecem no histórico."}</p></div></div>{data.predictions.length ? <div className="demo-prediction-list">{data.predictions.map((prediction) => <article key={prediction.id}><div><strong>{prediction.eventTitle || "Partida Demo"}</strong><span>{prediction.optionLabel || prediction.optionName}</span></div><div><StatusBadge status={prediction.status} /><small>{points(prediction.stakePoints ?? prediction.points)} pts utilizados</small><strong>{["WON", "VENCEDOR"].includes(prediction.status) ? `${points(prediction.rewardedPoints ?? prediction.rewardPoints)} pts recebidos` : ["LOST", "PERDEDOR"].includes(prediction.status) ? "Sem recompensa nesta partida" : ["REFUNDED", "REEMBOLSADO"].includes(prediction.status) ? `${points(prediction.stakePoints ?? prediction.points)} pts reembolsados` : ["CANCELLED", "CANCELADO"].includes(prediction.status) ? "Palpite cancelado" : "Aguardando resultado"}</strong></div></article>)}</div> : <EmptyState icon={Target} title="Nenhum palpite nesta conta" description="Use Participante Demo e selecione um placar na partida agendada para experimentar." />}</section>
    {data.history.length > 0 && <details className="surface demo-history"><summary>Histórico demonstrativo · {data.history.length} partidas</summary><p>Partidas identificadas como Demo para contextualizar a classificação.</p><div className="events-grid">{data.history.map((item) => <EventCard event={item} compact showPreview={false} key={item.id} />)}</div></details>}
    <p className="demo-updated">Atualizado em {dateTime(data.updatedAt)} · A página acompanha o servidor a cada 15 segundos enquanto está visível.</p>
    <PredictionComposer draft={draft} currentEvent={event} demoScenario onClose={() => setDraft(null)} onCreated={() => void refresh().catch(() => undefined)} />
    <Modal open={Boolean(confirmation)} onClose={() => { if (!busyRef.current) setConfirmation(null); }} title={confirmation ? actions[confirmation.action] : "Confirmar ação Demo"} size="sm"><div className="demo-confirmation"><p><strong>{home.name} × {away.name}</strong> · Rodada {confirmation?.generation}</p>{confirmation?.action === "start" && <p>Iniciar a partida encerra os palpites pré-jogo para todos os visitantes. Confira seu palpite antes de continuar.</p>}{confirmation?.action === "result" && <><label htmlFor="demo-final-score">Placar final simulado · BO3<select id="demo-final-score" value={score} disabled={busy} onChange={(change) => setScore(change.target.value)}>{finals.map(([left, right]) => <option key={`${left}:${right}`} value={`${left}:${right}`}>{home.name} {left} × {right} {away.name}</option>)}</select></label><p>O servidor finalizará esta partida Demo, processará os palpites e atualizará o ranking uma única vez.</p></>}{confirmation?.action === "reset" && <p>A rodada atual será arquivada e uma nova partida agendada será criada para todos os visitantes. Palpites ativos serão reembolsados. Histórico, lançamentos de pontos e partidas reais permanecem preservados; o ranking desta competição volta ao histórico de exemplo.</p>}{(staleConfirmation || actionUnavailable) && <p role="alert" className="field-error">Outro visitante atualizou a rodada. Feche esta confirmação e confira o estado atual.</p>}{actionError && <p role="alert" className="field-error">{actionError}</p>}<div className="modal-actions"><Button variant="secondary" disabled={busy} onClick={() => setConfirmation(null)}>Voltar</Button><Button loading={busy} disabled={staleConfirmation || actionUnavailable || !data.canManage} onClick={() => void confirm()}>Confirmar {confirmation?.action === "start" ? "início" : confirmation?.action === "result" ? "resultado" : "nova rodada"}</Button></div></div></Modal>
  </div>;
}
