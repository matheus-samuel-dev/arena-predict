import {
  ArrowRight,
  Inbox,
  LoaderCircle,
  RefreshCcw,
  SearchX,
  ShieldAlert,
  Sparkles,
  X,
  type LucideIcon,
} from "lucide-react";
import { useEffect, type ButtonHTMLAttributes, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { brand } from "../app/branding";

export function Button({
  variant = "primary",
  size = "md",
  loading = false,
  children,
  className = "",
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost" | "danger" | "quiet";
  size?: "sm" | "md" | "lg";
  loading?: boolean;
}) {
  return (
    <button className={`button button--${variant} button--${size} ${className}`} disabled={disabled || loading} {...props}>
      {loading && <LoaderCircle className="spin" size={17} aria-hidden="true" />}
      {children}
    </button>
  );
}

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="page-header">
      <div>
        {eyebrow && <span className="eyebrow">{eyebrow}</span>}
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {actions && <div className="page-header__actions">{actions}</div>}
    </header>
  );
}

export function SectionHeader({
  title,
  description,
  link,
  linkLabel = "Ver todos",
  action,
}: {
  title: string;
  description?: string;
  link?: string;
  linkLabel?: string;
  action?: ReactNode;
}) {
  return (
    <div className="section-header">
      <div>
        <h2>{title}</h2>
        {description && <p>{description}</p>}
      </div>
      {action ||
        (link && (
          <Link className="text-link" to={link}>
            {linkLabel} <ArrowRight size={15} />
          </Link>
        ))}
    </div>
  );
}

export function Skeleton({ className = "" }: { className?: string }) {
  return <span className={`skeleton ${className}`} aria-hidden="true" />;
}

export function PageSkeleton({ cards = 4 }: { cards?: number }) {
  return (
    <div className="page-skeleton" aria-label="Carregando conteúdo" role="status">
      <Skeleton className="skeleton--title" />
      <Skeleton className="skeleton--subtitle" />
      <div className="metric-grid">
        {Array.from({ length: cards }, (_, index) => (
          <div className="surface metric-card" key={index}>
            <Skeleton className="skeleton--icon" />
            <Skeleton className="skeleton--line" />
            <Skeleton className="skeleton--value" />
          </div>
        ))}
      </div>
      <Skeleton className="skeleton--panel" />
    </div>
  );
}

export function EmptyState({
  title,
  description,
  icon: Icon = Inbox,
  action,
}: {
  title: string;
  description: string;
  icon?: LucideIcon;
  action?: ReactNode;
}) {
  return (
    <div className="empty-state">
      <span className="empty-state__icon"><Icon size={25} /></span>
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="empty-state empty-state--error" role="alert">
      <span className="empty-state__icon"><ShieldAlert size={25} /></span>
      <h3>Não foi possível carregar</h3>
      <p>{message}</p>
      {onRetry && (
        <Button variant="secondary" onClick={onRetry}>
          <RefreshCcw size={16} /> Tentar novamente
        </Button>
      )}
    </div>
  );
}

export function NoResults({ onClear }: { onClear?: () => void }) {
  return (
    <EmptyState
      icon={SearchX}
      title="Nenhum resultado encontrado"
      description="Ajuste os filtros ou limpe a busca para ver outros conteúdos."
      action={onClear ? <Button variant="secondary" onClick={onClear}>Limpar filtros</Button> : undefined}
    />
  );
}

export function Modal({
  open,
  onClose,
  title,
  children,
  size = "md",
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  size?: "sm" | "md" | "lg";
}) {
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (event: KeyboardEvent) => event.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    document.body.classList.add("modal-open");
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.classList.remove("modal-open");
    };
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div className="modal-layer" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className={`modal modal--${size}`} role="dialog" aria-modal="true" aria-labelledby="modal-title">
        <header className="modal__header">
          <div>
            <span className="eyebrow"><Sparkles size={13} /> {brand.name}</span>
            <h2 id="modal-title">{title}</h2>
          </div>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Fechar">
            <X size={20} />
          </button>
        </header>
        <div className="modal__body">{children}</div>
      </section>
    </div>
  );
}

export function Avatar({ name, image, size = "md" }: { name?: string; image?: string | null; size?: "sm" | "md" | "lg" }) {
  const initials = (name || "Arena Player")
    .split(" ")
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
  return image ? (
    <img className={`avatar avatar--${size}`} src={image} alt="" />
  ) : (
    <span className={`avatar avatar--${size}`} aria-hidden="true">{initials}</span>
  );
}

export function StatusBadge({ status, label }: { status?: string; label?: string }) {
  const normalized = String(status || "neutral").toLowerCase().replace(/_/g, "-");
  return <span className={`status status--${normalized}`}><i />{label || status}</span>;
}

export function Progress({ value, max = 100, label }: { value: number; max?: number; label?: string }) {
  const percentage = Math.min(100, Math.max(0, max ? (value / max) * 100 : 0));
  return (
    <div className="progress-wrap">
      {label && <div className="progress-label"><span>{label}</span><b>{Math.round(percentage)}%</b></div>}
      <div className="progress-track" role="progressbar" aria-valuenow={value} aria-valuemin={0} aria-valuemax={max}>
        <span style={{ width: `${percentage}%` }} />
      </div>
    </div>
  );
}
