import { CalendarRange, Check, Clipboard, Crown, Globe2, KeyRound, LockKeyhole, Plus, ShieldCheck, Swords, Trophy, UserMinus, Users } from "lucide-react";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { brand } from "../app/branding";
import { championshipName, dateTime, points, sportName } from "../app/format";
import { Button, EmptyState, ErrorState, Modal, PageHeader, PageSkeleton, Progress, StatusBadge, UserAvatar } from "../components/UI";
import { useToast } from "../contexts/ToastContext";
import { useAppData } from "../contexts/AppDataContext";
import { useAuth } from "../contexts/AuthContext";
import { useApiResource } from "../hooks/useApiResource";
import { asList, catalogApi, poolsApi } from "../services/api";
import type { Championship, Pool, RankingRow, Sport } from "../types";

export function PoolsPage({ leaguesOnly = false }: { leaguesOnly?: boolean }) {
  const { user } = useAuth();
  const canCreate = !leaguesOnly || user?.role === "ADMIN";
  const [createOpen, setCreateOpen] = useState(false);
  const [joinOpen, setJoinOpen] = useState(false);
  const [rankingPool, setRankingPool] = useState<Pool | null>(null);
  const [ranking, setRanking] = useState<RankingRow[]>([]);
  const [rankingLoading, setRankingLoading] = useState(false);
  const [rankingError, setRankingError] = useState("");
  const [searchParams, setSearchParams] = useSearchParams();
  const openedSelected = useRef("");
  const { refreshWallet, refreshNotifications } = useAppData();
  const { data, loading, error, reload } = useApiResource(async () => asList(await poolsApi.list()), []);

  function refreshPoolData() {
    void Promise.allSettled([reload(), refreshWallet(), refreshNotifications()]);
  }

  const pools = useMemo(() => {
    return (data || []).filter((pool) => (String(pool.poolType || "POOL").toUpperCase() === "LEAGUE") === leaguesOnly);
  }, [data, leaguesOnly]);

  async function openRanking(pool: Pool) {
    setRankingPool(pool);
    setRankingLoading(true);
    setRankingError("");
    try {
      setRanking(asList(await poolsApi.ranking(pool.id)));
    } catch (reason) {
      setRanking([]);
      setRankingError(reason instanceof Error ? reason.message : "Não foi possível carregar o ranking.");
    } finally {
      setRankingLoading(false);
    }
  }

  useEffect(() => {
    const selected = searchParams.get("selected") || "";
    if (!selected || !data || openedSelected.current === selected) return;
    const pool = data.find((item) => String(item.id) === selected);
    if (!pool) return;
    openedSelected.current = selected;
    openRanking(pool).catch(() => undefined);
    const next = new URLSearchParams(searchParams);
    next.delete("selected");
    setSearchParams(next, { replace: true });
  }, [data, searchParams, setSearchParams]);

  if (loading) return <PageSkeleton cards={4} />;
  if (error) return <ErrorState message={error} onRetry={() => reload().catch(() => undefined)} />;

  return (
    <>
      <PageHeader
        eyebrow={leaguesOnly ? "COMPETIÇÕES DA PLATAFORMA" : "SEU GRUPO DE AMIGOS"}
        title={leaguesOnly ? "Ligas" : "Bolões"}
        description={leaguesOnly ? "Dispute temporadas organizadas pelo ArenaPredict, com período, modalidades e regras definidos pela plataforma." : "Crie grupos públicos ou privados, convide amigos e acompanhe o ranking dos palpites vinculados ao seu bolão."}
        actions={<div className="button-row">{!leaguesOnly && <Button variant="secondary" onClick={() => setJoinOpen(true)}><KeyRound size={17} /> Entrar por código</Button>}{canCreate && <Button onClick={() => setCreateOpen(true)}><Plus size={17} /> {leaguesOnly ? "Organizar liga" : "Criar bolão"}</Button>}</div>}
      />

      <section className="surface competition-explainer"><span>{leaguesOnly ? <Swords size={24} /> : <Users size={24} />}</span><div><h2>{leaguesOnly ? "Uma temporada, uma classificação" : "Seu bolão, suas regras"}</h2><p>{leaguesOnly ? "Ao participar, seus palpites válidos no período e escopo da liga contam automaticamente para a classificação. A organização é do ArenaPredict." : "O criador define o escopo e as regras. Ao confirmar um palpite, selecione o bolão para incluí-lo no ranking interno. Convites privados ficam entre os membros."}</p></div></section>
      {pools.length ? <div className="pool-grid">{pools.map((pool) => <PoolCard pool={pool} key={pool.id} onRanking={() => openRanking(pool)} onChange={refreshPoolData} />)}</div> : <EmptyState icon={leaguesOnly ? Swords : Trophy} title={leaguesOnly ? "Nenhuma temporada publicada" : "Nenhum bolão encontrado"} description={leaguesOnly ? "As próximas competições aparecerão quando forem publicadas pela plataforma." : "Crie o primeiro bolão ou entre com um código de convite."} action={canCreate ? <Button onClick={() => setCreateOpen(true)}><Plus size={17} /> Começar agora</Button> : undefined} />}

      <div className="virtual-footer-note"><ShieldCheck size={15} /> Rankings e premiações são exclusivamente virtuais.</div>
      <CreatePoolModal open={createOpen} onClose={() => setCreateOpen(false)} onCreated={refreshPoolData} league={leaguesOnly} />
      <JoinPoolModal open={joinOpen} onClose={() => setJoinOpen(false)} onJoined={refreshPoolData} />
      <PoolRankingModal pool={rankingPool} rows={ranking} loading={rankingLoading} error={rankingError} onRetry={() => rankingPool && openRanking(rankingPool)} onClose={() => setRankingPool(null)} />
    </>
  );
}

