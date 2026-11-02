import { describe, expect, it } from "vitest";
import { arrondirQuantite, formatQuantite, lireQuantite, nettoyerSaisieQuantite } from "./quantite";

describe("quantités à virgule", () => {
  it("s'affichent à la française, sans zéros inutiles", () => {
    expect(formatQuantite(2.5)).toBe("2,5");
    expect(formatQuantite(0.125)).toBe("0,125");
    expect(formatQuantite(12)).toBe("12");
  });

  it("se lisent avec une virgule ou un point, 3 décimales au plus", () => {
    expect(lireQuantite("2,5")).toBe(2.5);
    expect(lireQuantite("0.125")).toBe(0.125);
    expect(lireQuantite("3")).toBe(3);
    expect(lireQuantite("1,2345")).toBeNull();
    expect(lireQuantite("abc")).toBeNull();
    expect(lireQuantite("-1")).toBeNull();
  });

  it("une saisie ne garde que des chiffres et une seule virgule", () => {
    expect(nettoyerSaisieQuantite("2.5")).toBe("2,5");
    expect(nettoyerSaisieQuantite("1,23456")).toBe("1,234");
    expect(nettoyerSaisieQuantite("1,2,3")).toBe("1,23");
    expect(nettoyerSaisieQuantite(",5")).toBe("0,5");
    expect(nettoyerSaisieQuantite("007")).toBe("7");
  });

  it("l'arrondi efface les restes de la virgule flottante", () => {
    expect(arrondirQuantite(0.1 + 0.2)).toBe(0.3);
    expect(arrondirQuantite(2.5 + 1)).toBe(3.5);
  });
});
