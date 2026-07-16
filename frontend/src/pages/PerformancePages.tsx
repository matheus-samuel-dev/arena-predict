import { Award, BarChart3, ChevronDown, Crown, Flame, Gauge, Medal, ShieldCheck, Sparkles, Target, Trophy, Users, Zap } from "lucide-react";
import { useMemo, useState } from "react";
import { percentage, points } from "../app/format";
import { Avatar, EmptyState, ErrorState, PageHeader, PageSkeleton, Progress } from "../components/UI";
import { useApiResource } from "../hooks/useApiResource";
import { achievementsApi, asList, catalogApi, predictionsApi, rankingsApi } from "../services/api";
import type { Achievement, RankingRow } from "../types";

export function RankingsPage() {
  const [period, setPeriod] = useState("WEEKLY");
  const [scope, setScope] = useState("GLOBAL");
  const [sport, setSport] = useState("");
  const { data, loading, error, reload } = useApiResource(async () => ({
    rows: asList(await rankingsApi.list({ period, scope, sport: sport || undefined })),
    sports: asList(await catalogApi.sports()),
  }), [period, scope, sport]);
  if (loading) return <PageSkeleton cards={3} />;
  if (error) return <ErrorState message={error} onRetry={() => reload().catch(() => undefined)} />;
  const rows = data?.rows || [];
  return (
    <>
      <PageHeader eyebrow="PLACAR DA COMUNIDADE" title="Rankings" description="Compare consistência, precisão e evolução em diferentes períodos e modalidades." />
      <section className="ranking-controls surface">
        <div className="filter-tabs">{[{ value: "WEEKLY", label: "Semanal" }, { value: "MONTHLY", label: "Mensal" }, { value: "ALL", label: "Geral" }].map((item) => <button type="button" className={period === item.value ? "active" : ""} onClick={() => setPeriod(item.value)} key={item.value}>{item.label}</button>)}</div>
        <label className="select-field"><Users size={16} /><select value={scope} onChange={(event) => setScope(event.target.value)}><option value="GLOBAL">Todos os jogadores</option><option value="FRIENDS">Entre amigos</option></select><ChevronDown size={15} /></label>
        <label className="select-field"><Trophy size={16} /><select value={sport} onChange={(event) => setSport(event.target.value)}><option value="">Todas as modalidades</option>{(data?.sports || []).map((item) => <option value={item.slug || item.id} key={item.id}>{item.name}</option>)}</select><ChevronDown size={15} /></label>
      </section>
      {rows.length ? <><Podium rows={rows.slice(0, 3)} /><section className="surface ranking-table-panel"><div className="ranking-table ranking-table--header"><span>Posição</span><span>Jogador</span><span>Pontos</span><span>Acertos</span><span>Precisão</span><span>Sequência</span></div>{rows.map((row) => <RankingLine row={row} key={row.userId || row.position} />)}</section></> : <EmptyState icon={Trophy} title="Ranking ainda em formação" description="As posições surgem depois que os resultados oficiais são processados." />}
      <div className="virtual-footer-note"><ShieldCheck size={15} /> Rankings concedem apenas reconhecimento e recompensas virtuais.</div>
    </>
  );
}

