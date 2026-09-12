import { Activity, Award, Bell, CheckCheck, Flame, Gauge, KeyRound, LockKeyhole, Mail, Moon, Save, ShieldCheck, Target, Trophy, UserCircle } from "lucide-react";
import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { dateTime, percentage, points, predictionStatusLabel, relativeTime } from "../app/format";
import { enumLabel } from "../app/presentation";
import { Button, EmptyState, ErrorState, PageHeader, PageSkeleton, Progress, UserAvatar } from "../components/UI";
import { USER_AVATAR_OPTIONS } from "../components/UserAvatar";
import { useAppData } from "../contexts/AppDataContext";
import { useAuth } from "../contexts/AuthContext";
import { useToast } from "../contexts/ToastContext";
import { useApiResource } from "../hooks/useApiResource";
import { achievementsApi, asList, dashboardApi, profileApi } from "../services/api";
import { getStoredTheme, setTheme, type Theme } from "../app/theme";
import { ThemeSelector } from "../components/ThemeSelector";

export function NotificationsPage() {
  const { notifications, notificationsError, unreadCount, loading, refreshNotifications, markNotificationRead, markAllNotificationsRead } = useAppData();
  const { notify } = useToast();
  const navigate = useNavigate();

  async function open(id: number | string, link?: string) {
    try {
      await markNotificationRead(id);
      if (link?.startsWith("/")) navigate(link);
    } catch (error) {
      notify(error instanceof Error ? error.message : "Não foi possível atualizar.", "error");
    }
  }

  async function all() {
    try {
      await markAllNotificationsRead();
      notify("Todas as notificações foram marcadas como lidas.", "success");
    } catch (error) {
      notify(error instanceof Error ? error.message : "Não foi possível atualizar.", "error");
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="CENTRAL DE ATUALIZAÇÕES"
        title="Notificações"
        description="Resultados, conquistas, convites e atividades relevantes para você."
        actions={<Button variant="secondary" onClick={all} disabled={!unreadCount || loading}><CheckCheck size={17} /> Marcar todas como lidas</Button>}
      />
      {notificationsError && !notifications.length && <ErrorState message={notificationsError} onRetry={() => refreshNotifications().catch(() => undefined)} />}
      {notificationsError && notifications.length > 0 && <div className="virtual-footer-note" role="status">Exibindo a última leitura disponível. Não foi possível buscar novas notificações.</div>}
      {notifications.length ? (
        <div className="notification-page-list">
          {notifications.map((item) => (
            <button type="button" className={`surface notification-page-row ${!item.read && !item.readAt ? "unread" : ""}`} key={item.id} onClick={() => open(item.id, item.link)}>
              <span className="notification-page-row__icon"><Bell size={18} /></span>
              <div><span>{enumLabel(item.type || "UPDATE")}</span><strong>{item.title}</strong><p>{item.message}</p><small>{relativeTime(item.createdAt)}</small></div>
              {!item.read && !item.readAt && <i />}
            </button>
          ))}
        </div>
      ) : !notificationsError && <EmptyState icon={Bell} title="Tudo em dia" description="Seus resultados, convites e conquistas aparecerão aqui." />}
    </>
  );
}

function safeMetric(value: unknown, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, parsed) : fallback;
}