function PoolCard({ pool, onRanking, onChange }: { pool: Pool; onRanking: () => void; onChange: () => void }) {
  const [working, setWorking] = useState(false);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const { notify } = useToast();
  const count = Number(pool.participantCount ?? pool.participants ?? 0);
  const limit = Number(pool.maxParticipants || Math.max(count, 1));
  const league = pool.poolType === "LEAGUE";
  const scope = poolScopeLabels(pool);

  async function leave() {
    if (working) return;
    setWorking(true);
    try {
      await poolsApi.leave(pool.id);
      notify(`Você saiu de “${pool.name}”.`, "success");
      setConfirmLeave(false);
      onChange();
    } catch (error) {
      notify(error instanceof Error ? error.message : "Não foi possível sair do bolão.", "error");
    } finally {
      setWorking(false);
    }
  }

  async function joinPublic() {
    if (working) return;
    setWorking(true);
    try {
      await poolsApi.joinPublic(pool.id);
      notify(`Você entrou em “${pool.name}”.`, "success");
      onChange();
    } catch (error) {
      notify(error instanceof Error ? error.message : "Não foi possível entrar no bolão.", "error");
    } finally {
      setWorking(false);
    }
  }

  async function copyInvite() {
    if (!pool.inviteCode) return;
    try {
      await navigator.clipboard.writeText(pool.inviteCode);
      notify("Código de convite copiado.", "success");
    } catch {
      notify("Não foi possível copiar o código. Selecione-o manualmente.", "error");
    }
  }

  return (
    <>
      <article className={`surface pool-card${league ? " pool-card--league" : ""}`}>
        <div className="pool-card__accent" />
        <div className="pool-card__overview">
          <header><span className="pool-card__icon">{league ? <Swords size={21} /> : pool.privacy === "PRIVATE" ? <LockKeyhole size={21} /> : <Globe2 size={21} />}</span><div><StatusBadge status={pool.status || "ACTIVE"} /><h2>{pool.name}</h2><small>{league ? "Organização ArenaPredict" : pool.privacy === "PRIVATE" ? "Bolão privado · por convite" : "Bolão social · público"}</small></div>{pool.owner && !league && <span className="owner-badge"><Crown size={13} /> Criador</span>}</header>
          <p>{pool.description || `Competição entre participantes da ${brand.name}.`}</p>
          <div className="pool-card__meta"><span><Trophy size={15} /> {scope.sport}</span><span><CalendarRange size={15} /> {scope.championship}</span></div>
        </div>
        <div className="pool-card__details">
          <Progress value={count} max={limit} label={`${count} de ${limit} participantes`} />
          <div className="pool-card__dates"><span><small>Início</small><strong>{dateTime(pool.startsAt, league)}</strong></span><span><small>Encerramento</small><strong>{dateTime(pool.endsAt, league)}</strong></span></div>
          {pool.rules && <details className="competition-rules"><summary>Regras e classificação</summary><p>{pool.rules}</p></details>}
          {pool.virtualPrizePoints ? <p className="competition-reward">Reconhecimento da temporada: {points(pool.virtualPrizePoints)} pontos virtuais previstos nas regras.</p> : null}
          {!league && pool.inviteCode && <button className="invite-code" type="button" onClick={copyInvite}><span><small>Código de convite</small><strong>{pool.inviteCode}</strong></span><Clipboard size={16} /></button>}
          <footer><Button variant="secondary" onClick={onRanking}><Trophy size={16} /> Ver ranking</Button>{pool.joined && !pool.owner ? <Button variant="quiet" loading={working} onClick={() => setConfirmLeave(true)}><UserMinus size={16} /> Sair</Button> : pool.owner || pool.joined ? <span className="joined-label"><Check size={15} /> Participando</span> : pool.publicPool ? <Button loading={working} onClick={joinPublic}><Plus size={16} /> Participar</Button> : null}</footer>
        </div>
      </article>
      <Modal open={confirmLeave} onClose={() => !working && setConfirmLeave(false)} title="Sair do grupo" size="sm">
        <div className="confirm-content"><span><UserMinus size={25} /></span><p>Você deixará “{pool.name}” e não aparecerá mais no ranking deste grupo. Deseja continuar?</p><div className="modal-actions"><Button variant="secondary" onClick={() => setConfirmLeave(false)} disabled={working}>Continuar no grupo</Button><Button variant="danger" onClick={leave} loading={working}>Confirmar saída</Button></div></div>
      </Modal>
    </>
  );
}

