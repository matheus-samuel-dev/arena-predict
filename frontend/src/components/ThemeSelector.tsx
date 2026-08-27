import { Check, Moon, Sun } from "lucide-react";
import { useState } from "react";
import { getStoredTheme, setTheme, type Theme } from "../app/theme";

export function ThemeSelector({ compact = false, value, onChange }: { compact?: boolean; value?: Theme; onChange?: (theme: Theme) => void }) {
  const [internalTheme, setInternalTheme] = useState<Theme>(() => getStoredTheme());
  const theme = value ?? internalTheme;

  function choose(nextTheme: Theme) {
    setInternalTheme(nextTheme);
    setTheme(nextTheme);
    onChange?.(nextTheme);
  }

  return (
    <div className={`theme-selector ${compact ? "theme-selector--compact" : ""}`} aria-label="Aparência da interface">
      {!compact && <span className="theme-selector__label">Aparência</span>}
      <div className="theme-selector__options" role="group" aria-label="Tema">
        <button type="button" className={theme === "light" ? "active" : ""} onClick={() => choose("light")} aria-pressed={theme === "light"}>
          <Sun size={15} aria-hidden="true" /> <span>Claro</span>{theme === "light" && <Check size={14} aria-hidden="true" />}
        </button>
        <button type="button" className={theme === "dark" ? "active" : ""} onClick={() => choose("dark")} aria-pressed={theme === "dark"}>
          <Moon size={15} aria-hidden="true" /> <span>Escuro</span>{theme === "dark" && <Check size={14} aria-hidden="true" />}
        </button>
      </div>
    </div>
  );
}
