import {
  Archive,
  ArrowRight,
  CheckCircle2,
  Circle,
  Clock3,
  Inbox,
  LoaderCircle,
  PauseCircle,
  Radio,
  RefreshCcw,
  RotateCcw,
  SearchX,
  ShieldAlert,
  Sparkles,
  X,
  XCircle,
  type LucideIcon,
} from "lucide-react";
import { useEffect, useId, useRef, type ButtonHTMLAttributes, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { brand } from "../app/branding";
import { statusPresentation, type PresentationIcon } from "../app/presentation";

export { UserAvatar } from "./UserAvatar";

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
    <button
      className={`button button--${variant} button--${size} ${className}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
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
  const dialogRef = useRef<HTMLElement>(null);
  const restoreFocusRef = useRef<HTMLElement | null>(null);
  const onCloseRef = useRef(onClose);
  const titleId = useId();

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!open) return undefined;
    restoreFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;

    const focusableElements = () => Array.from(dialogRef.current?.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ) || []).filter((element) => element.getClientRects().length > 0 && element.getAttribute("aria-hidden") !== "true");

    const focusFrame = window.requestAnimationFrame(() => {
      const preferred = dialogRef.current?.querySelector<HTMLElement>(
        '[autofocus], .modal__body input:not([disabled]), .modal__body select:not([disabled]), .modal__body textarea:not([disabled])',
      );
      (preferred || focusableElements()[0] || dialogRef.current)?.focus();
    });

    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onCloseRef.current();
        return;
      }
      if (event.key !== "Tab") return;
      const focusable = focusableElements();
      if (!focusable.length) {
        event.preventDefault();
        dialogRef.current?.focus();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", onKey);
    document.body.classList.add("modal-open");
    return () => {
      window.cancelAnimationFrame(focusFrame);
      document.removeEventListener("keydown", onKey);
      document.body.classList.remove("modal-open");
      if (restoreFocusRef.current?.isConnected) restoreFocusRef.current.focus();
    };
  }, [open]);

  if (!open) return null;
  return (
    <div className="modal-layer" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onCloseRef.current()}>
      <section ref={dialogRef} className={`modal modal--${size}`} role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}>
        <header className="modal__header">
          <div>
            <span className="eyebrow"><Sparkles size={13} /> {brand.name}</span>
            <h2 id={titleId}>{title}</h2>
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

const statusIcons: Record<PresentationIcon, LucideIcon> = {
  neutral: Circle,
  clock: Clock3,
  live: Radio,
  check: CheckCircle2,
  close: XCircle,
  pause: PauseCircle,
  refund: RotateCcw,
  archive: Archive,
  shield: ShieldAlert,
};

export function StatusBadge({ status, label, tooltip }: { status?: string; label?: string; tooltip?: string }) {
  const presentation = statusPresentation(status);
  const explicitLabel = label?.trim();
  const displayLabel = explicitLabel && !/^[A-Z\d_ -]+$/.test(explicitLabel)
    ? explicitLabel
    : statusPresentation(explicitLabel || status).label;
  const help = tooltip || presentation.tooltip;
  const Icon = statusIcons[presentation.icon];
  return (
    <span className={`status status--${presentation.tone}`} title={help} aria-label={help ? `${displayLabel}. ${help}` : undefined}>
      <Icon size={14} strokeWidth={2.1} aria-hidden="true" />
      <span>{displayLabel}</span>
    </span>
  );
}

export function Progress({ value, max = 100, label }: { value: number; max?: number; label?: string }) {
  const safeMax = Number.isFinite(max) && max > 0 ? max : 100;
  const safeValue = Math.min(safeMax, Math.max(0, Number.isFinite(value) ? value : 0));
  const percentage = (safeValue / safeMax) * 100;
  return (
    <div className="progress-wrap">
      {label && <div className="progress-label"><span>{label}</span><b>{Math.round(percentage)}%</b></div>}
      <div
        className="progress-track"
        role="progressbar"
        aria-label={label || "Progresso"}
        aria-valuenow={safeValue}
        aria-valuemin={0}
        aria-valuemax={safeMax}
        aria-valuetext={`${Math.round(percentage)}%`}
      >
        <span style={{ width: `${percentage}%` }} />
      </div>
    </div>
  );
}
