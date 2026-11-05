import { describe, expect, it } from "vitest";
import { bornesDePeriode, dansLesBornes, libelleBornes } from "./periodes";

describe("périodes pour classer une liste par date", () => {
  it("toutes les dates : aucune borne, tout est retenu", () => {
    const bornes = bornesDePeriode("all", "2026-11-05");

    expect(bornes).toEqual({ debut: null, fin: null });
    expect(dansLesBornes("1999-01-01", bornes)).toBe(true);
    expect(libelleBornes(bornes)).toBe("");
  });

  it("aujourd'hui, une seule journée", () => {
    const bornes = bornesDePeriode("today", "2026-11-05");

    expect(bornes).toEqual({ debut: "2026-11-05", fin: "2026-11-05" });
    expect(dansLesBornes("2026-11-05", bornes)).toBe(true);
    expect(dansLesBornes("2026-11-04", bornes)).toBe(false);
    expect(dansLesBornes("2026-11-06", bornes)).toBe(false);
  });

  it("la semaine va du lundi au dimanche", () => {
    // le jeudi 5 novembre 2026
    expect(bornesDePeriode("week", "2026-11-05")).toEqual({ debut: "2026-11-02", fin: "2026-11-08" });
    // un lundi et un dimanche : la même semaine
    expect(bornesDePeriode("week", "2026-11-02")).toEqual({ debut: "2026-11-02", fin: "2026-11-08" });
    expect(bornesDePeriode("week", "2026-11-08")).toEqual({ debut: "2026-11-02", fin: "2026-11-08" });
    // à cheval sur deux mois
    expect(bornesDePeriode("week", "2026-12-01")).toEqual({ debut: "2026-11-30", fin: "2026-12-06" });
  });

  it("le mois va du 1er au dernier jour, février et années bissextiles compris", () => {
    expect(bornesDePeriode("month", "2026-11-05")).toEqual({ debut: "2026-11-01", fin: "2026-11-30" });
    expect(bornesDePeriode("month", "2026-12-31")).toEqual({ debut: "2026-12-01", fin: "2026-12-31" });
    expect(bornesDePeriode("month", "2026-02-10")).toEqual({ debut: "2026-02-01", fin: "2026-02-28" });
    expect(bornesDePeriode("month", "2028-02-10")).toEqual({ debut: "2028-02-01", fin: "2028-02-29" });
  });

  it("le mois dernier, y compris en janvier", () => {
    expect(bornesDePeriode("lastMonth", "2026-11-05")).toEqual({ debut: "2026-10-01", fin: "2026-10-31" });
    expect(bornesDePeriode("lastMonth", "2026-01-15")).toEqual({ debut: "2025-12-01", fin: "2025-12-31" });
    expect(bornesDePeriode("lastMonth", "2026-03-31")).toEqual({ debut: "2026-02-01", fin: "2026-02-28" });
  });

  it("dates précises : une borne peut manquer", () => {
    expect(bornesDePeriode("custom", "2026-11-05", "2026-10-10", "2026-10-20")).toEqual({ debut: "2026-10-10", fin: "2026-10-20" });
    const depuis = bornesDePeriode("custom", "2026-11-05", "2026-10-10", "");
    expect(depuis).toEqual({ debut: "2026-10-10", fin: null });
    expect(dansLesBornes("2026-10-09", depuis)).toBe(false);
    expect(dansLesBornes("2030-01-01", depuis)).toBe(true);
    const jusque = bornesDePeriode("custom", "2026-11-05", "", "2026-10-20");
    expect(dansLesBornes("2026-10-20", jusque)).toBe(true);
    expect(dansLesBornes("2026-10-21", jusque)).toBe(false);
  });

  it("écrit la période en clair", () => {
    expect(libelleBornes({ debut: "2026-11-01", fin: "2026-11-30" })).toBe("Du 01/11/2026 au 30/11/2026");
    expect(libelleBornes({ debut: "2026-11-05", fin: "2026-11-05" })).toBe("Le 05/11/2026");
    expect(libelleBornes({ debut: "2026-11-01", fin: null })).toBe("À partir du 01/11/2026");
    expect(libelleBornes({ debut: null, fin: "2026-11-30" })).toBe("Jusqu’au 30/11/2026");
  });
});
