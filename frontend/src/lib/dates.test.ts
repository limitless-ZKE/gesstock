import { afterEach, describe, expect, it, vi } from "vitest";
import { aujourdhui, debutDuMois, decalerJour, hier, jourCourt, libellePeriode, versJourIso } from "./dates";

describe("dates du commerce", () => {
  afterEach(() => vi.useRealTimers());

  it("écrit un jour AAAA-MM-JJ avec l'horloge du poste, pas celle de Greenwich", () => {
    // 00:30 le 1er octobre dans le fuseau du poste : toISOString() donnerait encore le 30 septembre dès que le fuseau est en avance sur UTC
    expect(versJourIso(new Date(2026, 9, 1, 0, 30))).toBe("2026-10-01");
    expect(versJourIso(new Date(2026, 11, 31, 23, 59))).toBe("2026-12-31");
  });

  it("calcule aujourd'hui, hier et le début du mois", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 9, 9, 10, 0));
    expect(aujourdhui()).toBe("2026-10-09");
    expect(hier()).toBe("2026-10-08");
    expect(debutDuMois()).toBe("2026-10-01");
  });

  it("recule d'un jour à travers un changement de mois ou d'année, y compris une année bissextile", () => {
    expect(decalerJour("2026-10-01", -1)).toBe("2026-09-30");
    expect(decalerJour("2027-01-01", -1)).toBe("2026-12-31");
    expect(decalerJour("2028-03-01", -1)).toBe("2028-02-29");
    expect(decalerJour("2026-10-09", 3)).toBe("2026-10-12");
  });

  it("présente une journée seule en toutes lettres et une période avec ses deux bornes", () => {
    expect(libellePeriode("2026-10-09", "2026-10-09")).toBe("vendredi 9 octobre 2026");
    expect(libellePeriode("2026-10-01", "2026-10-09")).toBe("Du 01/10/2026 au 09/10/2026");
    expect(jourCourt("2026-03-05")).toBe("05/03/2026");
  });
});