export function poolScopeLabels(pool: {
  sport?: Pool["sport"] | null;
  championship?: Pool["championship"] | null;
}) {
  return {
    sport: pool.sport ? sportName(pool.sport) : "Todas as modalidades",
    championship: pool.championship ? championshipName(pool.championship) : "Todos os campeonatos",
  };
}

export function validatePoolCreationDraft(values: {
  name: string;
  rules: string;
  limit: number;
  virtualPrizePoints: number;
  startsAt: string;
  endsAt: string;
}) {
  if (values.name.trim().length < 3) return "Informe um nome com pelo menos 3 caracteres.";
  if (!values.rules.trim()) return "Defina as regras do grupo.";
  if (!Number.isInteger(values.limit) || values.limit < 2 || values.limit > 500) return "O limite deve ser um número inteiro entre 2 e 500 participantes.";
  if (!Number.isInteger(values.virtualPrizePoints) || values.virtualPrizePoints < 0 || values.virtualPrizePoints > 1_000_000) return "A premiação deve ser um número inteiro entre 0 e 1.000.000 pontos virtuais.";
  if (values.startsAt && Number.isNaN(new Date(values.startsAt).getTime())) return "Informe uma data de início válida.";
  if (values.endsAt && Number.isNaN(new Date(values.endsAt).getTime())) return "Informe uma data de encerramento válida.";
  if (values.startsAt && values.endsAt && new Date(values.endsAt) <= new Date(values.startsAt)) return "O encerramento deve ocorrer depois do início.";
  return null;
}

