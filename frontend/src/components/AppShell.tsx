import {
  Activity,
  Award,
  BarChart3,
  Bell,
  BookOpen,
  CalendarRange,
  ChevronDown,
  CircleHelp,
  ClipboardCheck,
  Cog,
  Compass,
  Gamepad2,
  Gauge,
  Handshake,
  HeartHandshake,
  Layers3,
  LayoutDashboard,
  LogOut,
  Menu,
  MessageSquareText,
  Moon,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Swords,
  Target,
  Trophy,
  UserCircle,
  Users,
  WalletCards,
  X,
  Zap,
  type LucideIcon,
} from "lucide-react";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { brand, isExplicitDemoMode } from "../app/branding";
import { getWalletBalance, points, relativeTime } from "../app/format";
import { useAppData } from "../contexts/AppDataContext";
import { useAuth } from "../contexts/AuthContext";
import { useToast } from "../contexts/ToastContext";
import { Brand } from "./Brand";
import { Avatar, Button } from "./UI";

interface NavItem {
  label: string;
  to: string;
  icon: LucideIcon;
  end?: boolean;
}

const playerNavigation: Array<{ title?: string; items: NavItem[] }> = [
  {
    items: [
      { label: "Visão geral", to: "/app", icon: LayoutDashboard, end: true },
      { label: "Eventos", to: "/events", icon: Compass },
      { label: "Ao vivo", to: "/live", icon: Activity },
      { label: "Meus palpites", to: "/predictions", icon: Target },
    ],
  },
  {
    title: "Competir",
    items: [
      { label: "Bolões", to: "/pools", icon: Trophy },
      { label: "Ligas", to: "/leagues", icon: Swords },
      { label: "Rankings", to: "/rankings", icon: BarChart3 },
      { label: "Estatísticas", to: "/statistics", icon: Gauge },
      { label: "Conquistas", to: "/achievements", icon: Award },
    ],
  },
  {
    title: "Conectar",
    items: [
      { label: "Comunidade", to: "/community", icon: MessageSquareText },
      { label: "Notificações", to: "/notifications", icon: Bell },
      { label: "Perfil", to: "/profile", icon: UserCircle },
      { label: "Ajuda", to: "/help", icon: CircleHelp },
    ],
  },
];

const adminNavigation: Array<{ title?: string; items: NavItem[] }> = [
  {
    title: "Administração",
    items: [
      { label: "Painel", to: "/admin", icon: ShieldCheck, end: true },
      { label: "Modalidades", to: "/admin/sports", icon: Gamepad2 },
      { label: "Campeonatos", to: "/admin/championships", icon: Trophy },
      { label: "Equipes e participantes", to: "/admin/competitors", icon: Users },
      { label: "Eventos", to: "/admin/events", icon: CalendarRange },
      { label: "Mercados", to: "/admin/markets", icon: SlidersHorizontal },
      { label: "Resultados", to: "/admin/results", icon: ClipboardCheck },
      { label: "Usuários", to: "/admin/users", icon: Users },
      { label: "Bolões", to: "/admin/pools", icon: Layers3 },
      { label: "Pontuação", to: "/admin/scoring-rules", icon: Sparkles },
      { label: "Desafios", to: "/admin/challenges", icon: Target },
      { label: "Conquistas", to: "/admin/achievements", icon: Award },
      { label: "Notificações", to: "/admin/notifications", icon: Bell },
      { label: "Moderação", to: "/admin/moderation", icon: HeartHandshake },
      { label: "Relatórios", to: "/admin/reports", icon: BarChart3 },
      { label: "Auditoria", to: "/admin/audit", icon: BookOpen },
      { label: "Configurações", to: "/admin/settings", icon: Cog },
    ],
  },
];

const pageTitles: Record<string, string> = {
  "/app": "Visão geral",
  "/events": "Eventos",
  "/live": "Eventos ao vivo",
  "/predictions": "Meus palpites",
  "/pools": "Bolões",
  "/leagues": "Ligas",
  "/rankings": "Rankings",
  "/statistics": "Estatísticas",
  "/achievements": "Conquistas",
  "/community": "Comunidade",
  "/notifications": "Notificações",
  "/profile": "Perfil",
  "/help": "Central de ajuda",
  "/points": "Pontos virtuais",
  "/admin": "Painel administrativo",
};

