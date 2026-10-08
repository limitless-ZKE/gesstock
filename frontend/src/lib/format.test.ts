import { describe, expect, it } from "vitest";
import { formatPercent, money, moneyPrecise } from "./format";

/** Intl sépare les milliers par des espaces insécables fines : on les ramène à une espace pour comparer. */
const normaliser = (texte: string) => texte.replace(/[\u00a0\u202f]/g, " ");

describe("formats de montants", () => {
  it("affiche les francs CFA sans décimale, milliers séparés", () => {
    expect(normaliser(money.format(1500))).toBe("1 500 F CFA");
    expect(normaliser(money.format(1200000))).toBe("1 200 000 F CFA");
  });

  it("arrondit à l'unité et garde le signe d'un manque", () => {
    expect(normaliser(money.format(-373500))).toBe("-373 500 F CFA");
    expect(normaliser(money.format(0))).toBe("0 F CFA");
  });

  it("montre les centimes d'un prix négocié, mais pas les zéros inutiles", () => {
    expect(normaliser(moneyPrecise.format(1234.5))).toBe("1 234,5 F CFA");
    expect(normaliser(moneyPrecise.format(1000))).toBe("1 000 F CFA");
  });

  it("formate un pourcentage avec la virgule décimale française", () => {
    expect(formatPercent(10.2)).toBe("10,2");
    expect(formatPercent(25)).toBe("25");
  });
});
