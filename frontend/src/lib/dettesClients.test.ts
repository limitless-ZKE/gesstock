import { describe, expect, it } from "vitest";
import { dettesParClient, FILTRES_CLIENTS, joursEntre, retenirClient, trieParDette } from "./dettesClients";

const dette = (clientId: number, dateCreation: string, solde: number, statut = "EN_COURS") => ({ clientId, dateCreation, solde, statut });

describe("dettes des clients", () => {
  it("compte les jours entre deux dates sans décalage d'heure d'été", () => {
    expect(joursEntre("2026-03-20", "2026-03-30")).toBe(10);
    expect(joursEntre("2026-10-01", "2026-11-05")).toBe(35);
    expect(joursEntre("2026-11-05", "2026-11-05")).toBe(0);
  });

  it("totalise les dettes en cours d'un client et retient la plus ancienne ; les dettes soldées ou annulées ne comptent pas", () => {
    const resumes = dettesParClient([
      dette(1, "2026-10-20", 1000),
      dette(1, "2026-09-01", 2500, "PARTIELLEMENT_REGLEE"),
      dette(1, "2026-08-01", 0, "REGLEE"),
      dette(2, "2026-10-30", 400, "ANNULEE"),
      dette(3, "2026-11-04", 300),
    ], "2026-11-05");

    expect(resumes.get(1)).toEqual({ du: 3500, plusAncienne: "2026-09-01", jours: 65, nombre: 2 });
    expect(resumes.has(2)).toBe(false);
    expect(resumes.get(3)).toMatchObject({ du: 300, jours: 1, nombre: 1 });
  });

  it("applique les mêmes filtres que la liste imprimée", () => {
    const resumes = dettesParClient([dette(1, "2026-09-01", 100), dette(3, "2026-11-04", 300)], "2026-11-05");

    expect(retenirClient("all", undefined)).toBe(true);
    expect(retenirClient("withDebt", resumes.get(1))).toBe(true);
    expect(retenirClient("withDebt", resumes.get(2))).toBe(false);
    expect(retenirClient("noDebt", resumes.get(2))).toBe(true);
    expect(retenirClient("noDebt", resumes.get(1))).toBe(false);
    expect(retenirClient("old60", resumes.get(1))).toBe(true);    // 65 jours
    expect(retenirClient("old90", resumes.get(1))).toBe(false);
    expect(retenirClient("old30", resumes.get(3))).toBe(false);   // 1 jour
    expect(retenirClient("old30", undefined)).toBe(false);
  });

  it("demande au serveur le même filtre et le même nombre de jours", () => {
    expect(FILTRES_CLIENTS.old60).toMatchObject({ filtre: "dette-ancienne", jours: 60, nomDuFichier: "gesstock-clients-dette-ancienne-60j" });
    expect(FILTRES_CLIENTS.withDebt.filtre).toBe("avec-dette");
    expect(trieParDette("old30")).toBe(true);
    expect(trieParDette("all")).toBe(false);
  });
});
