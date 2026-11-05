import { describe, expect, it } from "vitest";
import { coutAVerifier, margeUnitaire, tauxDeMarge } from "./marge";

describe("marge d'un article", () => {
  it("la marge est le prix de vente moins le coût, négative sous le coût", () => {
    expect(margeUnitaire(500, 300)).toBe(200);
    expect(margeUnitaire(250, 300)).toBe(-50);
  });

  it("le taux se calcule sur le prix de vente", () => {
    expect(tauxDeMarge(500, 300)).toBeCloseTo(40);
    expect(tauxDeMarge(1000, 600)).toBeCloseTo(40);
    expect(tauxDeMarge(250, 300)).toBeCloseTo(-20);
  });

  it("n'invente pas de taux quand le coût est inconnu ou qu'il n'y a pas de vente", () => {
    expect(tauxDeMarge(500, 0)).toBeNull();
    expect(tauxDeMarge(0, 300)).toBeNull();
    expect(coutAVerifier(0)).toBe(true);
    expect(coutAVerifier(300)).toBe(false);
  });
});
