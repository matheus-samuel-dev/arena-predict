import { useMemo, useState, type HTMLAttributes } from "react";

export type UserAvatarSize = "xs" | "sm" | "md" | "lg" | "xl";

export interface UserAvatarProps extends Omit<HTMLAttributes<HTMLSpanElement>, "children"> {
  name?: string | null;
  avatarUrl?: string | null;
  src?: string | null;
  size?: UserAvatarSize;
  /** Eager is useful for above-the-fold identity; lists stay lazy by default. */
  loading?: "eager" | "lazy";
}

export const DEMO_PLAYER_AVATAR_PATH = "/assets/brand/demo-player-avatar.jpg";

const nameParticles = new Set(["da", "das", "de", "do", "dos", "e"]);

function characters(value: string) {
  return Array.from(value.normalize("NFC"));
}

/**
 * Produces a compact, predictable identity for every name shape used by the UI.
 * One-word names keep two characters; compound names use the first and last
 * meaningful words so long Portuguese names remain recognizable.
 */
export function getAvatarInitials(name?: string | null) {
  const words = (name || "")
    .trim()
    .split(/\s+/u)
    .filter(Boolean);

  if (!words.length) return "AP";
  if (words.length === 1) return characters(words[0]).slice(0, 2).join("").toLocaleUpperCase("pt-BR");

  const meaningfulWords = words.filter((word, index) => (
    index === 0 || index === words.length - 1 || !nameParticles.has(word.toLocaleLowerCase("pt-BR"))
  ));
  const first = meaningfulWords[0] || words[0];
  const last = meaningfulWords.at(-1) || words.at(-1) || first;
  return `${characters(first)[0] || ""}${characters(last)[0] || ""}`.toLocaleUpperCase("pt-BR");
}

function resolveAvatarSource(name?: string | null, avatarUrl?: string | null, src?: string | null) {
  const explicitSource = avatarUrl?.trim() || src?.trim();
  if (explicitSource) return explicitSource;
  return name?.trim().toLocaleLowerCase("pt-BR") === "jogador demo"
    ? DEMO_PLAYER_AVATAR_PATH
    : undefined;
}

/**
 * Stable avatar surface shared by account, ranking, community and admin views.
 * The wrapper never changes size when an image fails, preventing layout shift.
 * It is decorative by default because every current usage renders the user's
 * visible name next to it; callers may provide aria-label for standalone use.
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
  const source = resolveAvatarSource(name, avatarUrl, src);
  const [failedSource, setFailedSource] = useState<string>();
  const initials = useMemo(() => getAvatarInitials(name), [name]);
  const showImage = Boolean(source && source !== failedSource);
  const accessibleLabel = props["aria-label"];

  return (
    <span
      {...props}
      className={`user-avatar user-avatar--${size} ${className}`.trim()}
      role={accessibleLabel ? props.role || "img" : props.role}
      aria-hidden={accessibleLabel ? undefined : true}
    >
      {showImage ? (
        <img
          className="user-avatar__image"
          src={source}
          alt=""
          aria-hidden="true"
          loading={loading}
          decoding="async"
          onError={() => setFailedSource(source)}
        />
      ) : (
        <span className="user-avatar__initials">{initials}</span>
      )}
    </span>
  );
}
