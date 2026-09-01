import { useEffect, useMemo, useState } from "react";

type LogoSize = "sm" | "md" | "lg";

export interface TeamLogoProps {
  name?: string | null;
  code?: string | null;
  logoUrl?: string | null;
  size?: LogoSize;
  className?: string;
  decorative?: boolean;
}

/**
 * Curated sources for the demo teams. Explicit API-provided URLs have priority;
 * these aliases make the demo useful even while a catalog record has no image.
 * Every image has a graphic fallback so a provider outage never creates a
 * broken-image state or an inconsistent text-only identity.
 */
export const TEAM_PLACEHOLDER_PATH = "/assets/teams/team-placeholder.svg";
export const TEAM_LOGO_MAP: Record<string, string> = {
  palmeiras: "https://upload.wikimedia.org/wikipedia/commons/1/10/Palmeiras_logo.svg",
  flamengo: "https://upload.wikimedia.org/wikipedia/commons/9/96/Clube_de_Regatas_do_Flamengo_logo.svg",
  furia: "https://upload.wikimedia.org/wikipedia/en/a/ad/FURIA_Esports_logo.svg",
  "furia esports": "https://upload.wikimedia.org/wikipedia/en/a/ad/FURIA_Esports_logo.svg",
  navi: "https://commons.wikimedia.org/wiki/Special:FilePath/Natus_Vincere_logo.png",
  "natus vincere": "https://commons.wikimedia.org/wiki/Special:FilePath/Natus_Vincere_logo.png",
  loud: "https://commons.wikimedia.org/wiki/Special:FilePath/LOUD_logo.svg",
  t1: "https://commons.wikimedia.org/wiki/Special:FilePath/T1_esports_logo.svg",
  "gen.g": "https://commons.wikimedia.org/wiki/Special:FilePath/Gen.G_Logo.svg",
  geng: "https://commons.wikimedia.org/wiki/Special:FilePath/Gen.G_Logo.svg",
  "gen.g esports": "https://commons.wikimedia.org/wiki/Special:FilePath/Gen.G_Logo.svg",
  "boston celtics": "https://en.wikipedia.org/wiki/Special:Redirect/file/Boston_Celtics.svg",
  "dallas mavericks": "https://en.wikipedia.org/wiki/Special:Redirect/file/Dallas_Mavericks_logo.svg",
  vitality: "https://upload.wikimedia.org/wikipedia/en/4/49/Team_Vitality_logo.svg",
  "team vitality": "https://upload.wikimedia.org/wikipedia/en/4/49/Team_Vitality_logo.svg",
  "g2 esports": "https://commons.wikimedia.org/wiki/Special:FilePath/Esports_organization_G2_Esports_logo.svg",
  g2: "https://commons.wikimedia.org/wiki/Special:FilePath/Esports_organization_G2_Esports_logo.svg",
  leviatan: "https://owcdn.net/img/61b8888cc3860.png",
};

/**
 * Local, original demo identities for individual competitors. They are not
 * presented as official marks: their purpose is to keep athletes visually
 * distinct without depending on portrait/image rights or a remote provider.
 */
export const COMPETITOR_IDENTITY_MAP: Record<string, string> = {
  furia: "/assets/teams/furia.svg",
  "furia esports": "/assets/teams/furia.svg",
  leviatan: "/assets/teams/leviatan.svg",
  lev: "/assets/teams/leviatan.svg",
  "carlos alcaraz": "/assets/teams/carlos-alcaraz.svg",
  alc: "/assets/teams/carlos-alcaraz.svg",
  "jannik sinner": "/assets/teams/jannik-sinner.svg",
  sin: "/assets/teams/jannik-sinner.svg",
  "max verstappen": "/assets/teams/max-verstappen.svg",
  verstappen: "/assets/teams/max-verstappen.svg",
  ver: "/assets/teams/max-verstappen.svg",
  "lando norris": "/assets/teams/lando-norris.svg",
  norris: "/assets/teams/lando-norris.svg",
  nor: "/assets/teams/lando-norris.svg",
  "charles leclerc": "/assets/teams/charles-leclerc.svg",
  leclerc: "/assets/teams/charles-leclerc.svg",
  lec: "/assets/teams/charles-leclerc.svg",
  "oscar piastri": "/assets/teams/oscar-piastri.svg",
  piastri: "/assets/teams/oscar-piastri.svg",
  pia: "/assets/teams/oscar-piastri.svg",
};