function SidebarNav({ onNavigate }: { onNavigate: () => void }) {
  const { user } = useAuth();
  const { unreadCount } = useAppData();
  const groups = user?.role === "ADMIN" ? [...playerNavigation.slice(0, 1), ...adminNavigation] : playerNavigation;

  return (
    <nav className="sidebar-nav" aria-label="Navegação principal">
      {groups.map((group, groupIndex) => (
        <div className="nav-group" key={group.title || groupIndex}>
          {group.title && <span className="nav-group__title">{group.title}</span>}
          {group.items.map(({ label, to, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) => `nav-item ${isActive ? "nav-item--active" : ""}`}
              onClick={onNavigate}
            >
              <Icon size={18} strokeWidth={1.9} />
              <span>{label}</span>
              {to === "/notifications" && unreadCount > 0 && <b className="nav-badge">{Math.min(unreadCount, 99)}</b>}
            </NavLink>
          ))}
        </div>
      ))}
    </nav>
  );
}

export function AppShell() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [search, setSearch] = useState("");
  const location = useLocation();
  const navigate = useNavigate();
  const searchRef = useRef<HTMLInputElement>(null);
  const { user, logout } = useAuth();
  const { wallet, notifications, unreadCount, markNotificationRead } = useAppData();
  const { notify } = useToast();

  const title = useMemo(() => {
    const exact = pageTitles[location.pathname];
    if (exact) return exact;
    if (location.pathname.startsWith("/events/")) return "Detalhes do evento";
    if (location.pathname.startsWith("/admin/")) {
      const item = adminNavigation[0].items.find(({ to }) => location.pathname.startsWith(to));
      return item?.label || "Administração";
    }
    return brand.name;
  }, [location.pathname]);

  useEffect(() => {
    setSidebarOpen(false);
    setNotificationOpen(false);
    setAccountOpen(false);
    document.title = `${title} · ${brand.name}`;
  }, [location.pathname, title]);

  useEffect(() => {
    const shortcut = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        searchRef.current?.focus();
      }
    };
    window.addEventListener("keydown", shortcut);
    return () => window.removeEventListener("keydown", shortcut);
  }, []);

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    const value = search.trim();
    if (!value) {
      notify("Digite um time, evento ou campeonato para buscar.", "info");
      searchRef.current?.focus();
      return;
    }
    navigate(`/events?q=${encodeURIComponent(value)}`);
  }

  async function openNotification(id: number | string, link?: string) {
    try {
      await markNotificationRead(id);
    } catch {
      notify("Não foi possível marcar a notificação como lida.", "error");
    }
    navigate(link && link.startsWith("/") ? link : "/notifications");
  }

  const balance = getWalletBalance(wallet);

  return (
    <div className={`app-shell ${sidebarOpen ? "app-shell--menu-open" : ""}`}>
      <aside className="sidebar">
        <div className="sidebar__brand">
          <Brand />
          <button className="sidebar__close icon-button" type="button" onClick={() => setSidebarOpen(false)} aria-label="Fechar menu">
            <X size={19} />
          </button>
        </div>
        <SidebarNav onNavigate={() => setSidebarOpen(false)} />
        <div className="sidebar__foot">
          <NavLink className="points-card" to="/points">
            <span className="points-card__icon"><Zap size={17} /></span>
            <span><small>Saldo virtual</small><strong>{points(balance)} pts</strong></span>
            <ChevronDown size={16} className="points-card__arrow" />
          </NavLink>
          <div className="safe-note"><ShieldCheck size={15} /><span>Pontos sem valor financeiro</span></div>
        </div>
      </aside>

      <button className="sidebar-scrim" type="button" onClick={() => setSidebarOpen(false)} aria-label="Fechar menu" />

      <div className="app-main">
        {isExplicitDemoMode && (
          <div className="demo-banner" role="status">
            <Sparkles size={15} /> Modo demonstração explícito — dados e eventos podem ser simulados.
          </div>
        )}
        <header className="topbar">
          <button className="mobile-menu icon-button" type="button" onClick={() => setSidebarOpen(true)} aria-label="Abrir menu">
            <Menu size={21} />
          </button>
          <div className="topbar__title"><span>Agora</span><strong>{title}</strong></div>
          <form className="global-search" role="search" onSubmit={submitSearch}>
            <Search size={18} aria-hidden="true" />
            <input ref={searchRef} value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar eventos, campeonatos ou times" aria-label="Busca global" />
            <kbd>Ctrl K</kbd>
          </form>
          <div className="topbar__actions">
            <div className="dropdown-wrap">
              <button className="icon-button notification-button" type="button" onClick={() => setNotificationOpen((value) => !value)} aria-label={`Notificações${unreadCount ? `, ${unreadCount} não lidas` : ""}`} aria-expanded={notificationOpen}>
                <Bell size={20} />
                {unreadCount > 0 && <span>{Math.min(unreadCount, 9)}</span>}
              </button>
              {notificationOpen && (
                <div className="dropdown dropdown--notifications">
                  <div className="dropdown__head"><strong>Notificações</strong><NavLink to="/notifications">Ver todas</NavLink></div>
                  {notifications.slice(0, 4).map((item) => (
                    <button type="button" className={`notification-row ${!item.read && !item.readAt ? "notification-row--unread" : ""}`} onClick={() => openNotification(item.id, item.link)} key={item.id}>
                      <span className="notification-row__dot" />
                      <span><strong>{item.title}</strong><small>{item.message}</small><em>{relativeTime(item.createdAt)}</em></span>
                    </button>
                  ))}
                  {!notifications.length && <p className="dropdown__empty">Tudo em dia por aqui.</p>}
                </div>
              )}
            </div>

            <div className="dropdown-wrap account-wrap">
              <button className="account-button" type="button" onClick={() => setAccountOpen((value) => !value)} aria-expanded={accountOpen}>
                <Avatar name={user?.name} image={user?.avatarUrl} />
                <span><strong>{user?.name}</strong><small>{user?.role === "ADMIN" ? "Administrador" : "Participante"}</small></span>
                <ChevronDown size={16} />
              </button>
              {accountOpen && (
                <div className="dropdown dropdown--account">
                  <NavLink to="/profile"><UserCircle size={17} /> Meu perfil</NavLink>
                  <NavLink to="/points"><WalletCards size={17} /> Pontos virtuais</NavLink>
                  <button type="button" onClick={() => logout()}><LogOut size={17} /> Sair</button>
                </div>
              )}
            </div>
          </div>
        </header>

        <main className="content" id="main-content">
          <Outlet />
        </main>

        <nav className="mobile-bottom-nav" aria-label="Navegação móvel">
          {[
            { label: "Início", to: "/app", icon: LayoutDashboard },
            { label: "Eventos", to: "/events", icon: Compass },
            { label: "Ao vivo", to: "/live", icon: Activity },
            { label: "Palpites", to: "/predictions", icon: Target },
            { label: "Perfil", to: "/profile", icon: UserCircle },
          ].map(({ label, to, icon: Icon }) => (
            <NavLink to={to} key={to} className={({ isActive }) => (isActive ? "active" : "")}>
              <Icon size={20} /><span>{label}</span>
            </NavLink>
          ))}
        </nav>
      </div>
    </div>
  );
}

export function AccessDenied() {
  const navigate = useNavigate();
  return (
    <div className="standalone-state">
      <Brand />
      <span className="standalone-state__icon"><ShieldCheck size={32} /></span>
      <h1>Acesso restrito</h1>
      <p>Esta área é exclusiva para administradores. Seu login continua ativo na área de participante.</p>
      <Button onClick={() => navigate("/app")}>Voltar para visão geral</Button>
    </div>
  );
}
