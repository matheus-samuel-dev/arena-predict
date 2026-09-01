import { useEffect, useMemo, useState, type HTMLAttributes } from "react";

export type UserAvatarSize = "xs" | "sm" | "md" | "lg" | "xl";

export interface UserAvatarProps extends Omit<HTMLAttributes<HTMLSpanElement>, "children"> {
  name?: string | null;
  avatarUrl?: string | null;
  src?: string | null;
  size?: UserAvatarSize;
  /** Eager is useful for above-the-fold identity; lists stay lazy by default. */
  loading?: "eager" | "lazy";
}

export const DEFAULT_USER_AVATAR_PATH = "/assets/avatars/avatar-default.webp";
export const DEMO_PLAYER_AVATAR_PATH = "/assets/avatars/jogador-demo.webp";

export interface UserAvatarOption {
  id: string;
  label: string;
  src: string;
}

/**
 * Curated local portraits available in the account picker. Keeping this list
 * beside the avatar resolver gives account, header, ranking and community a
 * single source of truth without accepting arbitrary remote images.
 */
export const USER_AVATAR_OPTIONS: readonly UserAvatarOption[] = [
  { id: "arena-01", label: "Avatar Arena 1", src: DEMO_PLAYER_AVATAR_PATH },
  { id: "arena-02", label: "Avatar Arena 2", src: "/assets/avatars/ana-ribeiro.webp" },
  { id: "arena-03", label: "Avatar Arena 3", src: "/assets/avatars/beatriz-nunes.webp" },
  { id: "arena-04", label: "Avatar Arena 4", src: "/assets/avatars/camila-rocha.webp" },
  { id: "arena-05", label: "Avatar Arena 5", src: "/assets/avatars/diego-ferreira.webp" },
  { id: "arena-06", label: "Avatar Arena 6", src: "/assets/avatars/lucas-almeida.webp" },
  { id: "arena-07", label: "Avatar Arena 7", src: "/assets/avatars/marina-costa.webp" },
  { id: "arena-08", label: "Avatar Arena 8", src: "/assets/avatars/rafael-lima.webp" },
];

/**
 * Demo identities are centralized here so ranking and administration responses
 * that only contain a display name still receive the same local visual avatar.
 * API-provided avatar URLs always take priority.
 */
export const DEMO_USER_AVATAR_MAP: Record<string, string> = {
  "administrador demo": "/assets/avatars/admin-demo.webp",
  "jogador demo": DEMO_PLAYER_AVATAR_PATH,
  "beatriz nunes": "/assets/avatars/beatriz-nunes.webp",
  "marina costa": "/assets/avatars/marina-costa.webp",
  "rafael lima": "/assets/avatars/rafael-lima.webp",
  "camila rocha": "/assets/avatars/camila-rocha.webp",
  "lucas almeida": "/assets/avatars/lucas-almeida.webp",
  "ana ribeiro": "/assets/avatars/ana-ribeiro.webp",
  "diego ferreira": "/assets/avatars/diego-ferreira.webp",
};

export function normalizeUserName(value?: string | null) {
  return (value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLocaleLowerCase("pt-BR")
    .replace(/\s+/g, " ")
    .trim();
}

export function resolveAvatarSource(name?: string | null, avatarUrl?: string | null, src?: string | null) {
  const explicitSource = avatarUrl?.trim() || src?.trim();
  if (explicitSource) return explicitSource;
  return DEMO_USER_AVATAR_MAP[normalizeUserName(name)] || DEFAULT_USER_AVATAR_PATH;
}

export function resolveAvatarFallback(name?: string | null) {
  return DEMO_USER_AVATAR_MAP[normalizeUserName(name)] || DEFAULT_USER_AVATAR_PATH;
}

/**
 * Stable avatar surface shared by account, ranking, community and admin views.
 * Failed remote images fall back to a local illustrated portrait, never initials,
 * so every identity keeps a polished and context-rich visual treatment.
 */
export function UserAvatar({
  name,
  avatarUrl,
  src,
  size = "md",
  loading = "lazy",
  className = "",
  ...props
}: UserAvatarProps) {
  const preferredSource = useMemo(() => resolveAvatarSource(name, avatarUrl, src), [name, avatarUrl, src]);
  const visualFallback = useMemo(() => resolveAvatarFallback(name), [name]);
  const [source, setSource] = useState<string | null>(preferredSource);
  const accessibleLabel = props["aria-label"];

  useEffect(() => {
    setSource(preferredSource);
  }, [preferredSource]);

  return (
    <span
      {...props}
      className={`user-avatar user-avatar--${size} ${className}`.trim()}
      role={accessibleLabel ? props.role || "img" : props.role}
      aria-hidden={accessibleLabel ? undefined : true}
    >
      {source ? (
        <img
          className="user-avatar__image"
          src={source}
          alt=""
          aria-hidden="true"
          loading={loading}
          decoding="async"
          onError={() => {
            if (source !== visualFallback) setSource(visualFallback);
            else if (source !== DEFAULT_USER_AVATAR_PATH) setSource(DEFAULT_USER_AVATAR_PATH);
            else setSource(null);
          }}
        />
      ) : (
        <svg className="user-avatar__placeholder" viewBox="0 0 48 48" aria-hidden="true">
          <circle cx="24" cy="18" r="8" />
          <path d="M9 42c1.4-9 6.6-13.5 15-13.5S37.6 33 39 42" />
        </svg>
      )}
    </span>
  );
}
