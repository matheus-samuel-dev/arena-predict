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
 * Every image has an initials fallback so a provider outage never creates a
 * broken-image state in the interface.
 */
export const TEAM_LOGO_MAP: Record<string, string> = {
  palmeiras: "https://upload.wikimedia.org/wikipedia/commons/1/10/Palmeiras_logo.svg",
  flamengo: "https://upload.wikimedia.org/wikipedia/commons/9/96/Clube_de_Regatas_do_Flamengo_logo.svg",
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

export function teamInitials(name?: string | null, code?: string | null) {
  const source = (name || code || "EQ").trim();
  const parts = source.split(/\s+/).filter(Boolean);
  return (parts.length > 1 ? `${parts[0][0]}${parts[1][0]}` : source.slice(0, 2)).toUpperCase();
}

export function resolveTeamLogo(name?: string | null, code?: string | null, logoUrl?: string | null) {
  if (logoUrl?.trim()) return logoUrl.trim();
  const nameKey = normalizeTeamName(name);
  const codeKey = normalizeTeamName(code);
  return TEAM_LOGO_MAP[nameKey] || TEAM_LOGO_MAP[codeKey];
}

export function TeamLogo({ name, code, logoUrl, size = "md", className = "", decorative = true }: TeamLogoProps) {
  const [failed, setFailed] = useState(false);
  const resolved = useMemo(() => resolveTeamLogo(name, code, logoUrl), [name, code, logoUrl]);
  useEffect(() => {
    setFailed(false);
  }, [resolved]);
  const label = name || code || "Equipe sem identificação";
  const initials = teamInitials(name, code);
  const classes = `team-logo team-logo--${size}${failed || !resolved ? " team-logo--fallback" : ""} ${className}`.trim();

  return (
    <span className={classes} role={decorative ? undefined : "img"} aria-label={decorative ? undefined : label} aria-hidden={decorative ? "true" : undefined}>
      {resolved && !failed ? <img src={resolved} alt="" loading="lazy" decoding="async" onError={() => setFailed(true)} /> : <span aria-hidden="true">{initials}</span>}
    </span>
  );
}