function Podium({ rows }: { rows: RankingRow[] }) {
  const ordered = [rows[1], rows[0], rows[2]].filter(Boolean);
  return <section className="podium" aria-label="Pódio"><div className="podium__ambient" />{ordered.map((row) => <article className={`podium__item podium__item--${row.position} ${row.currentUser ? "current" : ""}`} key={row.userId || row.position}><span className="podium__crown">{row.position === 1 ? <Crown size={20} /> : <Medal size={20} />}</span><Avatar name={row.name || row.participant} image={row.avatarUrl} size="lg" /><strong>{row.name || row.participant}</strong><small>{row.hits || 0} acertos · {percentage(row.accuracy)}</small><b>{points(row.points)} pts</b><em>#{row.position}</em></article>)}</section>;
}

function RankingLine({ row }: { row: RankingRow }) {
  return <div className={`ranking-table ranking-table--row ${row.currentUser ? "current" : ""}`}><span data-label="Posição"><b>#{row.position}</b>{row.movement !== undefined && row.movement !== 0 && <small className={row.movement > 0 ? "up" : "down"}>{row.movement > 0 ? "↑" : "↓"}{Math.abs(row.movement)}</small>}</span><span data-label="Jogador"><Avatar name={row.name || row.participant} image={row.avatarUrl} size="sm" /><strong>{row.name || row.participant}</strong>{row.currentUser && <em>Você</em>}</span><span data-label="Pontos"><strong>{points(row.points)}</strong></span><span data-label="Acertos">{row.hits || 0}/{row.predictions || 0}</span><span data-label="Precisão">{percentage(row.accuracy)}</span><span data-label="Sequência"><Flame size={15} /> {row.streak || 0}</span></div>;
}

export function StatisticsPage() {
  const { data, loading, error, reload } = useApiResource(async () => {
    const predictions = asList(await predictionsApi.list({ size: 200 }));
    return { predictions };
  }, []);
  if (loading) return <PageSkeleton cards={4} />;
  if (error || !data) return <ErrorState message={error || "Estatísticas indisponíveis."} onRetry={() => reload().catch(() => undefined)} />;
  const predictions = data.predictions;
  const settled = predictions.filter((item) => ["WON", "VENCEDOR", "LOST", "PERDEDOR"].includes(String(item.status).toUpperCase()));
  const won = settled.filter((item) => ["WON", "VENCEDOR"].includes(String(item.status).toUpperCase()));
  const accuracy = settled.length ? (won.length / settled.length) * 100 : 0;
  const used = predictions.reduce((sum, item) => sum + Number(item.stakePoints ?? item.points ?? 0), 0);
  const rewards = predictions.reduce((sum, item) => sum + Number(item.rewardedPoints ?? item.rewardPoints ?? 0), 0);
  const byMarket = Array.from(predictions.reduce((map, item) => { const key = item.marketName || "Outros mercados"; const value = map.get(key) || { name: key, total: 0, won: 0 }; value.total += 1; if (["WON", "VENCEDOR"].includes(String(item.status).toUpperCase())) value.won += 1; map.set(key, value); return map; }, new Map<string, { name: string; total: number; won: number }>()).values()).sort((a, b) => b.total - a.total);
  return (
    <>
      <PageHeader eyebrow="ANÁLISE PESSOAL" title="Estatísticas" description="Entenda seus padrões, especialidades e evolução sem números decorativos." />
      <section className="metric-grid">
        {[
          { label: "Precisão geral", value: percentage(accuracy), detail: `${won.length} de ${settled.length} resultados`, icon: Gauge, tone: "green" },
          { label: "Palpites registrados", value: points(predictions.length), detail: `${settled.length} já processados`, icon: Target, tone: "violet" },
          { label: "Pontos utilizados", value: `${points(used)} pts`, detail: "em toda a sua jornada", icon: Zap, tone: "blue" },
          { label: "Pontos recompensados", value: `${points(rewards)} pts`, detail: "por palpites vencedores", icon: Trophy, tone: "orange" },
        ].map(({ label, value, detail, icon: Icon, tone }) => <article className={`surface metric-card metric-card--${tone}`} key={label}><span className="metric-card__icon"><Icon size={21} /></span><div><small>{label}</small><strong>{value}</strong><span>{detail}</span></div></article>)}
      </section>
      <section className="statistics-grid">
        <article className="surface accuracy-panel"><div><span><Gauge size={23} /></span><small>Sua precisão</small><strong>{percentage(accuracy)}</strong><p>calculada apenas sobre palpites encerrados</p></div><div className="accuracy-ring" style={{ "--accuracy": `${accuracy * 3.6}deg` } as React.CSSProperties}><span>{Math.round(accuracy)}%</span></div></article>
        <article className="surface market-performance"><h2>Mercados mais utilizados</h2><p>Volume e aproveitamento por tipo de leitura.</p>{byMarket.length ? <div>{byMarket.slice(0, 6).map((item) => <section key={item.name}><header><strong>{item.name}</strong><span>{item.total} palpites · {percentage(item.total ? (item.won / item.total) * 100 : 0)}</span></header><Progress value={item.won} max={item.total} /></section>)}</div> : <EmptyState icon={BarChart3} title="Dados insuficientes" description="Seu desempenho por mercado aparecerá após os primeiros palpites." />}</article>
      </section>
    </>
  );
}

export function AchievementsPage() {
  const { data, loading, error, reload } = useApiResource(async () => asList(await achievementsApi.list()), []);
  if (loading) return <PageSkeleton cards={6} />;
  if (error) return <ErrorState message={error} onRetry={() => reload().catch(() => undefined)} />;
  const achievements = data || [];
  const unlocked = achievements.filter((item) => item.unlocked || item.unlockedAt);
  return (
    <>
      <PageHeader eyebrow="SUA TRAJETÓRIA" title="Conquistas" description="Marcos desbloqueados por regras reais do produto, da primeira leitura às grandes sequências." />
      <section className="achievement-summary surface"><div><span><Award size={25} /></span><div><small>Desbloqueadas</small><strong>{unlocked.length} <em>de {achievements.length}</em></strong></div></div><Progress value={unlocked.length} max={Math.max(achievements.length, 1)} label="Progresso da coleção" /><div><small>Pontos recebidos</small><strong>{points(unlocked.reduce((sum, item) => sum + Number(item.pointsReward || 0), 0))} pts</strong></div></section>
      {achievements.length ? <div className="achievement-grid">{achievements.map((item: Achievement) => <article className={`surface achievement-card ${item.unlocked || item.unlockedAt ? "achievement-card--unlocked" : ""}`} key={item.id}><span className="achievement-card__icon">{item.unlocked || item.unlockedAt ? <Award size={27} /> : <ShieldCheck size={27} />}</span><div><small>{item.rarity || "Conquista"}</small><h2>{item.name}</h2><p>{item.description}</p></div>{item.target !== undefined && <Progress value={Number(item.progress || 0)} max={Number(item.target || 1)} label={`${points(item.progress || 0)} / ${points(item.target)}`} />}<footer>{item.unlocked || item.unlockedAt ? <span><Sparkles size={14} /> Desbloqueada</span> : <span>Continue evoluindo</span>}<strong>+{points(item.pointsReward || 0)} pts</strong></footer></article>)}</div> : <EmptyState icon={Award} title="Catálogo em preparação" description="As conquistas configuradas pela organização aparecerão aqui." />}
    </>
  );
}
