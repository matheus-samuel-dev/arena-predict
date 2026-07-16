import { CalendarRange, Check, Clipboard, Crown, Globe2, KeyRound, LockKeyhole, Plus, ShieldCheck, Swords, Trophy, UserMinus, Users } from "lucide-react";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { brand } from "../app/branding";
import { championshipName, dateTime, points, sportName } from "../app/format";
import { Button, EmptyState, ErrorState, Modal, PageHeader, PageSkeleton, Progress, StatusBadge } from "../components/UI";
import { useToast } from "../contexts/ToastContext";
import { useApiResource } from "../hooks/useApiResource";
import { asList, catalogApi, poolsApi } from "../services/api";
import type { Championship, Pool, RankingRow, Sport } from "../types";

export function PoolsPage({ leaguesOnly = false }: { leaguesOnly?: boolean }) {
  const [createOpen, setCreateOpen] = useState(false);
  const [joinOpen, setJoinOpen] = useState(false);
  const [rankingPool, setRankingPool] = useState<Pool | null>(null);
  const [ranking, setRanking] = useState<RankingRow[]>([]);
  const [rankingLoading, setRankingLoading] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();
  const openedSelected = useRef("");
  const { data, loading, error, reload } = useApiResource(async () => asList(await poolsApi.list()), []);

  const pools = useMemo(() => {
    if (!leaguesOnly) return data || [];
    return (data || []).filter((pool) => String(pool.poolType || "").toUpperCase() === "LEAGUE" || Boolean(pool.recurring));
  }, [data, leaguesOnly]);

  async function openRanking(pool: Pool) {
    setRankingPool(pool);
    setRankingLoading(true);
    try {
      setRanking(asList(await poolsApi.ranking(pool.id)));
    } catch {
      setRanking([]);
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
        eyebrow={leaguesOnly ? "COMPETIÇÕES RECORRENTES" : "COMPETIÇÃO EM GRUPO"}
        title={leaguesOnly ? "Ligas" : "Bolões"}
        description={leaguesOnly ? "Crie temporadas recorrentes com amigos, compare evolução e mantenha a rivalidade saudável." : "Crie grupos públicos ou privados, convide amigos e acompanhe rankings próprios."}
        actions={<div className="button-row"><Button variant="secondary" onClick={() => setJoinOpen(true)}><KeyRound size={17} /> Entrar por código</Button><Button onClick={() => setCreateOpen(true)}><Plus size={17} /> {leaguesOnly ? "Criar liga" : "Criar bolão"}</Button></div>}
      />

      {pools.length ? <div className="pool-grid">{pools.map((pool) => <PoolCard pool={pool} key={pool.id} onRanking={() => openRanking(pool)} onChange={() => reload().catch(() => undefined)} />)}</div> : <EmptyState icon={leaguesOnly ? Swords : Trophy} title={leaguesOnly ? "Nenhuma liga recorrente ainda" : "Nenhum bolão encontrado"} description={leaguesOnly ? "Crie uma temporada entre amigos e mantenha um ranking contínuo." : "Crie o primeiro bolão ou entre com um código de convite."} action={<Button onClick={() => setCreateOpen(true)}><Plus size={17} /> Começar agora</Button>} />}

      <div className="virtual-footer-note"><ShieldCheck size={15} /> Rankings e premiações são exclusivamente virtuais.</div>
      <CreatePoolModal open={createOpen} onClose={() => setCreateOpen(false)} onCreated={() => reload().catch(() => undefined)} league={leaguesOnly} />
      <JoinPoolModal open={joinOpen} onClose={() => setJoinOpen(false)} onJoined={() => reload().catch(() => undefined)} />
      <PoolRankingModal pool={rankingPool} rows={ranking} loading={rankingLoading} onClose={() => setRankingPool(null)} />
    </>
  );
}

function PoolCard({ pool, onRanking, onChange }: { pool: Pool; onRanking: () => void; onChange: () => void }) {
  const [working, setWorking] = useState(false);
  const { notify } = useToast();
  const count = Number(pool.participantCount ?? pool.participants ?? 0);
  const limit = Number(pool.maxParticipants || Math.max(count, 1));

  async function leave() {
    setWorking(true);
    try {
      await poolsApi.leave(pool.id);
      notify(`Você saiu de “${pool.name}”.`, "success");
      onChange();
    } catch (error) {
      notify(error instanceof Error ? error.message : "Não foi possível sair do bolão.", "error");
    } finally {
      setWorking(false);
    }
  }

  async function joinPublic() {
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
    await navigator.clipboard.writeText(pool.inviteCode);
    notify("Código de convite copiado.", "success");
  }

  return (
    <article className="surface pool-card">
      <div className="pool-card__accent" />
      <header><span className="pool-card__icon">{pool.privacy === "PRIVATE" ? <LockKeyhole size={21} /> : <Globe2 size={21} />}</span><div><StatusBadge status={pool.status || "active"} label={pool.status || "Ativo"} /><h2>{pool.name}</h2></div>{pool.owner && <span className="owner-badge"><Crown size={13} /> Criador</span>}</header>
      <p>{pool.description || `Competição entre participantes da ${brand.name}.`}</p>
      <div className="pool-card__meta"><span><Trophy size={15} /> {sportName(pool.sport)}</span><span><CalendarRange size={15} /> {championshipName(pool.championship)}</span></div>
      <Progress value={count} max={limit} label={`${count} de ${limit} participantes`} />
      <div className="pool-card__dates"><span><small>Início</small><strong>{dateTime(pool.startsAt)}</strong></span><span><small>Encerramento</small><strong>{dateTime(pool.endsAt)}</strong></span></div>
      {pool.inviteCode && <button className="invite-code" type="button" onClick={copyInvite}><span><small>Código de convite</small><strong>{pool.inviteCode}</strong></span><Clipboard size={16} /></button>}
      <footer><Button variant="secondary" onClick={onRanking}><Trophy size={16} /> Ver ranking</Button>{pool.joined && !pool.owner ? <Button variant="quiet" loading={working} onClick={leave}><UserMinus size={16} /> Sair</Button> : pool.owner || pool.joined ? <span className="joined-label"><Check size={15} /> Participando</span> : pool.publicPool ? <Button loading={working} onClick={joinPublic}><Plus size={16} /> Participar</Button> : null}</footer>
    </article>
  );
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
  const { notify } = useToast();
  const { data: catalog, loading: catalogLoading, error: catalogError } = useApiResource(async () => {
    const [sports, championships] = await Promise.all([catalogApi.sports(), catalogApi.championships()]);
    return { sports: asList(sports), championships: asList(championships) };
  }, []);
  const championships = (catalog?.championships || []).filter((item: Championship) => !sportId || String(item.sportId || "") === sportId);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (name.trim().length < 3) { notify("Informe um nome com pelo menos 3 caracteres.", "error"); return; }
    if (!rules.trim()) { notify("Defina as regras do grupo.", "error"); return; }
    if (startsAt && endsAt && new Date(endsAt) <= new Date(startsAt)) { notify("O encerramento deve ocorrer depois do início.", "error"); return; }
    setSubmitting(true);
    try {
      await poolsApi.create({
        name: name.trim(), description: description.trim(), privacy, maxParticipants: limit,
        sportId: sportId ? Number(sportId) : undefined,
        championshipId: championshipId ? Number(championshipId) : undefined,
        rules: rules.trim(), virtualPrizePoints, startsAt: startsAt ? new Date(startsAt).toISOString() : undefined,
        endsAt: endsAt ? new Date(endsAt).toISOString() : undefined,
        poolType: league ? "LEAGUE" : "POOL", recurring: league,
      } as Partial<Pool> & { sportId?: number; championshipId?: number });
      notify(`${league ? "Liga" : "Bolão"} criado com sucesso.`, "success");
      setName(""); setDescription(""); setSportId(""); setChampionshipId(""); setVirtualPrizePoints(0); setStartsAt(""); setEndsAt(""); onClose(); onCreated();
    } catch (error) { notify(error instanceof Error ? error.message : "Não foi possível criar.", "error"); } finally { setSubmitting(false); }
  }
  return <Modal open={open} onClose={onClose} title={league ? "Criar liga" : "Criar bolão"}><form className="stack-form" onSubmit={submit}><label><span>Nome</span><input value={name} onChange={(event) => setName(event.target.value)} placeholder={league ? "Ex.: Liga dos Analistas" : "Ex.: Clássicos entre amigos"} /></label><label><span>Descrição</span><textarea value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Explique a proposta do grupo." /></label><div className="form-columns"><label><span>Modalidade</span><select value={sportId} onChange={(event) => { setSportId(event.target.value); setChampionshipId(""); }} disabled={catalogLoading}><option value="">Todas as modalidades</option>{(catalog?.sports || []).map((item: Sport) => <option value={String(item.id)} key={item.id}>{item.name}</option>)}</select></label><label><span>Campeonato</span><select value={championshipId} onChange={(event) => setChampionshipId(event.target.value)} disabled={catalogLoading}><option value="">Todos os campeonatos</option>{championships.map((item: Championship) => <option value={String(item.id)} key={item.id}>{item.name} {item.season ? `· ${item.season}` : ""}</option>)}</select></label></div>{catalogError && <p className="field-error">Não foi possível carregar modalidades e campeonatos.</p>}<label><span>Regras</span><textarea required value={rules} onChange={(event) => setRules(event.target.value)} /></label><div className="form-columns"><label><span>Visibilidade</span><select value={privacy} onChange={(event) => setPrivacy(event.target.value)}><option value="PRIVATE">Privado, por convite</option><option value="PUBLIC">Público</option></select></label><label><span>Limite de participantes</span><input type="number" min="2" max="500" value={limit} onChange={(event) => setLimit(Number(event.target.value))} /></label></div><div className="form-columns"><label><span>Início</span><input type="datetime-local" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} /></label><label><span>Encerramento</span><input type="datetime-local" value={endsAt} onChange={(event) => setEndsAt(event.target.value)} /></label></div><label><span>Premiação virtual</span><input type="number" min="0" max="1000000" value={virtualPrizePoints} onChange={(event) => setVirtualPrizePoints(Number(event.target.value))} /><small>Somente pontos internos, sem valor financeiro.</small></label><div className="virtual-disclaimer"><ShieldCheck size={16} /> Premiações e pontuações não possuem valor financeiro.</div><div className="modal-actions"><Button type="button" variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" loading={submitting}>Criar {league ? "liga" : "bolão"}</Button></div></form></Modal>;
}