function CreatePoolModal({ open, onClose, onCreated, league }: { open: boolean; onClose: () => void; onCreated: () => void; league: boolean }) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [privacy, setPrivacy] = useState("PRIVATE");
  const [limit, setLimit] = useState(20);
  const [sportId, setSportId] = useState("");
  const [championshipId, setChampionshipId] = useState("");
  const [rules, setRules] = useState("Classificação por pontos virtuais acumulados nos palpites do período.");
  const [virtualPrizePoints, setVirtualPrizePoints] = useState(0);
  const [startsAt, setStartsAt] = useState("");
  const [endsAt, setEndsAt] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState("");
  const submitRequestRef = useRef(false);
  const nameRef = useRef<HTMLInputElement>(null);
  const { notify } = useToast();
  const { data: catalog, loading: catalogLoading, error: catalogError } = useApiResource(async () => {
    const [sports, championships] = await Promise.all([catalogApi.sports(), catalogApi.championships()]);
    return { sports: asList(sports), championships: asList(championships) };
  }, []);
  const championships = (catalog?.championships || []).filter((item: Championship) => !sportId || String(item.sportId || "") === sportId);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (submitRequestRef.current) return;
    const validationError = validatePoolCreationDraft({ name, rules, limit, virtualPrizePoints, startsAt, endsAt });
    if (validationError) {
      setFormError(validationError);
      notify(validationError, "error");
      if (name.trim().length < 3) window.requestAnimationFrame(() => nameRef.current?.focus());
      return;
    }
    submitRequestRef.current = true;
    setFormError("");
    setSubmitting(true);
    try {
      await poolsApi.create({
        name: name.trim(), description: description.trim(), privacy: league ? "PUBLIC" : privacy, maxParticipants: limit,
        sportId: sportId ? Number(sportId) : undefined,
        championshipId: championshipId ? Number(championshipId) : undefined,
        rules: rules.trim(), virtualPrizePoints, startsAt: startsAt ? new Date(startsAt).toISOString() : undefined,
        endsAt: endsAt ? new Date(endsAt).toISOString() : undefined,
        poolType: league ? "LEAGUE" : "POOL", recurring: league,
      } as Partial<Pool> & { sportId?: number; championshipId?: number });
      notify(`${league ? "Liga" : "Bolão"} criado com sucesso.`, "success");
      setName(""); setDescription(""); setSportId(""); setChampionshipId(""); setVirtualPrizePoints(0); setStartsAt(""); setEndsAt(""); onClose(); onCreated();
    } catch (error) {
      const message = error instanceof Error ? error.message : "Não foi possível criar.";
      setFormError(message);
      notify(message, "error");
    } finally {
      submitRequestRef.current = false;
      setSubmitting(false);
    }
  }
  const clearError = () => setFormError("");
  return (
    <Modal open={open} onClose={() => !submitting && onClose()} title={league ? "Criar liga" : "Criar bolão"}>
      <form className="stack-form" onSubmit={submit} noValidate aria-busy={submitting} aria-describedby={formError ? "pool-create-error" : undefined}>
        <label><span>Nome *</span><input ref={nameRef} required minLength={3} maxLength={100} value={name} disabled={submitting} onChange={(event) => { setName(event.target.value); clearError(); }} placeholder={league ? "Ex.: Liga dos Analistas" : "Ex.: Clássicos entre amigos"} /></label>
        <label><span>Descrição</span><textarea maxLength={500} value={description} disabled={submitting} onChange={(event) => setDescription(event.target.value)} placeholder="Explique a proposta do grupo." /></label>
        <div className="form-columns">
          <label><span>Modalidade</span><select value={sportId} onChange={(event) => { setSportId(event.target.value); setChampionshipId(""); }} disabled={catalogLoading || submitting}><option value="">Todas as modalidades</option>{(catalog?.sports || []).map((item: Sport) => <option value={String(item.id)} key={item.id}>{item.name}</option>)}</select></label>
          <label><span>Campeonato</span><select value={championshipId} onChange={(event) => setChampionshipId(event.target.value)} disabled={catalogLoading || submitting}><option value="">Todos os campeonatos</option>{championships.map((item: Championship) => <option value={String(item.id)} key={item.id}>{item.name} {item.season ? `· ${item.season}` : ""}</option>)}</select></label>
        </div>
        {catalogError && <p className="field-error">Não foi possível carregar modalidades e campeonatos.</p>}
        <label><span>Regras *</span><textarea required maxLength={1_500} value={rules} disabled={submitting} onChange={(event) => { setRules(event.target.value); clearError(); }} /></label>
        <div className="form-columns">
          <label><span>Visibilidade</span><select value={league ? "PUBLIC" : privacy} disabled={submitting || league} onChange={(event) => setPrivacy(event.target.value)}><option value="PRIVATE">Privado, por convite</option><option value="PUBLIC">Público</option></select></label>
          <label><span>Limite de participantes</span><input type="number" min="2" max="500" step="1" value={limit} disabled={submitting} onChange={(event) => { setLimit(Number(event.target.value)); clearError(); }} /></label>
        </div>
        <div className="form-columns">
          <label><span>Início{league ? " *" : ""}</span><input type="datetime-local" required={league} value={startsAt} disabled={submitting} onChange={(event) => { setStartsAt(event.target.value); clearError(); }} /></label>
          <label><span>Encerramento{league ? " *" : ""}</span><input type="datetime-local" required={league} value={endsAt} disabled={submitting} onChange={(event) => { setEndsAt(event.target.value); clearError(); }} /></label>
        </div>
        <label><span>Premiação virtual</span><input type="number" min="0" max="1000000" step="1" value={virtualPrizePoints} disabled={submitting} onChange={(event) => { setVirtualPrizePoints(Number(event.target.value)); clearError(); }} /><small>Somente pontos internos, sem valor financeiro.</small></label>
        {formError && <p className="field-error" role="alert" id="pool-create-error">{formError}</p>}
        <div className="virtual-disclaimer"><ShieldCheck size={16} /> Premiações e pontuações não possuem valor financeiro.</div>
        <div className="modal-actions"><Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>Cancelar</Button><Button type="submit" loading={submitting}>Criar {league ? "liga" : "bolão"}</Button></div>
      </form>
    </Modal>
  );
}