export function ProfilePage() {
  const { updateLocalUser } = useAuth();
  const { notify } = useToast();
  const { data: profile, setData: setProfile, loading, error, reload } = useApiResource(() => profileApi.get(), []);
  const {
    data: insights,
    loading: insightsLoading,
    error: insightsError,
  } = useApiResource(async () => {
    const [dashboard, achievements] = await Promise.all([dashboardApi.get(), achievementsApi.list()]);
    return { dashboard, achievements: asList(achievements) };
  }, []);
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get("tab");
  const [tab, setTab] = useState<"profile" | "security" | "preferences">(
    requestedTab === "security" || requestedTab === "preferences" ? requestedTab : "profile",
  );
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [avatarUrl, setAvatarUrl] = useState("");
  const [bio, setBio] = useState("");
  const [favoriteSports, setFavoriteSports] = useState("");
  const [saving, setSaving] = useState(false);
  const [avatarSaving, setAvatarSaving] = useState(false);
  const avatarRequestRef = useRef(false);
  const preserveDraftOnProfileSync = useRef(false);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [notifications, setNotifications] = useState(true);
  const [publicProfile, setPublicProfile] = useState(true);
  const [theme, setThemeState] = useState<Theme>(() => getStoredTheme());

  useEffect(() => {
    if (requestedTab === "security" || requestedTab === "preferences") setTab(requestedTab);
    else setTab("profile");
  }, [requestedTab]);

  useEffect(() => {
    if (!profile) return;
    if (preserveDraftOnProfileSync.current) {
      preserveDraftOnProfileSync.current = false;
      setAvatarUrl(profile.avatarUrl || "");
      return;
    }
    setName(profile.name || "");
    setEmail(profile.email || "");
    setAvatarUrl(profile.avatarUrl || "");
    setBio(profile.bio || "");
    setFavoriteSports((profile.favoriteSports || []).join(", "));
    setNotifications(profile.notifications !== false);
    setPublicProfile(profile.publicProfile !== false);
  }, [profile]);

  async function saveProfile(event: FormEvent) {
    event.preventDefault();
    if (!profile || saving || avatarSaving) return;
    const sports = favoriteSports.split(",").map((value) => value.trim()).filter(Boolean).slice(0, 12);
    setSaving(true);
    try {
      const updated = await profileApi.update({
        name: name.trim(),
        email: email.trim(),
        avatarUrl: avatarUrl.trim() || null,
        bio: bio.trim() || null,
        favoriteSports: sports,
        publicProfile,
      });
      setProfile(updated);
      updateLocalUser({ name: updated.name, email: updated.email, avatarUrl: updated.avatarUrl });
      notify("Perfil atualizado.", "success");
    } catch (reason) {
      notify(reason instanceof Error ? reason.message : "Não foi possível salvar.", "error");
    } finally {
      setSaving(false);
    }
  }

  async function selectAvatar(nextAvatarUrl: string) {
    if (!profile || saving || avatarRequestRef.current || nextAvatarUrl === avatarUrl) return;
    avatarRequestRef.current = true;
    setAvatarSaving(true);
    try {
      const updated = await profileApi.update({
        name: profile.name,
        email: profile.email,
        avatarUrl: nextAvatarUrl,
        bio: profile.bio || null,
        favoriteSports: profile.favoriteSports || [],
        publicProfile: profile.publicProfile,
      });
      preserveDraftOnProfileSync.current = true;
      setProfile(updated);
      setAvatarUrl(updated.avatarUrl || nextAvatarUrl);
      updateLocalUser({ name: updated.name, email: updated.email, avatarUrl: updated.avatarUrl || nextAvatarUrl });
      notify("Avatar atualizado em toda a ArenaPredict.", "success");
    } catch (reason) {
      notify(reason instanceof Error ? reason.message : "Não foi possível atualizar o avatar.", "error");
    } finally {
      avatarRequestRef.current = false;
      setAvatarSaving(false);
    }
  }

  async function password(event: FormEvent) {
    event.preventDefault();
    if (saving || avatarSaving) return;
    if (newPassword.length < 8) {
      notify("A nova senha deve ter ao menos 8 caracteres.", "error");
      return;
    }
    setSaving(true);
    try {
      await profileApi.changePassword({ currentPassword, newPassword });
      setCurrentPassword("");
      setNewPassword("");
      notify("Senha alterada com segurança.", "success");
    } catch (reason) {
      notify(reason instanceof Error ? reason.message : "Não foi possível alterar a senha.", "error");
    } finally {
      setSaving(false);
    }
  }

  async function savePreferences(event: FormEvent) {
    event.preventDefault();
    if (saving || avatarSaving) return;
    setSaving(true);
    try {
      const updated = await profileApi.preferences({ theme, language: "pt-BR", notifications, publicProfile });
      setProfile((current) => current ? { ...current, ...updated } : current);
      setNotifications(updated.notifications);
      setPublicProfile(updated.publicProfile);
      setTheme(theme);
      notify("Preferências atualizadas.", "success");
    } catch (reason) {
      notify(reason instanceof Error ? reason.message : "Não foi possível salvar as preferências.", "error");
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <PageSkeleton cards={3} />;
  if (error || !profile) {
    return (
      <>
        <PageHeader eyebrow="CENTRAL DO PARTICIPANTE" title="Minha conta" description="Seus dados, desempenho, preferências e segurança em um só lugar." />
        <ErrorState message={error || "O perfil não retornou dados."} onRetry={() => reload().catch(() => undefined)} />
      </>
    );
  }

  const level = Math.max(1, Math.floor(safeMetric(profile.level, 1)));
  const xp = safeMetric(profile.xp);
  const virtualPoints = safeMetric(profile.points);
  const accountIdentifier = `@${profile.email.split("@")[0].replace(/[^a-z0-9._-]/gi, "") || "participante"}`;
  const dashboard = insights?.dashboard;
  const totalPredictions = safeMetric(dashboard?.activePredictions) + safeMetric(dashboard?.finishedPredictions);
  const wonPredictions = safeMetric(dashboard?.wonPredictions);
  const currentStreak = safeMetric(dashboard?.streak);
  const bestStreak = Math.max(currentStreak, safeMetric(dashboard?.bestStreak, currentStreak));
  const nextLevelXp = Math.max(xp + 1, safeMetric(dashboard?.nextLevelXp, level * 5_000));
  const levelStartXp = Math.max(0, (level - 1) * 5_000);
  const xpInCurrentLevel = Math.max(0, xp - levelStartXp);
  const xpRequiredInLevel = Math.max(1, nextLevelXp - levelStartXp);
  const recentAchievements = (insights?.achievements || []).filter((item) => item.unlocked || item.unlockedAt).slice(0, 3);
  const recentActivity = (dashboard?.recentPredictions || []).slice(0, 4);
  const profileTabs = [
    { id: "profile" as const, label: "Dados pessoais", icon: UserCircle },
    { id: "security" as const, label: "Segurança", icon: LockKeyhole },
    { id: "preferences" as const, label: "Preferências", icon: Moon },
  ];

  function selectTab(nextTab: typeof tab) {
    setTab(nextTab);
    setSearchParams(nextTab === "profile" ? {} : { tab: nextTab }, { replace: true });
  }

  function navigateProfileTabs(event: KeyboardEvent<HTMLButtonElement>, currentTab: typeof tab) {
    const currentIndex = profileTabs.findIndex(({ id }) => id === currentTab);
    let nextIndex: number;
    if (event.key === "ArrowRight") nextIndex = (currentIndex + 1) % profileTabs.length;
    else if (event.key === "ArrowLeft") nextIndex = (currentIndex - 1 + profileTabs.length) % profileTabs.length;
    else if (event.key === "Home") nextIndex = 0;
    else if (event.key === "End") nextIndex = profileTabs.length - 1;
    else return;
    event.preventDefault();
    const nextTab = profileTabs[nextIndex].id;
    selectTab(nextTab);
    window.requestAnimationFrame(() => document.getElementById(`profile-tab-${nextTab}`)?.focus());
  }

  return (
    <>
      <PageHeader eyebrow="CENTRAL DO PARTICIPANTE" title="Minha conta" description="Acompanhe sua identidade, desempenho, preferências e segurança em um só lugar." />
      <section className="profile-hero surface">
        <UserAvatar name={profile.name} avatarUrl={avatarUrl || profile.avatarUrl} size="xl" loading="eager" aria-label={`Avatar de ${profile.name}`} />
        <div className="profile-hero__identity"><span>{profile.role === "ADMIN" ? "Administrador" : "Participante"}</span><h2>{profile.name}</h2><p>{accountIdentifier} <i aria-hidden="true">•</i> {profile.email}</p>{profile.createdAt && <small>Membro desde {dateTime(profile.createdAt)}</small>}</div>
        <div className="profile-hero__progress"><small>Nível {level}</small><strong>{points(xp)} XP</strong><Progress value={xpInCurrentLevel} max={xpRequiredInLevel} label={`Progresso para o nível ${level + 1}`} /><small>{points(xpInCurrentLevel)} de {points(xpRequiredInLevel)} XP neste nível</small></div>
        <div><small>Pontos virtuais</small><strong>{points(virtualPoints)}</strong></div>
      </section>
      <section className="account-metrics" aria-label="Resumo de desempenho">
        <article className="surface account-metric"><span><Target size={19} /></span><div><small>Palpites feitos</small><strong>{insightsLoading ? "—" : points(totalPredictions)}</strong><p>{points(safeMetric(dashboard?.activePredictions))} aguardando resultado</p></div></article>
        <article className="surface account-metric"><span><Award size={19} /></span><div><small>Palpites vencedores</small><strong>{insightsLoading ? "—" : points(wonPredictions)}</strong><p>resultados encerrados com acerto</p></div></article>
        <article className="surface account-metric"><span><Gauge size={19} /></span><div><small>Taxa de acerto</small><strong>{insightsLoading ? "—" : percentage(dashboard?.accuracy)}</strong><p>desempenho em palpites encerrados</p></div></article>
        <article className="surface account-metric"><span><Flame size={19} /></span><div><small>Sequência atual</small><strong>{insightsLoading ? "—" : `${points(currentStreak)} acerto${currentStreak === 1 ? "" : "s"}`}</strong><p>Melhor sequência: {insightsLoading ? "—" : points(bestStreak)}</p></div></article>
        <article className="surface account-metric"><span><Trophy size={19} /></span><div><small>Ranking geral</small><strong>{insightsLoading ? "—" : dashboard?.rankingPosition ? `#${dashboard.rankingPosition}` : "Em formação"}</strong><p>posição na comunidade</p></div></article>
      </section>
      <div className="account-overview-grid">
        <section className="surface account-insight-card">
          <header><div><span><Award size={19} /></span><div><small>PROGRESSÃO</small><h2>Conquistas recentes</h2></div></div><Link to="/achievements">Ver catálogo</Link></header>
          {recentAchievements.length ? <div className="account-achievement-list">{recentAchievements.map((achievement) => <article key={achievement.id}><span><Award size={18} /></span><div><strong>{achievement.name}</strong><p>{achievement.description}</p></div><em>+{points(achievement.pointsReward)} pts</em></article>)}</div> : <p className="account-insight-empty">{insightsLoading ? "Carregando suas conquistas..." : "Continue participando para desbloquear novas conquistas."}</p>}
        </section>
        <section className="surface account-insight-card">
          <header><div><span><Activity size={19} /></span><div><small>HISTÓRICO</small><h2>Atividade recente</h2></div></div><Link to="/predictions">Ver palpites</Link></header>
          {recentActivity.length ? <div className="account-activity-list">{recentActivity.map((prediction) => <article key={prediction.id}><span><Target size={17} /></span><div><strong>{prediction.eventTitle || "Evento esportivo"}</strong><p>{prediction.optionName || prediction.optionLabel || "Opção registrada"} · {points(prediction.stakePoints ?? prediction.points)} pts</p></div><div><em>{predictionStatusLabel(prediction.status)}</em><small>{relativeTime(prediction.placedAt || prediction.createdAt)}</small></div></article>)}</div> : <p className="account-insight-empty">{insightsLoading ? "Carregando sua atividade..." : "Seus próximos palpites aparecerão neste histórico."}</p>}
        </section>
      </div>
      {insightsError && <div className="virtual-footer-note" role="status">Os dados da conta estão disponíveis; o resumo de desempenho não pôde ser atualizado agora.</div>}
      <div className="profile-layout">
        <nav className="surface profile-tabs" role="tablist" aria-label="Seções do perfil">
          {profileTabs.map(({ id, label, icon: Icon }) => (
            <button id={`profile-tab-${id}`} type="button" role="tab" aria-selected={tab === id} aria-controls={`profile-panel-${id}`} tabIndex={tab === id ? 0 : -1} className={tab === id ? "active" : ""} onKeyDown={(event) => navigateProfileTabs(event, id)} onClick={() => selectTab(id)} key={id}><Icon size={17} aria-hidden="true" /> {label}</button>
          ))}
        </nav>
        <section className="surface profile-form-panel" id={`profile-panel-${tab}`} role="tabpanel" aria-labelledby={`profile-tab-${tab}`} tabIndex={0}>
          {tab === "profile" && (
            <form className="stack-form" onSubmit={saveProfile}>
              <div><h2>Dados pessoais</h2><p>Essas informações identificam você na comunidade e nos rankings.</p></div>
              <label><span>Nome de exibição</span><input required minLength={2} value={name} onChange={(event) => setName(event.target.value)} /></label>
              <label><span>E-mail</span><div className="field"><Mail size={17} /><input required readOnly type="email" value={email} aria-describedby="email-help" /></div><small id="email-help">O e-mail de acesso é protegido; a troca exige um fluxo de reautenticação.</small></label>
              <fieldset className="avatar-gallery" disabled={saving || avatarSaving} aria-describedby="avatar-gallery-help">
                <legend>Avatar do perfil</legend>
                <p id="avatar-gallery-help">Escolha um dos retratos visuais da ArenaPredict. A alteração é salva imediatamente.</p>
                <div className="avatar-gallery__options" role="group" aria-label="Avatares disponíveis">
                  {USER_AVATAR_OPTIONS.map((option) => {
                    const selected = avatarUrl === option.src;
                    return (
                      <button
                        key={option.id}
                        type="button"
                        className={`avatar-gallery__option ${selected ? "selected" : ""}`}
                        aria-label={`${selected ? "Avatar selecionado" : "Selecionar"}: ${option.label}`}
                        aria-pressed={selected}
                        disabled={saving || avatarSaving}
                        onClick={() => void selectAvatar(option.src)}
                      >
                        <UserAvatar avatarUrl={option.src} name={option.label} size="lg" loading="lazy" />
                        <span>{option.label}</span>
                        {selected && <strong aria-hidden="true">Selecionado</strong>}
                      </button>
                    );
                  })}
                </div>
                <p className="avatar-gallery__status" role="status" aria-live="polite">{avatarSaving ? "Atualizando avatar..." : "O avatar escolhido também aparece no header, na comunidade e nos rankings."}</p>
              </fieldset>
              <label><span>Biografia</span><textarea maxLength={500} value={bio} onChange={(event) => setBio(event.target.value)} placeholder="Conte um pouco sobre sua trajetória na Arena." /></label>
              <label><span>Modalidades favoritas</span><input value={favoriteSports} onChange={(event) => setFavoriteSports(event.target.value)} placeholder="Futebol, CS2, Tênis" /><small>Separe até 12 modalidades por vírgulas.</small></label>
              <div className="virtual-disclaimer"><ShieldCheck size={16} /> Seu e-mail não é exibido publicamente.</div>
              <div className="form-actions"><Button type="submit" loading={saving} disabled={avatarSaving}><Save size={16} /> Salvar alterações</Button></div>
            </form>
          )}
          {tab === "security" && (
            <form className="stack-form" onSubmit={password}>
              <div><h2>Alterar senha</h2><p>Use uma senha exclusiva e evite reutilizar credenciais.</p></div>
              <label><span>Senha atual</span><input required type="password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} autoComplete="current-password" /></label>
              <label><span>Nova senha</span><input required minLength={8} type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} autoComplete="new-password" /></label>
              <div className="form-actions"><Button type="submit" loading={saving} disabled={avatarSaving}><KeyRound size={16} /> Atualizar senha</Button></div>
            </form>
          )}
          {tab === "preferences" && (
            <form className="stack-form" onSubmit={savePreferences}>
              <div><h2>Preferências</h2><p>Gerencie privacidade e notificações da sua experiência.</p></div>
              <div className="preference-lock"><ShieldCheck size={19} aria-hidden="true" /><span><strong>Experiência ArenaPredict</strong><small>Escolha uma aparência confortável. O português do Brasil é mantido em toda a plataforma.</small></span></div>
              <ThemeSelector value={theme} onChange={setThemeState} />
              <label className="toggle-row"><span><Bell size={17} /><span><strong>Notificações da plataforma</strong><small>Resultados, convites, conquistas e avisos.</small></span></span><input type="checkbox" checked={notifications} onChange={(event) => setNotifications(event.target.checked)} /></label>
              <label className="toggle-row"><span><UserCircle size={17} /><span><strong>Perfil público</strong><small>Permite que outros participantes vejam suas estatísticas.</small></span></span><input type="checkbox" checked={publicProfile} onChange={(event) => setPublicProfile(event.target.checked)} /></label>
              <div className="form-actions"><Button type="submit" loading={saving} disabled={avatarSaving}><Save size={16} /> Salvar preferências</Button></div>
            </form>
          )}
        </section>
      </div>
    </>
  );
}
