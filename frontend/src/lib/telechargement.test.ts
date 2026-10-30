import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { PontAndroid } from "./android";
import { telecharger } from "./telechargement";

type FenetreAvecPont = Window & { GesStockAndroid?: PontAndroid };

describe("telecharger", () => {
  afterEach(() => {
    delete (window as FenetreAvecPont).GesStockAndroid;
  });

  describe("dans un navigateur", () => {
    const creer = vi.fn(() => "blob:essai");
    const liberer = vi.fn();

    beforeEach(() => {
      // jsdom ne fournit pas ces deux fonctions du navigateur.
      Object.defineProperty(URL, "createObjectURL", { value: creer, configurable: true, writable: true });
      Object.defineProperty(URL, "revokeObjectURL", { value: liberer, configurable: true, writable: true });
    });

    it("propose le fichier en téléchargement sous son nom", async () => {
      const liens: HTMLAnchorElement[] = [];
      vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
        liens.push(this);
      });

      await telecharger(new Blob(["%PDF-1.4"], { type: "application/pdf" }), "journal.pdf");

      expect(liens).toHaveLength(1);
      expect(liens[0].download).toBe("journal.pdf");
      expect(liens[0].href).toBe("blob:essai");
      expect(liberer).toHaveBeenCalledWith("blob:essai");
    });
  });

  describe("dans l'application Android", () => {
    it("confie le fichier au téléphone : nom, type et contenu en base 64", async () => {
      const enregistrerFichier = vi.fn();
      (window as FenetreAvecPont).GesStockAndroid = { enregistrerFichier, imprimer: vi.fn() };

      await telecharger(new Blob(["%PDF-1.4"], { type: "application/pdf" }), "journal.pdf");

      expect(enregistrerFichier).toHaveBeenCalledWith("journal.pdf", "application/pdf", btoa("%PDF-1.4"));
    });

    it("déduit le type de l'extension quand le fichier arrive sans type", async () => {
      const enregistrerFichier = vi.fn();
      (window as FenetreAvecPont).GesStockAndroid = { enregistrerFichier, imprimer: vi.fn() };

      await telecharger(new Blob(["PK"]), "inventaire.XLSX");

      expect(enregistrerFichier).toHaveBeenCalledWith(
        "inventaire.XLSX",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        btoa("PK"),
      );
    });

    it("ne passe pas par le navigateur, qui ne sait pas enregistrer ce fichier dans une vue web Android", async () => {
      (window as FenetreAvecPont).GesStockAndroid = { enregistrerFichier: vi.fn(), imprimer: vi.fn() };
      const clic = vi.spyOn(HTMLAnchorElement.prototype, "click");

      await telecharger(new Blob(["x"], { type: "application/pdf" }), "a.pdf");

      expect(clic).not.toHaveBeenCalled();
    });
  });
});
