import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { erreurHttp, erreurReseau } from "../test/axios";
import { api } from "./api";

/** L'intercepteur de réponse prévient l'application par des événements : on vérifie lesquels, et seulement eux. */
describe("intercepteur d'erreurs de l'API", () => {
  const nonAutorise = vi.fn();
  const erreurApi = vi.fn<(detail: string) => void>();
  const surErreurApi = (event: Event) => erreurApi((event as CustomEvent<string>).detail);
  const adaptateurInitial = api.defaults.adapter;

  beforeEach(() => {
    window.addEventListener("gesstock:unauthorized", nonAutorise);
    window.addEventListener("gesstock:api-error", surErreurApi);
  });

  afterEach(() => {
    window.removeEventListener("gesstock:unauthorized", nonAutorise);
    window.removeEventListener("gesstock:api-error", surErreurApi);
    nonAutorise.mockReset();
    erreurApi.mockReset();
    api.defaults.adapter = adaptateurInitial;
  });

  const echouer = (erreur: Error) => {
    api.defaults.adapter = () => Promise.reject(erreur);
    return api.get("/x").catch(() => undefined);
  };

  it("une session expirée (401) renvoie vers la connexion, sans bandeau d'erreur", async () => {
    await echouer(erreurHttp(401, { detail: "Non authentifié" }));
    expect(nonAutorise).toHaveBeenCalledTimes(1);
    expect(erreurApi).not.toHaveBeenCalled();
  });

  it("un refus de droits (403) s'affiche avec la référence à communiquer", async () => {
    await echouer(erreurHttp(403, { detail: "Droits insuffisants", requestId: "12345678-aaaa-bbbb" }));
    expect(erreurApi).toHaveBeenCalledWith("Droits insuffisants (réf. 12345678)");
    expect(nonAutorise).not.toHaveBeenCalled();
  });

  it("une panne serveur (500) et un réseau coupé s'affichent", async () => {
    await echouer(erreurHttp(500, { detail: "Erreur interne" }));
    await echouer(erreurReseau());
    expect(erreurApi).toHaveBeenCalledTimes(2);
    expect(erreurApi.mock.calls[1][0]).toContain("injoignable");
  });

  it("une erreur métier (409) reste à la charge de l'écran : aucun événement", async () => {
    await echouer(erreurHttp(409, { detail: "Stock insuffisant" }));
    expect(nonAutorise).not.toHaveBeenCalled();
    expect(erreurApi).not.toHaveBeenCalled();
  });

  it("laisse l'erreur remonter à l'appelant", async () => {
    api.defaults.adapter = () => Promise.reject(erreurHttp(409, { detail: "Stock insuffisant" }));
    await expect(api.get("/x")).rejects.toMatchObject({ response: { status: 409 } });
  });
});
