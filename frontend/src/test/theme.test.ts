import { beforeEach, describe, expect, it } from "vitest";
import { getStoredTheme, initializeTheme, setTheme } from "../app/theme";

describe("preferência de tema", () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.dataset.theme = "";
  });

  it("começa no claro quando não existe preferência salva", () => {
    expect(initializeTheme()).toBe("light");
    expect(document.documentElement.dataset.theme).toBe("light");
  });

  it("persiste o modo escuro e aplica o atributo da raiz", () => {
    setTheme("dark");
    expect(getStoredTheme()).toBe("dark");
    expect(document.documentElement.dataset.theme).toBe("dark");
  });
});