const IDENTITY_PALETTE = [
  ["#4f46e5", "#312e81"],
  ["#0891b2", "#164e63"],
  ["#0f766e", "#134e4a"],
  ["#c2410c", "#7c2d12"],
  ["#be123c", "#881337"],
  ["#7c3aed", "#4c1d95"],
] as const;

export function normalizeTeamName(value?: string | null) {
  return (value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

export function resolveTeamLogo(name?: string | null, code?: string | null, logoUrl?: string | null) {
  if (logoUrl?.trim()) return logoUrl.trim();
  const nameKey = normalizeTeamName(name);
  const codeKey = normalizeTeamName(code);
  return TEAM_LOGO_MAP[nameKey] || TEAM_LOGO_MAP[codeKey]
    || COMPETITOR_IDENTITY_MAP[nameKey] || COMPETITOR_IDENTITY_MAP[codeKey];
}

function stableIdentityBadge(name?: string | null, code?: string | null) {
  const normalized = normalizeTeamName(name || code) || "participante";
  let hash = 0;
  for (const character of normalized) hash = ((hash << 5) - hash + character.charCodeAt(0)) | 0;
  const [start, end] = IDENTITY_PALETTE[Math.abs(hash) % IDENTITY_PALETTE.length];
  const words = normalized.replace(/[^a-z0-9 ]/g, "").split(" ").filter(Boolean);
  const label = (code?.trim() || (words.length > 1 ? `${words[0][0]}${words[1][0]}` : words[0]?.slice(0, 2)) || "AP")
    .replace(/[^a-zA-Z0-9]/g, "")
    .slice(0, 3)
    .toUpperCase();
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><defs><linearGradient id="g" x1="8" y1="6" x2="56" y2="58"><stop stop-color="${start}"/><stop offset="1" stop-color="${end}"/></linearGradient></defs><path fill="url(#g)" d="M32 3 56 13v17c0 15-9.8 25.4-24 31C17.8 55.4 8 45 8 30V13L32 3Z"/><path fill="none" stroke="#fff" stroke-opacity=".3" stroke-width="2" d="M32 9 50 16v14c0 11-6.8 19-18 24-11.2-5-18-13-18-24V16l18-7Z"/><text x="32" y="37" fill="#fff" font-family="Arial,sans-serif" font-size="17" font-weight="800" text-anchor="middle">${label}</text></svg>`;
  return `data:image/svg+xml,${encodeURIComponent(svg)}`;
}

export function TeamLogo({ name, code, logoUrl, size = "md", className = "", decorative = true }: TeamLogoProps) {
  const resolved = useMemo(() => resolveTeamLogo(name, code, logoUrl), [name, code, logoUrl]);
  const curated = useMemo(() => {
    const nameKey = normalizeTeamName(name);
    const codeKey = normalizeTeamName(code);
    return TEAM_LOGO_MAP[nameKey] || TEAM_LOGO_MAP[codeKey];
  }, [name, code]);
  const localIdentity = useMemo(() => {
    const nameKey = normalizeTeamName(name);
    const codeKey = normalizeTeamName(code);
    return COMPETITOR_IDENTITY_MAP[nameKey] || COMPETITOR_IDENTITY_MAP[codeKey];
  }, [name, code]);
  const generatedIdentity = useMemo(() => stableIdentityBadge(name, code), [name, code]);
  const sources = useMemo(() => Array.from(new Set([
    resolved,
    curated,
    localIdentity,
    generatedIdentity,
    TEAM_PLACEHOLDER_PATH,
  ].filter((value): value is string => Boolean(value)))), [resolved, curated, localIdentity, generatedIdentity]);
  const [sourceIndex, setSourceIndex] = useState(0);
  useEffect(() => {
    setSourceIndex(0);
  }, [sources]);
  const source = sources[sourceIndex] || TEAM_PLACEHOLDER_PATH;
  const label = name || code || "Equipe sem identificação";
  const identityKey = normalizeTeamName(name || code);
  const fallback = source === TEAM_PLACEHOLDER_PATH;
  const generated = source.startsWith("data:image/svg+xml");
  const classes = `team-logo team-logo--${size}${fallback ? " team-logo--fallback" : ""}${generated ? " team-logo--generated" : ""}${identityKey.includes("furia") ? " team-logo--furia" : ""} ${className}`.trim();

  return (
    <span className={classes} data-team={identityKey || undefined} role={decorative ? undefined : "img"} aria-label={decorative ? undefined : label} aria-hidden={decorative ? "true" : undefined}>
      <img
        src={source}
        alt=""
        loading="lazy"
        decoding="async"
        onError={() => setSourceIndex((current) => Math.min(current + 1, sources.length - 1))}
      />
    </span>
  );
}
