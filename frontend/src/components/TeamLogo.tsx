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
  furia: "https://us.furia.gg/images/brand/logotipo-white.svg",
  "furia esports": "https://us.furia.gg/images/brand/logotipo-white.svg",
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
};

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
  return TEAM_LOGO_MAP[nameKey] || TEAM_LOGO_MAP[codeKey];
}

export function TeamLogo({ name, code, logoUrl, size = "md", className = "", decorative = true }: TeamLogoProps) {
  const resolved = useMemo(() => resolveTeamLogo(name, code, logoUrl), [name, code, logoUrl]);
  const curated = useMemo(() => {
    const nameKey = normalizeTeamName(name);
    const codeKey = normalizeTeamName(code);
    return TEAM_LOGO_MAP[nameKey] || TEAM_LOGO_MAP[codeKey];
  }, [name, code]);
  const [source, setSource] = useState(resolved || TEAM_PLACEHOLDER_PATH);
  useEffect(() => {
    setSource(resolved || TEAM_PLACEHOLDER_PATH);
  }, [resolved]);
  const label = name || code || "Equipe sem identificação";
  const identityKey = normalizeTeamName(name || code);
  const fallback = source === TEAM_PLACEHOLDER_PATH;
  const classes = `team-logo team-logo--${size}${fallback ? " team-logo--fallback" : ""}${identityKey.includes("furia") ? " team-logo--furia" : ""} ${className}`.trim();

  return (
    <span className={classes} data-team={identityKey || undefined} role={decorative ? undefined : "img"} aria-label={decorative ? undefined : label} aria-hidden={decorative ? "true" : undefined}>
      <img
        src={source}
        alt=""
        loading="lazy"
        decoding="async"
        onError={() => {
          if (curated && source !== curated) setSource(curated);
          else if (source !== TEAM_PLACEHOLDER_PATH) setSource(TEAM_PLACEHOLDER_PATH);
        }}
      />
    </span>
  );
}
