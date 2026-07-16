import { Bell, CheckCheck, KeyRound, LockKeyhole, Mail, Moon, Save, ShieldCheck, UserCircle } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { brand } from "../app/branding";
import { points, relativeTime } from "../app/format";
import { Avatar, Button, EmptyState, ErrorState, PageHeader, PageSkeleton } from "../components/UI";
import { useAppData } from "../contexts/AppDataContext";
import { useAuth } from "../contexts/AuthContext";
import { useToast } from "../contexts/ToastContext";
import { useApiResource } from "../hooks/useApiResource";
import { profileApi } from "../services/api";

export function NotificationsPage() {
  const { notifications, unreadCount, loading, markNotificationRead, markAllNotificationsRead } = useAppData();
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
      {notifications.length ? (
        <div className="notification-page-list">
          {notifications.map((item) => (
            <button type="button" className={`surface notification-page-row ${!item.read && !item.readAt ? "unread" : ""}`} key={item.id} onClick={() => open(item.id, item.link)}>
              <span className="notification-page-row__icon"><Bell size={18} /></span>
              <div><span>{item.type || "Atualização"}</span><strong>{item.title}</strong><p>{item.message}</p><small>{relativeTime(item.createdAt)}</small></div>
              {!item.read && !item.readAt && <i />}
            </button>
          ))}
        </div>
      ) : <EmptyState icon={Bell} title="Tudo em dia" description="Seus resultados, convites e conquistas aparecerão aqui." />}
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
  const [tab, setTab] = useState("profile");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [avatarUrl, setAvatarUrl] = useState("");
  const [bio, setBio] = useState("");
  const [favoriteSports, setFavoriteSports] = useState("");
  const [saving, setSaving] = useState(false);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [theme, setTheme] = useState("dark");
  const [notifications, setNotifications] = useState(true);
  const [language, setLanguage] = useState("pt-BR");
  const [publicProfile, setPublicProfile] = useState(true);

  useEffect(() => {
    if (!profile) return;
    setName(profile.name || "");
    setEmail(profile.email || "");
    setAvatarUrl(profile.avatarUrl || "");
    setBio(profile.bio || "");
    setFavoriteSports((profile.favoriteSports || []).join(", "));
    setTheme(profile.theme || "dark");
    setLanguage(profile.language || "pt-BR");
    setNotifications(profile.notifications !== false);
    setPublicProfile(profile.publicProfile !== false);
  }, [profile]);

  async function saveProfile(event: FormEvent) {
    event.preventDefault();
    if (!profile) return;
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

  async function password(event: FormEvent) {
    event.preventDefault();
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
    setSaving(true);
    try {
      const updated = await profileApi.preferences({ theme, language, notifications, publicProfile });
      setProfile((current) => current ? { ...current, ...updated } : current);
      setTheme(updated.theme);
      setLanguage(updated.language);
      setNotifications(updated.notifications);
      setPublicProfile(updated.publicProfile);
      localStorage.setItem(`${brand.storageNamespace}:theme`, updated.theme);
      document.documentElement.dataset.theme = updated.theme;
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
        <PageHeader eyebrow="IDENTIDADE DO JOGADOR" title="Perfil" description="Gerencie seus dados, segurança e preferências da experiência." />
        <ErrorState message={error || "O perfil não retornou dados."} onRetry={() => reload().catch(() => undefined)} />
      </>
    );
  }

  const level = Math.max(1, Math.floor(safeMetric(profile.level, 1)));
  const xp = safeMetric(profile.xp);
  const virtualPoints = safeMetric(profile.points);

  return (
    <>
      <PageHeader eyebrow="IDENTIDADE DO JOGADOR" title="Perfil" description="Gerencie seus dados, segurança e preferências da experiência." />
      <section className="profile-hero surface">
        <Avatar name={profile.name} image={profile.avatarUrl} size="lg" />
        <div><span>{profile.role === "ADMIN" ? "Administrador" : "Participante"}</span><h2>{profile.name}</h2><p>{profile.email}</p></div>
        <div><small>Nível</small><strong>{level}</strong><small>{points(xp)} XP acumulados</small></div>
        <div><small>Pontos virtuais</small><strong>{points(virtualPoints)}</strong></div>
      </section>
      <div className="profile-layout">
        <nav className="surface profile-tabs">
          {[
            { id: "profile", label: "Dados pessoais", icon: UserCircle },
            { id: "security", label: "Segurança", icon: LockKeyhole },
            { id: "preferences", label: "Preferências", icon: Moon },
          ].map(({ id, label, icon: Icon }) => (
            <button type="button" className={tab === id ? "active" : ""} onClick={() => setTab(id)} key={id}><Icon size={17} /> {label}</button>
          ))}
        </nav>
        <section className="surface profile-form-panel">
          {tab === "profile" && (
            <form className="stack-form" onSubmit={saveProfile}>
              <div><h2>Dados pessoais</h2><p>Essas informações identificam você na comunidade e nos rankings.</p></div>
              <label><span>Nome de exibição</span><input required minLength={2} value={name} onChange={(event) => setName(event.target.value)} /></label>
              <label><span>E-mail</span><div className="field"><Mail size={17} /><input required readOnly type="email" value={email} aria-describedby="email-help" /></div><small id="email-help">O e-mail de acesso é protegido; a troca exige um fluxo de reautenticação.</small></label>
              <label><span>URL do avatar</span><input type="url" value={avatarUrl} onChange={(event) => setAvatarUrl(event.target.value)} placeholder="https://..." /></label>
              <label><span>Bio</span><textarea maxLength={500} value={bio} onChange={(event) => setBio(event.target.value)} placeholder="Conte um pouco sobre sua trajetória na Arena." /></label>
              <label><span>Modalidades favoritas</span><input value={favoriteSports} onChange={(event) => setFavoriteSports(event.target.value)} placeholder="Futebol, CS2, Tênis" /><small>Separe até 12 modalidades por vírgulas.</small></label>
              <div className="virtual-disclaimer"><ShieldCheck size={16} /> Seu e-mail não é exibido publicamente.</div>
              <div className="form-actions"><Button type="submit" loading={saving}><Save size={16} /> Salvar alterações</Button></div>
            </form>
          )}
          {tab === "security" && (
            <form className="stack-form" onSubmit={password}>
              <div><h2>Alterar senha</h2><p>Use uma senha exclusiva e evite reutilizar credenciais.</p></div>
              <label><span>Senha atual</span><input required type="password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} autoComplete="current-password" /></label>
              <label><span>Nova senha</span><input required minLength={8} type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} autoComplete="new-password" /></label>
              <div className="form-actions"><Button type="submit" loading={saving}><KeyRound size={16} /> Atualizar senha</Button></div>
            </form>
          )}
          {tab === "preferences" && (
            <form className="stack-form" onSubmit={savePreferences}>
              <div><h2>Preferências</h2><p>Personalize idioma, aparência, privacidade e notificações.</p></div>
              <label><span>Tema</span><select value={theme} onChange={(event) => setTheme(event.target.value)}><option value="dark">Escuro Arena</option><option value="light">Claro</option></select></label>
              <label><span>Idioma</span><select value={language} onChange={(event) => setLanguage(event.target.value)}><option value="pt-BR">Português (Brasil)</option><option value="en-US">English (US)</option></select></label>
              <label className="toggle-row"><span><Bell size={17} /><span><strong>Notificações da plataforma</strong><small>Resultados, convites, conquistas e avisos.</small></span></span><input type="checkbox" checked={notifications} onChange={(event) => setNotifications(event.target.checked)} /></label>
              <label className="toggle-row"><span><UserCircle size={17} /><span><strong>Perfil público</strong><small>Permite que outros participantes vejam suas estatísticas.</small></span></span><input type="checkbox" checked={publicProfile} onChange={(event) => setPublicProfile(event.target.checked)} /></label>
              <div className="form-actions"><Button type="submit" loading={saving}><Save size={16} /> Salvar preferências</Button></div>
            </form>
          )}
        </section>
      </div>
    </>
  );
}