function JoinPoolModal({ open, onClose, onJoined }: { open: boolean; onClose: () => void; onJoined: () => void }) {
  const [code, setCode] = useState(""); const [submitting, setSubmitting] = useState(false); const { notify } = useToast();
  async function submit(event: FormEvent) { event.preventDefault(); if (!code.trim() || submitting) return; setSubmitting(true); try { const pool = await poolsApi.join(code.trim().toUpperCase()); notify(`Você entrou em “${pool.name}”.`, "success"); setCode(""); onClose(); onJoined(); } catch (error) { notify(error instanceof Error ? error.message : "Código inválido.", "error"); } finally { setSubmitting(false); } }
  return <Modal open={open} onClose={() => !submitting && onClose()} title="Entrar com convite" size="sm"><form className="join-form" onSubmit={submit}><span><KeyRound size={26} /></span><p>Digite o código compartilhado pelo criador do bolão ou da liga.</p><input value={code} onChange={(event) => setCode(event.target.value.toUpperCase())} placeholder="ARENA-2026" maxLength={16} autoFocus aria-label="Código de convite" /><div className="modal-actions"><Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>Cancelar</Button><Button type="submit" loading={submitting} disabled={!code.trim()}>Entrar no grupo</Button></div></form></Modal>;
}

function PoolRankingModal({ pool, rows, loading, error, onRetry, onClose }: { pool: Pool | null; rows: RankingRow[]; loading: boolean; error: string; onRetry: () => void; onClose: () => void }) {
  return <Modal open={Boolean(pool)} onClose={onClose} title={`Ranking · ${pool?.name || "Bolão"}`}><div className="pool-ranking">{loading ? <p role="status">Carregando ranking...</p> : error ? <ErrorState message={error} onRetry={onRetry} /> : rows.length ? rows.map((row) => <div className={row.currentUser ? "current" : ""} key={row.userId || row.position}><b>#{row.position}</b><UserAvatar name={row.name || row.participant} avatarUrl={row.avatarUrl} size="sm" /><span><strong>{row.name || row.participant}</strong><small>{row.hits || 0} acertos</small></span><em>{points(row.points)} pts</em></div>) : <EmptyState icon={Users} title="Ranking ainda sem posições" description="Os resultados dos primeiros eventos formarão esta classificação." />}</div></Modal>;
}
