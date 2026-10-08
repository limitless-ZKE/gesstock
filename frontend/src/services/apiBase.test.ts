import { afterEach, describe, expect, it, vi } from "vitest";

/** L'adresse de l'API se décide à la construction : la même interface doit marcher sur un poste ou sur tout le réseau. */
describe("adresse de l'API", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  const charger = async () => {
    vi.resetModules();
    return (await import("./api")).API_BASE_URL;
  };

  it("version construite : « /api » sur le serveur qui sert l'interface (donc valable depuis n'importe quel poste)", async () => {
    vi.stubEnv("PROD", true);
    vi.stubEnv("VITE_API_BASE_URL", undefined);
    expect(await charger()).toBe("/api");
  });

  it("développement : le serveur Java tourne à part, sur le port 8080", async () => {
    vi.stubEnv("PROD", false);
    vi.stubEnv("VITE_API_BASE_URL", undefined);
    expect(await charger()).toBe("http://localhost:8080/api");
  });

  it("VITE_API_BASE_URL impose une autre adresse, dans les deux cas", async () => {
    vi.stubEnv("VITE_API_BASE_URL", "http://serveur-central:9000/api");
    vi.stubEnv("PROD", true);
    expect(await charger()).toBe("http://serveur-central:9000/api");
    vi.stubEnv("PROD", false);
    expect(await charger()).toBe("http://serveur-central:9000/api");
  });
});
