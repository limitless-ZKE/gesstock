import { describe, expect, it } from "vitest";
import { erreurHttp, erreurReseau } from "../test/axios";
import { apiErrorMessage, parseApiError } from "./apiError";

describe("parseApiError", () => {
  it("lit le contrat problem+json du serveur", () => {
    const erreur = parseApiError(erreurHttp(409, {
      detail: "Stock insuffisant pour SKU-1",
      code: "STOCK_INSUFFISANT",
      requestId: "abc-123",
      erreurs: { quantite: "doit être positive" },
    }));
    expect(erreur).toEqual({
      status: 409,
      code: "STOCK_INSUFFISANT",
      message: "Stock insuffisant pour SKU-1",
      fieldErrors: { quantite: "doit être positive" },
      requestId: "abc-123",
    });
  });

  it("signale un serveur injoignable par un code stable", () => {
    const erreur = parseApiError(erreurReseau());
    expect(erreur.status).toBeNull();
    expect(erreur.code).toBe("RESEAU");
    expect(erreur.message).toContain("injoignable");
  });

  it("retombe sur le texte de l'écran quand le corps n'explique rien", () => {
    expect(parseApiError(erreurHttp(500, ""), "Chargement impossible.").message).toBe("Chargement impossible.");
    expect(parseApiError(erreurHttp(500, "<html>"), "Chargement impossible.").message).toBe("Chargement impossible.");
  });

  it("accepte l'ancien champ « message » et l'en-tête X-Request-Id", () => {
    const erreur = parseApiError(erreurHttp(400, { message: "Ancien format" }, { "x-request-id": "req-9" }));
    expect(erreur.message).toBe("Ancien format");
    expect(erreur.requestId).toBe("req-9");
  });

  it("ne plante pas sur une erreur qui ne vient pas d'axios", () => {
    const erreur = parseApiError(new Error("boom"), "Repli");
    expect(erreur).toMatchObject({ status: null, code: null, message: "Repli", fieldErrors: {}, requestId: null });
  });
});

describe("apiErrorMessage", () => {
  it("donne le message du serveur s'il existe, sinon le repli", () => {
    expect(apiErrorMessage(erreurHttp(403, { detail: "Droits insuffisants" }), "Repli")).toBe("Droits insuffisants");
    expect(apiErrorMessage(undefined, "Repli")).toBe("Repli");
  });
});
