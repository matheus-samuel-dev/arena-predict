import {
  Activity,
  ArrowRight,
  Award,
  BarChart3,
  CalendarClock,
  Flame,
  Gauge,
  Medal,
  ShieldCheck,
  Sparkles,
  Target,
  Trophy,
  WalletCards,
  Zap,
} from "lucide-react";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { dateTime, percentage, points, predictionStatusLabel } from "../app/format";
import { EventCard, FeaturedEventCard } from "../components/EventCard";
import { PredictionComposer } from "../components/PredictionComposer";
import { Avatar, EmptyState, ErrorState, PageHeader, PageSkeleton, Progress, SectionHeader, StatusBadge } from "../components/UI";
import { useAuth } from "../contexts/AuthContext";
import { useApiResource } from "../hooks/useApiResource";
import { dashboardApi } from "../services/api";
import type { ArenaEvent, DashboardData, PredictionDraft } from "../types";

type LooseDashboard = DashboardData & {
  myPoints?: number;
  myPosition?: number;
  participants?: number;
  predictionsSent?: number;
  predictionsExpected?: number;
  nextMatches?: ArenaEvent[];
  nextMatch?: ArenaEvent;
};

export function normalizeDashboardProgression(data: Pick<DashboardData, "level" | "levelTitle" | "xp" | "nextLevelXp">) {
  const parsedLevel = Number(data.level);
  const level = Number.isFinite(parsedLevel) ? Math.max(1, Math.floor(parsedLevel)) : 1;
  const legacyTitle = typeof data.level === "string" && !Number.isFinite(parsedLevel) ? data.level.trim() : "";
  const xpValue = Number(data.xp);
  const nextLevelValue = Number(data.nextLevelXp);
  return {
    level,
    levelTitle: String(data.levelTitle || legacyTitle || "Competidor"),
    xp: Number.isFinite(xpValue) ? Math.max(0, xpValue) : 0,
    nextLevelXp: Number.isFinite(nextLevelValue) ? Math.max(0, nextLevelValue) : 0,
  };
}

export function localDashboardLevelProgress(data: ReturnType<typeof normalizeDashboardProgression>) {
  if (data.nextLevelXp <= 0) return { currentXp: 0, neededXp: 1, remainingXp: 0 };
  const levelSpan = data.nextLevelXp / Math.max(1, data.level);
  const levelStartXp = Math.max(0, data.nextLevelXp - levelSpan);
  const neededXp = Math.max(1, data.nextLevelXp - levelStartXp);
  const currentXp = Math.min(neededXp, Math.max(0, data.xp - levelStartXp));
  return {
    currentXp,
    neededXp,
    remainingXp: Math.max(0, data.nextLevelXp - data.xp),
  };
}

