import { useEffect, useMemo, useState, type CSSProperties, type HTMLAttributes } from "react";

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
  return DEMO_USER_AVATAR_MAP[normalizeUserName(name)] || null;
}

export function resolveAvatarFallback(name?: string | null) {
  return DEMO_USER_AVATAR_MAP[normalizeUserName(name)] || null;
}

export function userInitials(name?: string | null) {
  const parts = (name || "")
    .trim()
    .split(/\s+/)
    .filter(Boolean);

  if (parts.length === 0) return "?";
  const initials = parts.length === 1
    ? parts[0].slice(0, 2)
    : `${parts[0][0]}${parts.at(-1)?.[0] || ""}`;
  return initials.toLocaleUpperCase("pt-BR");
}

export function userAvatarGradient(name?: string | null) {
  const normalizedName = normalizeUserName(name) || "participante";
  const hash = [...normalizedName].reduce((value, character) => (
    ((value << 5) - value + character.charCodeAt(0)) | 0
  ), 0);
  const hue = Math.abs(hash) % 360;
  return `linear-gradient(135deg, hsl(${hue} 66% 46%), hsl(${(hue + 42) % 360} 72% 34%))`;
}

/**
 * Stable avatar surface shared by account, ranking, community and admin views.
 * Known demo identities keep their curated portrait. Other identities receive
 * deterministic initials and colors, including when a stale image URL fails.
 */
export function UserAvatar({
  name,
  avatarUrl,
  src,
  size = "md",
  loading = "lazy",
  className = "",
  style,
  ...props
}: UserAvatarProps) {
  const preferredSource = useMemo(() => resolveAvatarSource(name, avatarUrl, src), [name, avatarUrl, src]);
  const visualFallback = useMemo(() => resolveAvatarFallback(name), [name]);
  const initials = useMemo(() => userInitials(name), [name]);
  const background = useMemo(() => userAvatarGradient(name), [name]);
  const [source, setSource] = useState<string | null>(preferredSource);
  const accessibleLabel = props["aria-label"];

  useEffect(() => {
    setSource(preferredSource);
  }, [preferredSource]);

  return (
    <span
      {...props}
      className={`user-avatar user-avatar--${size} ${className}`.trim()}
      style={{ background, ...style } as CSSProperties}
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
            else setSource(null);
          }}
        />
      ) : (
        <span className="user-avatar__initials" aria-hidden="true">{initials}</span>
      )}
    </span>
  );
}
