import { brand } from "../app/branding";

const brandMark = "/assets/brand/arena-predict-mark.svg";

type BrandProps = {
  compact?: boolean;
  className?: string;
};

export function Brand({ compact = false, className = "" }: BrandProps) {
  return (
    <span
      className={["brand-lockup", compact && "brand-lockup--compact", className].filter(Boolean).join(" ")}
      role="img"
      aria-label={brand.name}
    >
      <img className="brand-symbol" src={brandMark} width="40" height="40" alt="" aria-hidden="true" draggable="false" />
      {!compact && (
        <span className="brand-wordmark" aria-hidden="true">
          <b>{brand.name}</b>
        </span>
      )}
    </span>
  );
}
