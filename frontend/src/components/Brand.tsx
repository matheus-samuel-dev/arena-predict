import { brand } from "../app/branding";

export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <span className={`brand-lockup ${compact ? "brand-lockup--compact" : ""}`} aria-label={brand.name}>
      <span className="brand-symbol" aria-hidden="true">
        <i className="brand-symbol__core" />
        <i className="brand-symbol__pulse" />
      </span>
      {!compact && (
        <span className="brand-wordmark">
          <b>{brand.name}</b>
        </span>
      )}
    </span>
  );
}
