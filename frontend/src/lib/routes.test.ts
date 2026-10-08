import { describe, expect, it } from "vitest";
import { normaliserRole, pageDAccueil } from "./routes";

describe("normaliserRole", () => {
  it("accepte « ROLE_ADMIN », « admin » et les espaces", () => {
    expect(normaliserRole("ROLE_ADMIN")).toBe("ADMIN");
    expect(normaliserRole(" manager ")).toBe("MANAGER");
  });
});

describe("pageDAccueil", () => {
  it("envoie le pilotage sur le tableau de bord", () => {
    for (const role of ["ADMIN", "MANAGER", "GESTIONNAIRE", "ROLE_MANAGER"]) {
      expect(pageDAccueil([role])).toBe("/dashboard");
    }
  });

  it("envoie le caissier à la caisse, pas sur un tableau de bord qui lui est interdit", () => {
    expect(pageDAccueil(["CAISSIER"])).toBe("/caisse");
  });

  it("donne la priorité au pilotage en cas de cumul de rôles", () => {
    expect(pageDAccueil(["CAISSIER", "MANAGER"])).toBe("/dashboard");
  });

  it("retombe sur le tableau de bord sans rôle connu", () => {
    expect(pageDAccueil([])).toBe("/dashboard");
    expect(pageDAccueil(["STAGIAIRE"])).toBe("/dashboard");
  });
});
