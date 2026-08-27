import {
  Activity,
  Award,
  BarChart3,
  Bell,
  BookOpen,
  CalendarRange,
  ChevronDown,
  ChevronRight,
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
import { ThemeSelector } from "./ThemeSelector";
import { Button, UserAvatar } from "./UI";

interface NavItem {
  label: string;
  to: string;
  icon: LucideIcon;
  end?: boolean;
}

interface NavGroup {
  id: string;
  title: string;
  items: NavItem[];
  defaultOpen?: boolean;
}

const playerNavigation: NavGroup[] = [
  {
    id: "experience",
    title: "Principal",
    defaultOpen: true,
    items: [
      { label: "Visão geral", to: "/app", icon: LayoutDashboard, end: true },
      { label: "Eventos", to: "/events", icon: Compass },
      { label: "Ao vivo", to: "/live", icon: Activity },
      { label: "Meus palpites", to: "/predictions", icon: Target },
    ],
  },
  {
    id: "compete",
    title: "Competições",
    defaultOpen: true,
    items: [
      { label: "Bolões", to: "/pools", icon: Trophy },
      { label: "Ligas", to: "/leagues", icon: Swords },
      { label: "Rankings", to: "/rankings", icon: BarChart3 },
      { label: "Estatísticas", to: "/statistics", icon: Gauge },
      { label: "Desafios", to: "/challenges", icon: Target },
      { label: "Conquistas", to: "/achievements", icon: Award },
    ],
  },
  {
    id: "connect",
    title: "Comunidade e conta",
    defaultOpen: false,
    items: [
      { label: "Comunidade", to: "/community", icon: MessageSquareText },
      { label: "Notificações", to: "/notifications", icon: Bell },
      { label: "Perfil", to: "/profile", icon: UserCircle },
      { label: "Ajuda", to: "/help", icon: CircleHelp },
    ],
  },
];

const adminNavigation: NavGroup[] = [
  {
    id: "operation",
    title: "Operação",
    defaultOpen: true,
    items: [
      { label: "Painel", to: "/admin", icon: ShieldCheck, end: true },
      { label: "Eventos", to: "/admin/events", icon: CalendarRange },
      { label: "Mercados", to: "/admin/markets", icon: SlidersHorizontal },
      { label: "Resultados", to: "/admin/results", icon: ClipboardCheck },
    ],
  },
  {
    id: "catalog",
    title: "Catálogo",
    items: [
      { label: "Modalidades", to: "/admin/sports", icon: Gamepad2 },
      { label: "Campeonatos", to: "/admin/championships", icon: Trophy },
      { label: "Equipes e participantes", to: "/admin/competitors", icon: Users },
    ],
  },
  {
    id: "engagement",
    title: "Engajamento",
    items: [
      { label: "Bolões", to: "/admin/pools", icon: Layers3 },
      { label: "Pontuação", to: "/admin/scoring-rules", icon: Sparkles },
      { label: "Desafios", to: "/admin/challenges", icon: Target },
      { label: "Conquistas", to: "/admin/achievements", icon: Award },
    ],
  },
  {
    id: "management",
    title: "Gestão",
    items: [
      { label: "Usuários", to: "/admin/users", icon: Users },
      { label: "Notificações", to: "/admin/notifications", icon: Bell },
      { label: "Moderação", to: "/admin/moderation", icon: HeartHandshake },
    ],
  },
  {
    id: "governance",
    title: "Governança",
    items: [
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
  "/challenges": "Desafios",
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
  const location = useLocation();
  const groups = useMemo(
    () => (user?.role === "ADMIN" ? [...playerNavigation.slice(0, 1), ...adminNavigation] : playerNavigation),
    [user?.role],
  );
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});

  useEffect(() => {
    const activeGroup = groups.find((group) => group.items.some((item) => item.end
      ? location.pathname === item.to
      : location.pathname.startsWith(item.to)));
    if (activeGroup) setExpanded((current) => ({ ...current, [activeGroup.id]: true }));
  }, [groups, location.pathname]);

  return (
    <nav className="sidebar-nav" aria-label="Navegação principal">
      {groups.map((group) => {
        const active = group.items.some((item) => item.end ? location.pathname === item.to : location.pathname.startsWith(item.to));
        const isExpanded = expanded[group.id] ?? group.defaultOpen ?? active;
        const panelId = `nav-group-${group.id}`;
        return (
          <section className={`nav-group ${active ? "nav-group--active" : ""}`} key={group.id}>
            <button
              className="nav-group__toggle"
              type="button"
              aria-expanded={isExpanded}
              aria-controls={panelId}
              onClick={() => setExpanded((current) => ({ ...current, [group.id]: !isExpanded }))}
            >
              <span>{group.title}</span>
              <ChevronDown size={15} aria-hidden="true" />
            </button>
            <div className="nav-group__items" id={panelId} hidden={!isExpanded}>
              {group.items.map(({ label, to, icon: Icon, end }) => (
                <NavLink
                  key={to}
                  to={to}
                  end={end}
                  className={({ isActive }) => `nav-item ${isActive ? "nav-item--active" : ""}`}
                  onClick={onNavigate}
                >
                  <Icon size={19} strokeWidth={1.9} aria-hidden="true" />
                  <span>{label}</span>
                  {to === "/notifications" && unreadCount > 0 && <b className="nav-badge">{unreadCount > 99 ? "99+" : unreadCount}</b>}
                </NavLink>
              ))}
            </div>
          </section>
        );
      })}
    </nav>
  );
}

export function AppShell() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [drawerMode, setDrawerMode] = useState(() => typeof window !== "undefined" && window.matchMedia("(max-width: 860px)").matches);
  const location = useLocation();
  const navigate = useNavigate();
  const searchRef = useRef<HTMLInputElement>(null);
  const sidebarRef = useRef<HTMLElement>(null);
  const closeSidebarRef = useRef<HTMLButtonElement>(null);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const notificationRef = useRef<HTMLDivElement>(null);
  const notificationButtonRef = useRef<HTMLButtonElement>(null);
  const accountRef = useRef<HTMLDivElement>(null);
  const accountButtonRef = useRef<HTMLButtonElement>(null);
  const { user, logout } = useAuth();
  const { wallet, walletError, notifications, notificationsError, unreadCount, markNotificationRead } = useAppData();
  const { notify } = useToast();

  const title = useMemo(() => {
    const exact = pageTitles[location.pathname];
    if (exact) return exact;
    if (location.pathname.startsWith("/events/")) return "Detalhes do evento";
    if (location.pathname.startsWith("/admin/")) {
      const item = adminNavigation
        .flatMap((group) => group.items)
        .find(({ to, end }) => end ? location.pathname === to : location.pathname.startsWith(to));
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
    const media = window.matchMedia("(max-width: 860px)");
    const updateMode = () => setDrawerMode(media.matches);
    updateMode();
    media.addEventListener("change", updateMode);
    return () => media.removeEventListener("change", updateMode);
  }, []);

  useEffect(() => {
    const sidebar = sidebarRef.current;
    if (!sidebar) return undefined;

    if (!drawerMode) {
      sidebar.removeAttribute("inert");
      sidebar.removeAttribute("aria-hidden");
      document.body.classList.remove("navigation-open");
      return undefined;
    }

    if (!sidebarOpen) {
      sidebar.setAttribute("inert", "");
      sidebar.setAttribute("aria-hidden", "true");
      document.body.classList.remove("navigation-open");
      return undefined;
    }

    sidebar.removeAttribute("inert");
    sidebar.removeAttribute("aria-hidden");
    document.body.classList.add("navigation-open");
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const focusableSelector = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
    window.requestAnimationFrame(() => closeSidebarRef.current?.focus());

    const keepFocusInside = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setSidebarOpen(false);
        return;
      }
      if (event.key !== "Tab") return;
      const elements = Array.from(sidebar.querySelectorAll<HTMLElement>(focusableSelector)).filter((element) => element.getClientRects().length > 0);
      if (!elements.length) return;
      const first = elements[0];
      const last = elements[elements.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener("keydown", keepFocusInside);
    return () => {
      document.removeEventListener("keydown", keepFocusInside);
      document.body.classList.remove("navigation-open");
      (menuButtonRef.current || previouslyFocused)?.focus();
    };
  }, [drawerMode, sidebarOpen]);

  useEffect(() => {
    if (!notificationOpen && !accountOpen) return undefined;
    const closeDropdowns = (event: PointerEvent) => {
      const target = event.target as Node;
      if (notificationOpen && !notificationRef.current?.contains(target)) setNotificationOpen(false);
      if (accountOpen && !accountRef.current?.contains(target)) setAccountOpen(false);
    };
    const closeWithKeyboard = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      const restoreAccountFocus = accountOpen;
      const restoreNotificationFocus = notificationOpen;
      setNotificationOpen(false);
      setAccountOpen(false);
      if (restoreAccountFocus) window.requestAnimationFrame(() => accountButtonRef.current?.focus());
      else if (restoreNotificationFocus) window.requestAnimationFrame(() => notificationButtonRef.current?.focus());
    };
    document.addEventListener("pointerdown", closeDropdowns);
    document.addEventListener("keydown", closeWithKeyboard);
    return () => {
      document.removeEventListener("pointerdown", closeDropdowns);
      document.removeEventListener("keydown", closeWithKeyboard);
    };
  }, [accountOpen, notificationOpen]);

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
    <>
      <a className="skip-link" href="#main-content">Pular para o conteúdo principal</a>
      <div className={`app-shell ${sidebarOpen ? "app-shell--menu-open" : ""}`}>
      <aside className="sidebar" id="primary-sidebar" ref={sidebarRef} aria-label="Menu principal">
        <div className="sidebar__brand">
          <Brand />
          <button ref={closeSidebarRef} className="sidebar__close icon-button" type="button" onClick={() => setSidebarOpen(false)} aria-label="Fechar menu">
            <X size={19} aria-hidden="true" />
          </button>
        </div>
        <SidebarNav onNavigate={() => setSidebarOpen(false)} />
        <div className="sidebar__foot">
          <NavLink className="points-card" to="/points">
            <span className="points-card__icon"><Zap size={17} /></span>
            <span><small>Saldo virtual</small><strong>{walletError ? "Indisponível" : `${points(balance)} pts`}</strong></span>
            <ChevronRight size={16} className="points-card__arrow" aria-hidden="true" />
          </NavLink>
          <div className="safe-note"><ShieldCheck size={15} /><span>Pontos sem valor financeiro</span></div>
          <div className="sidebar-mobile-account">
            <div><UserAvatar name={user?.name} avatarUrl={user?.avatarUrl} /><span><strong>{user?.name}</strong><small>{user?.role === "ADMIN" ? "Administrador" : "Participante"}</small></span></div>
            <NavLink to="/profile" onClick={() => setSidebarOpen(false)}><UserCircle size={18} aria-hidden="true" /> Meu perfil</NavLink>
            <button type="button" onClick={() => void logout()}><LogOut size={18} aria-hidden="true" /> Sair da conta</button>
          </div>
        </div>
      </aside>

      {sidebarOpen && <button className="sidebar-scrim" type="button" onClick={() => setSidebarOpen(false)} aria-label="Fechar menu" />}

      <div className="app-main">
        {isExplicitDemoMode && (
          <div className="demo-banner" role="note" title="Este ambiente usa dados de demonstração e pode simular atualizações de eventos." aria-label="Ambiente demonstrativo. Este ambiente usa dados de demonstração e pode simular atualizações de eventos.">
            <Sparkles size={15} aria-hidden="true" /> <span>Ambiente demonstrativo <b aria-hidden="true">•</b> dados e eventos simulados</span>
          </div>
        )}
        <header className="topbar">
          <button ref={menuButtonRef} className="mobile-menu icon-button" type="button" onClick={() => setSidebarOpen(true)} aria-label="Abrir menu" aria-expanded={sidebarOpen} aria-controls="primary-sidebar">
            <Menu size={21} aria-hidden="true" />
          </button>
          <div className="topbar__title"><span>Agora</span><strong>{title}</strong></div>
          <form className="global-search" role="search" onSubmit={submitSearch}>
            <button className="global-search__submit" type="submit" aria-label="Executar busca">
              <Search size={18} aria-hidden="true" />
            </button>
            <input ref={searchRef} value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar eventos, campeonatos ou times" aria-label="Busca global" />
            <kbd>Ctrl K</kbd>
          </form>
          <div className="topbar__actions">
            <div className="dropdown-wrap" ref={notificationRef}>
              <button ref={notificationButtonRef} className="icon-button notification-button" type="button" onClick={() => { setNotificationOpen((value) => !value); setAccountOpen(false); }} aria-label={`Notificações${unreadCount ? `, ${unreadCount} não lidas` : ""}`} aria-haspopup="true" aria-expanded={notificationOpen} aria-controls="notifications-dropdown">
                <Bell size={20} aria-hidden="true" />
                {unreadCount > 0 && <span>{unreadCount > 9 ? "9+" : unreadCount}</span>}
              </button>
              {notificationOpen && (
                <div className="dropdown dropdown--notifications" id="notifications-dropdown" role="region" aria-label="Notificações recentes">
                  <div className="dropdown__head"><strong>Notificações</strong><NavLink to="/notifications">Ver todas</NavLink></div>
                  {notifications.slice(0, 4).map((item) => (
                    <button type="button" className={`notification-row ${!item.read && !item.readAt ? "notification-row--unread" : ""}`} onClick={() => openNotification(item.id, item.link)} key={item.id}>
                      <span className="notification-row__dot" />
                      <span><strong>{item.title}</strong><small>{item.message}</small><em>{relativeTime(item.createdAt)}</em></span>
                    </button>
                  ))}
                  {!notifications.length && !notificationsError && <p className="dropdown__empty">Tudo em dia por aqui.</p>}
                  {notificationsError && <p className="dropdown__empty" role="alert">Não foi possível atualizar as notificações.</p>}
                </div>
              )}
            </div>

            <div className="dropdown-wrap account-wrap" ref={accountRef}>
              <button ref={accountButtonRef} className="account-button" type="button" onClick={() => { setAccountOpen((value) => !value); setNotificationOpen(false); }} aria-haspopup="true" aria-expanded={accountOpen} aria-controls="account-dropdown" aria-label="Abrir opções da conta">
                <UserAvatar name={user?.name} avatarUrl={user?.avatarUrl} loading="eager" />
                <span><strong>{user?.name}</strong><small>{user?.role === "ADMIN" ? "Administrador" : "Participante"}</small></span>
                <ChevronDown size={16} />
              </button>
              {accountOpen && (
                <div className="dropdown dropdown--account" id="account-dropdown" role="group" aria-label="Opções da conta">
                  <NavLink to="/profile"><UserCircle size={17} aria-hidden="true" /> Meu perfil</NavLink>
                  <NavLink to="/profile?tab=preferences"><Cog size={17} aria-hidden="true" /> Preferências</NavLink>
                  <ThemeSelector compact />
                  <NavLink to="/points"><WalletCards size={17} aria-hidden="true" /> Pontos virtuais</NavLink>
                  <button type="button" onClick={() => void logout()}><LogOut size={17} aria-hidden="true" /> Sair</button>
                </div>
              )}
            </div>
          </div>
        </header>

        <main className="content" id="main-content">
          <Outlet />
        </main>

        <nav className="mobile-bottom-nav" aria-label="Navegação móvel">
          {(user?.role === "ADMIN" ? [
            { label: "Painel", to: "/admin", icon: ShieldCheck },
            { label: "Eventos", to: "/admin/events", icon: CalendarRange },
            { label: "Mercados", to: "/admin/markets", icon: SlidersHorizontal },
            { label: "Resultados", to: "/admin/results", icon: ClipboardCheck },
            { label: "Perfil", to: "/profile", icon: UserCircle },
          ] : [
            { label: "Início", to: "/app", icon: LayoutDashboard },
            { label: "Eventos", to: "/events", icon: Compass },
            { label: "Ao vivo", to: "/live", icon: Activity },
            { label: "Palpites", to: "/predictions", icon: Target },
            { label: "Perfil", to: "/profile", icon: UserCircle },
          ]).map(({ label, to, icon: Icon }) => (
            <NavLink to={to} key={to} className={({ isActive }) => (isActive ? "active" : "")}>
              <Icon size={20} /><span>{label}</span>
            </NavLink>
          ))}
        </nav>
      </div>
    </div>
    </>
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
