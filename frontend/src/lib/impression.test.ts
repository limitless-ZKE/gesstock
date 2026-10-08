import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { attendreImages, imprimer } from "./impression";

/** Une image « en cours de chargement » : jsdom ne charge rien, on pilote donc `complete` et les événements à la main. */
function imageEnCours(zone: HTMLElement) {
  const image = document.createElement("img");
  let terminee = false;
  Object.defineProperty(image, "complete", { get: () => terminee });
  zone.appendChild(image);
  return { image, terminer: (evenement: "load" | "error") => { terminee = true; image.dispatchEvent(new Event(evenement)); } };
}

describe("impression", () => {
  let zone: HTMLElement;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(window, "print").mockImplementation(() => undefined);
    zone = document.createElement("div");
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("imprime tout de suite quand il n'y a pas d'image à attendre", async () => {
    await imprimer(zone);
    expect(window.print).toHaveBeenCalledTimes(1);
    await imprimer(null);
    expect(window.print).toHaveBeenCalledTimes(2);
  });

  it("n'imprime qu'une fois le logo chargé : sinon le ticket sortirait sans logo", async () => {
    const logo = imageEnCours(zone);

    const impression = imprimer(zone);
    await vi.advanceTimersByTimeAsync(500);
    expect(window.print).not.toHaveBeenCalled();

    logo.terminer("load");
    await impression;
    expect(window.print).toHaveBeenCalledTimes(1);
  });

  it("imprime aussi quand le logo est introuvable (le ticket de vente doit toujours sortir)", async () => {
    const logo = imageEnCours(zone);

    const impression = imprimer(zone);
    logo.terminer("error");
    await impression;

    expect(window.print).toHaveBeenCalledTimes(1);
  });

  it("n'attend pas indéfiniment un logo qui ne répond pas", async () => {
    imageEnCours(zone);

    const impression = imprimer(zone);
    await vi.advanceTimersByTimeAsync(3000);
    await impression;

    expect(window.print).toHaveBeenCalledTimes(1);
  });

  it("attend toutes les images, pas seulement la première", async () => {
    const premier = imageEnCours(zone);
    const second = imageEnCours(zone);

    let fini = false;
    const attente = attendreImages(zone).then(() => { fini = true; });
    premier.terminer("load");
    await vi.advanceTimersByTimeAsync(100);
    expect(fini).toBe(false);

    second.terminer("load");
    await attente;
    expect(fini).toBe(true);
  });
});