export function DashboardPage() {
  const { user } = useAuth();
  const { data, loading, error, reload } = useApiResource<LooseDashboard>(() => dashboardApi.get(), []);
  const [draft, setDraft] = useState<PredictionDraft | null>(null);

  const content = useMemo(() => {
    if (!data) return null;
    const available = Number(data.availablePoints ?? data.points ?? data.myPoints ?? 0);
    const rankingPosition = Number(data.rankingPosition ?? data.position ?? data.myPosition ?? 0);
    const predictionsExpected = Number(data.predictionsExpected ?? 0);
    const predictionsSent = Number(data.predictionsSent ?? 0);
    const accuracy = Number(data.accuracy ?? (predictionsExpected ? (predictionsSent / predictionsExpected) * 100 : 0));
    const upcoming = data.upcomingEvents || data.nextMatches || (data.nextMatch ? [data.nextMatch] : []);
    return { available, rankingPosition, accuracy, upcoming };
  }, [data]);

  if (loading) return <PageSkeleton cards={6} />;
  if (error || !data || !content) return <ErrorState message={error || "O dashboard não retornou dados."} onRetry={() => reload().catch(() => undefined)} />;

  const featuredEvents = data.featuredEvents || content.upcoming.filter((event) => event.featured).slice(0, 2);
  const liveEvents = data.liveEvents || [];
  const predictions = data.recentPredictions || data.predictions || [];
  const pools = Array.isArray(data.activePools) ? data.activePools : data.pools || [];
  const ranking = data.weeklyRanking || data.ranking || [];
  const challenges = data.challenges || [];
  const achievements = data.recentAchievements || [];
  const performance = data.performance || [];
  const name = user?.name.split(" ")[0] || "Participante";
  const progression = normalizeDashboardProgression(data);
  const levelProgress = localDashboardLevelProgress(progression);
  const finishedCount = Number(data.finishedPredictions ?? data.settledPredictions ?? 0);
  const streak = Number(data.streak || 0);

  return (
    <>
      <PageHeader
        eyebrow="CENTRAL DO PARTICIPANTE"
        title={`Boa leitura, ${name}.`}
        description="Seu momento na arena, eventos importantes e próximos desafios em um só lugar."
        actions={<Link className="button button--secondary button--md" to="/events"><Target size={17} /> Explorar eventos</Link>}
      />

      <section className="player-strip surface">
        <div className="player-strip__identity">
          <Avatar name={user?.name} image={user?.avatarUrl} size="lg" />
          <div><span>Nível {progression.level} · {progression.levelTitle}</span><strong>{user?.name}</strong><small>{points(progression.xp)} XP conquistados</small></div>
        </div>
        <div className="player-strip__xp">
          <Progress
            value={levelProgress.currentXp}
            max={levelProgress.neededXp}
            label={progression.nextLevelXp > 0 ? `${points(levelProgress.currentXp)} de ${points(levelProgress.neededXp)} XP no nível · faltam ${points(levelProgress.remainingXp)} XP` : "Meta do próximo nível ainda não publicada"}
          />
        </div>
        <div className="player-strip__rank"><Medal size={20} /><span><small>Ranking geral</small><strong>{content.rankingPosition ? `#${content.rankingPosition}` : "—"}</strong></span></div>
      </section>

      <section className="metric-grid metric-grid--dashboard" aria-label="Seus indicadores">
        {[
          { label: "Pontos disponíveis", value: `${points(content.available)} pts`, detail: "saldo exclusivamente virtual", icon: WalletCards, tone: "violet" },
          { label: "Precisão", value: percentage(content.accuracy), detail: `${finishedCount} ${finishedCount === 1 ? "palpite encerrado" : "palpites encerrados"}`, icon: Gauge, tone: "blue" },
          { label: "Sequência", value: `${streak} ${streak === 1 ? "acerto" : "acertos"}`, detail: "melhor momento recente", icon: Flame, tone: "orange" },
          { label: "Palpites ativos", value: points(data.activePredictions || predictions.filter((item) => ["ACTIVE", "ATIVO", "PENDING", "PENDENTE"].includes(String(item.status).toUpperCase())).length), detail: "aguardando resultado", icon: Target, tone: "green" },
        ].map(({ label, value, detail, icon: Icon, tone }) => (
          <article className={`surface metric-card metric-card--${tone}`} key={label}>
            <span className="metric-card__icon"><Icon size={21} /></span>
            <div><small>{label}</small><strong>{value}</strong><span>{detail}</span></div>
          </article>
        ))}
      </section>

      {featuredEvents.length > 0 && (
        <section className="dashboard-section">
          <SectionHeader title="Na mira da Arena" description="Eventos em destaque escolhidos pela curadoria." link="/events" />
          <div className="featured-grid">{featuredEvents.slice(0, 2).map((event) => <FeaturedEventCard key={event.id} event={event} />)}</div>
        </section>
      )}

      <section className="dashboard-layout">
        <div className="dashboard-layout__main">
          <section className="dashboard-section">
            <SectionHeader title="Acontecendo agora" description="Eventos em andamento e seus mercados." link="/live" linkLabel="Abrir central ao vivo" />
            {liveEvents.length ? (
              <div className="event-list">{liveEvents.slice(0, 3).map((event) => <EventCard event={event} onPredict={setDraft} key={event.id} />)}</div>
            ) : (
              <EmptyState icon={Activity} title="Sem eventos ao vivo neste momento" description="Veja os próximos eventos e deixe seus palpites prontos antes do fechamento." action={<Link className="button button--secondary button--md" to="/events">Ver agenda</Link>} />
            )}
          </section>

          <section className="dashboard-section">
            <SectionHeader title="Próximos eventos" description="Não perca o limite dos seus mercados favoritos." link="/events" />
            {content.upcoming.length ? (
              <div className="compact-event-grid">{content.upcoming.slice(0, 4).map((event) => <EventCard event={event} onPredict={setDraft} compact key={event.id} />)}</div>
            ) : (
              <EmptyState icon={CalendarClock} title="Agenda em preparação" description="Novos eventos aparecerão assim que forem publicados pela organização." />
            )}
          </section>

          <section className="surface chart-panel">
            <SectionHeader title="Evolução de desempenho" description="Precisão nos últimos ciclos de atividade." link="/statistics" linkLabel="Análise completa" />
            {performance.length ? (
              <div className="performance-chart" aria-label="Gráfico de desempenho">
                <div className="chart-grid-lines"><span /><span /><span /><span /></div>
                <div className="chart-bars">
                  {performance.slice(-12).map((item, index) => {
                    const value = Number(item.value ?? item.accuracy ?? 0);
                    return <div key={`${item.label || item.date}-${index}`}><span style={{ height: `${Math.max(5, Math.min(100, value))}%` }} title={`${value}%`} /><small>{item.label || (item.date ? dateTime(item.date).split(" ")[0] : index + 1)}</small></div>;
                  })}
                </div>
              </div>
            ) : (
              <EmptyState icon={BarChart3} title="Seu gráfico começa com o primeiro resultado" description="Quando seus palpites forem processados, a evolução será exibida aqui." />
            )}
          </section>
        </div>

        <aside className="dashboard-layout__side">
          <section className="surface side-panel">
            <SectionHeader title="Meus palpites" link="/predictions" />
            {predictions.length ? predictions.slice(0, 4).map((prediction) => (
              <Link className="mini-prediction" to="/predictions" key={prediction.id}>
                <span className="mini-prediction__icon"><Target size={16} /></span>
                <span><strong>{prediction.optionLabel || prediction.optionName || prediction.eventTitle || "Palpite"}</strong><small>{prediction.marketName || `${points(prediction.stakePoints || prediction.points)} pts utilizados`}</small></span>
                <StatusBadge status={prediction.status} label={predictionStatusLabel(prediction.status)} />
              </Link>
            )) : <EmptyState icon={Target} title="Nenhum palpite ativo" description="Escolha um evento para entrar na disputa." />}
          </section>

          <section className="surface side-panel">
            <SectionHeader title="Ranking semanal" link="/rankings" />
            {ranking.length ? <div className="mini-ranking">{ranking.slice(0, 6).map((row) => (
              <div className={row.currentUser ? "current" : ""} key={row.userId || row.position}>
                <b>{row.position}</b><Avatar name={row.name || row.participant} size="sm" /><span><strong>{row.name || row.participant}</strong><small>{row.hits || 0} acertos</small></span><em>{points(row.points)}</em>
              </div>
            ))}</div> : <EmptyState icon={Trophy} title="Ranking sendo formado" description="As posições aparecem após o processamento dos primeiros eventos." />}
          </section>

          {challenges[0] && (
            <section className="surface challenge-card">
              <div className="challenge-card__head"><span><Zap size={19} /></span><div><small>Desafio em destaque</small><strong>{challenges[0].name}</strong></div></div>
              <p>{challenges[0].description}</p>
              <Progress value={Number(challenges[0].progress || 0)} max={Number(challenges[0].target || 1)} />
              <footer><span>{points(challenges[0].progress || 0)}/{points(challenges[0].target || 0)}</span><strong><Sparkles size={14} /> {points(challenges[0].rewardPoints || 0)} pts</strong></footer>
              <Link className="text-link" to="/challenges">Ver todos os desafios <ArrowRight size={15} /></Link>
            </section>
          )}

          {pools.length > 0 && (
            <section className="surface side-panel">
              <SectionHeader title="Bolões em andamento" link="/pools" />
              <div className="pool-mini-list">{pools.slice(0, 3).map((pool) => <Link to={`/pools?selected=${pool.id}`} key={pool.id}><span><Trophy size={16} /></span><div><strong>{pool.name}</strong><small>{pool.participantCount ?? pool.participants ?? 0} participantes</small></div><ArrowRight size={15} /></Link>)}</div>
            </section>
          )}

          {achievements[0] && (
            <section className="surface achievement-callout">
              <span><Award size={25} /></span><div><small>Conquista recente</small><strong>{achievements[0].name}</strong><p>{achievements[0].description}</p></div>
            </section>
          )}
        </aside>
      </section>

      <div className="virtual-footer-note"><ShieldCheck size={15} /> {data.virtualPointsNotice || "Todos os pontos da plataforma são virtuais e não possuem valor financeiro."}</div>
      <PredictionComposer draft={draft} onClose={() => setDraft(null)} onCreated={() => reload().catch(() => undefined)} />
    </>
  );
}