function JoinPoolModal({ open, onClose, onJoined }: { open: boolean; onClose: () => void; onJoined: () => void }) {
  const [code, setCode] = useState(""); const [submitting, setSubmitting] = useState(false); const { notify } = useToast();
  async function submit(event: FormEvent) { event.preventDefault(); if (!code.trim()) return; setSubmitting(true); try { const pool = await poolsApi.join(code.trim().toUpperCase()); notify(`Você entrou em “${pool.name}”.`, "success"); setCode(""); onClose(); onJoined(); } catch (error) { notify(error instanceof Error ? error.message : "Código inválido.", "error"); } finally { setSubmitting(false); } }
  return <Modal open={open} onClose={onClose} title="Entrar com convite" size="sm"><form className="join-form" onSubmit={submit}><span><KeyRound size={26} /></span><p>Digite o código compartilhado pelo criador do bolão ou da liga.</p><input value={code} onChange={(event) => setCode(event.target.value.toUpperCase())} placeholder="ARENA-2026" maxLength={24} autoFocus /><div className="modal-actions"><Button type="button" variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" loading={submitting} disabled={!code.trim()}>Entrar no grupo</Button></div></form></Modal>;
}

function PoolRankingModal({ pool, rows, loading, onClose }: { pool: Pool | null; rows: RankingRow[]; loading: boolean; onClose: () => void }) {
  return <Modal open={Boolean(pool)} onClose={onClose} title={`Ranking · ${pool?.name || "Bolão"}`}><div className="pool-ranking">{loading ? <p>Carregando ranking...</p> : rows.length ? rows.map((row) => <div className={row.currentUser ? "current" : ""} key={row.userId || row.position}><b>#{row.position}</b><span><strong>{row.name || row.participant}</strong><small>{row.hits || 0} acertos</small></span><em>{points(row.points)} pts</em></div>) : <EmptyState icon={Users} title="Ranking ainda sem posições" description="Os resultados dos primeiros eventos formarão esta classificação." />}</div></Modal>;
}
