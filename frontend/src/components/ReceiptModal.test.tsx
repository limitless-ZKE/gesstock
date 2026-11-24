import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useEntreprise, type Entreprise } from "../entreprise/EntrepriseContext";
import { imprimer } from "../lib/impression";
import ReceiptModal, { totauxDuTicket, type Sale } from "./ReceiptModal";

vi.mock("../services/api", () => ({ api: {}, API_BASE_URL: "http://serveur/api" }));
vi.mock("../lib/impression", () => ({ imprimer: vi.fn().mockResolvedValue(undefined) }));
vi.mock("../entreprise/EntrepriseContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../entreprise/EntrepriseContext")>()),
  useEntreprise: vi.fn(),
}));

const ENTREPRISE: Entreprise = {
  nom: "Baobab SARL", adresse: "12 rue du Marché, Dakar", telephone: "+221 33 800 00 00", email: null,
  identifiantLegal: "RCCM SN-DKR-2020-B-1234", siteWeb: null, piedDePage: "À bientôt chez Baobab !", logoSurTickets: true,
  logo: { empreinte: "empreinte-1", largeur: 600, hauteur: 200 }, misAJourLe: "2026-10-08T10:00:00", misAJourPar: "admin",
};

const VENTE: Sale = {
  id: 1, code: "VTE-0001", dateVente: "2026-10-08T10:30:00", statut: "VALIDEE", client: null, vendeur: "caissier", aCredit: false,
  montantTotal: 2000, montantHT: 2000, montantTva: 0, tauxTva: 0, modeReglement: "ESPECES", montantRecu: 2000,
  lignes: [{ articleId: 1, reference: "ALIM-001", designation: "Riz local", quantite: 2, prixUnitaire: 1000, montantLigne: 2000, promotionId: null, remiseUnitaire: null, prixCatalogue: 1000 }],
};

function afficher(entreprise: Entreprise | null, vente: Sale = VENTE, duplicata = false) {
  vi.mocked(useEntreprise).mockReturnValue({ entreprise, rafraichir: vi.fn(), logoUrl: vi.fn() });
  render(<ReceiptModal sale={vente} onClose={vi.fn()} duplicata={duplicata} />);
}

const ligne = (articleId: number, designation: string, quantite: number, prixCatalogue: number, montantLigne: number, remiseUnitaire: number | null = null) => ({
  articleId, reference: `REF-${articleId}`, designation, quantite, prixUnitaire: prixCatalogue, montantLigne, promotionId: null, remiseUnitaire, prixCatalogue,
});

/** La vente de la facture papier de référence : 11 lignes, 106 800 F, aucune remise. */
const FACTURE_BERU: Sale = {
  ...VENTE, id: 333, code: "333", dateVente: "2026-11-17T14:50:13", client: "ALEX", vendeur: "Admin", aCredit: true, modeReglement: null, montantRecu: null,
  montantTotal: 106800, montantHT: 106800,
  lignes: [
    ligne(1, "Sangria Forte", 1, 13500, 13500), ligne(2, "3X PM", 1, 9000, 9000), ligne(3, "Malta", 2, 7800, 15600),
    ligne(4, "Cocktail", 2, 7800, 15600), ligne(5, "Desperado", 1, 13000, 13000), ligne(6, "Becker's", 1, 10000, 10000),
    ligne(7, "Alomo", 2, 1900, 3800), ligne(8, "Sun Set Max GM", 0.5, 12000, 6000), ligne(9, "Voltic 1,5l", 1, 3400, 3400),
    ligne(10, "Vitale 1,5l", 1, 3400, 3400), ligne(11, "Vody", 1, 13500, 13500),
  ],
};

describe("ticket de vente", () => {
  beforeEach(() => {
    vi.mocked(imprimer).mockClear();
  });

  it("porte le nom, les coordonnées, le logo et le message de fin de l'entreprise", () => {
    afficher(ENTREPRISE);
    const ticket = document.getElementById("sales-receipt")!;
    expect(ticket).toHaveTextContent("Baobab SARL");
    expect(ticket).toHaveTextContent("12 rue du Marché, Dakar");
    expect(ticket).toHaveTextContent("RCCM SN-DKR-2020-B-1234");
    expect(ticket).toHaveTextContent("À bientôt chez Baobab !");
    expect(ticket).toHaveTextContent("VTE-0001");
    expect(screen.getByAltText("Logo Baobab SARL")).toHaveAttribute("src", expect.stringContaining("taille=ticket"));
    expect(ticket).not.toHaveTextContent(/gesstock/i);
  });

  it("n'imprime pas de message de fin quand l'entreprise n'en a pas choisi", () => {
    afficher({ ...ENTREPRISE, piedDePage: null });
    expect(document.getElementById("sales-receipt")).not.toHaveTextContent("Merci");
  });

  it("garde le message d'usage tant que les informations de l'entreprise ne sont pas chargées", () => {
    afficher(null);
    const ticket = document.getElementById("sales-receipt")!;
    expect(ticket).toHaveTextContent("Merci de votre visite !");
    expect(ticket).toHaveTextContent("VTE-0001");   // le ticket reste imprimable, sans en-tête personnalisé
  });

  it("imprime le ticket après le chargement de son logo", async () => {
    afficher(ENTREPRISE);
    await userEvent.setup().click(screen.getByRole("button", { name: /Imprimer/ }));

    expect(imprimer).toHaveBeenCalledTimes(1);
    expect(vi.mocked(imprimer).mock.calls[0][0]).toBe(document.getElementById("sales-receipt"));
  });
});

describe("mise en page du ticket : en-tête, tableau, totaux", () => {
  it("annonce la facture, la date, le client, le paiement et le vendeur", () => {
    afficher(ENTREPRISE, FACTURE_BERU);
    const ticket = document.getElementById("sales-receipt")!;
    expect(ticket).toHaveTextContent("FACTURE N° 333");
    expect(ticket).toHaveTextContent("Date : 2026-11-17 14:50:13");
    expect(ticket).toHaveTextContent("Client : ALEX");
    expect(ticket).toHaveTextContent("Paiement : Crédit - Vendeur : Admin");
  });

  it("met un client de passage quand la vente n'a pas de client", () => {
    afficher(ENTREPRISE);
    expect(document.getElementById("sales-receipt")).toHaveTextContent("Client : Client de passage");
    expect(document.getElementById("sales-receipt")).toHaveTextContent("Paiement : Espèces - Vendeur : caissier");
  });

  it("présente les articles en tableau fermé N° / Produit / Qté / Prix / Montant, sans colonne Remise", () => {
    afficher(ENTREPRISE, FACTURE_BERU);
    expect(screen.getAllByRole("columnheader").map((cellule) => cellule.textContent)).toEqual(["N°", "Produit", "Qté", "Prix", "Montant"]);
    const lignes = screen.getAllByRole("row").slice(1);
    expect(lignes).toHaveLength(11);
    expect(Array.from(lignes[2].querySelectorAll("td")).map((cellule) => cellule.textContent?.replace(/\s/g, " "))).toEqual(["3", "Malta", "2,0", "7 800 F", "15 600 F"]);
    expect(lignes[7]).toHaveTextContent("Sun Set Max GM");
    expect(lignes[7]).toHaveTextContent("0,5");
    expect(lignes[7]).toHaveTextContent("6 000 F");
  });

  it("totalise comme la facture de référence : brut, remise à 0 %, net, acompte et reste à payer", () => {
    afficher(ENTREPRISE, FACTURE_BERU);
    const ticket = document.getElementById("sales-receipt")!;
    expect(ticket).toHaveTextContent("Total brut106 800 F");
    expect(ticket).toHaveTextContent("Remise (0,0 %)−0 F");
    expect(ticket).toHaveTextContent("TOTAL NET106 800 F");
    expect(ticket).toHaveTextContent("Acompte payé0 F");
    expect(ticket).toHaveTextContent("Reste à payer106 800 F");
  });

  it("affiche par ligne le prix négocié et le montant net, et garde le total brut au prix catalogue", () => {
    // 2 riz au catalogue à 1 000 négociés à 800 pièce, plus 1 huile à 3 500 en promotion à 3 000
    const vente: Sale = { ...VENTE, montantTotal: 4600, montantHT: 4600, montantRecu: 5000, lignes: [
      { ...ligne(1, "Riz local", 2, 1000, 1600) },
      { ...ligne(2, "Huile 5 l", 1, 3500, 3000, 500) },
    ] };

    expect(totauxDuTicket(vente)).toMatchObject({ brut: 5500, remise: 900, pourcentage: 16.36 });
    expect(totauxDuTicket(vente).lignes.map((ligne) => [ligne.prix, ligne.montant])).toEqual([[800, 1600], [3000, 3000]]);
    afficher(ENTREPRISE, vente);
    const ticket = document.getElementById("sales-receipt")!;
    const [riz, huile] = screen.getAllByRole("row").slice(1);
    expect(riz).toHaveTextContent("Riz local2,0800 F1 600 F");
    expect(huile).toHaveTextContent("Huile 5 l1,03 000 F3 000 F");
    expect(ticket).toHaveTextContent("Total brut5 500 F");
    expect(ticket).toHaveTextContent("Remise (16,4 %)−900 F");
    expect(ticket).toHaveTextContent("TOTAL NET4 600 F");
    expect(ticket).toHaveTextContent("Reçu (Espèces)5 000 F");
    expect(ticket).toHaveTextContent("Monnaie rendue400 F");
  });

  it("rejoue le reçu validé : une ligne négociée sur le total affiche le prix qui en découle", () => {
    // Vodka 0,125 l à 12 000 ; Sangria 0,065 l à 14 000 négociée à 900 F au total au lieu de 910 F
    const vente: Sale = { ...VENTE, montantTotal: 2400, montantHT: 2400, montantRecu: 2400, lignes: [
      ligne(1, "Vodka grand", 0.125, 12000, 1500),
      ligne(2, "Sangria Forte", 0.065, 14000, 900),
    ] };
    afficher(ENTREPRISE, vente);
    const ticket = document.getElementById("sales-receipt")!;
    const [vodka, sangria] = screen.getAllByRole("row").slice(1);

    expect(vodka).toHaveTextContent("Vodka grand0,12512 000 F1 500 F");
    expect(sangria).toHaveTextContent("Sangria Forte0,06513 846,15 F900 F");
    expect(ticket).toHaveTextContent("Total brut2 410 F");
    expect(ticket).toHaveTextContent("Remise (0,4 %)−10 F");
    expect(ticket).toHaveTextContent("TOTAL NET2 400 F");
  });

  it("ne montre ni acompte ni reste à payer pour une vente au comptant", () => {
    afficher(ENTREPRISE);
    const ticket = document.getElementById("sales-receipt")!;
    expect(ticket).not.toHaveTextContent("Acompte");
    expect(ticket).not.toHaveTextContent("Reste à payer");
    expect(ticket).toHaveTextContent("Monnaie rendue0 F");
  });

  it("indique la part HT et la TVA quand la vente est taxée", () => {
    afficher(ENTREPRISE, { ...VENTE, tauxTva: 18, montantHT: 1694.92, montantTva: 305.08 });
    const ticket = document.getElementById("sales-receipt")!;
    expect(ticket).toHaveTextContent("dont HT1 694,92 F");
    expect(ticket).toHaveTextContent("dont TVA (18 %)305,08 F");
  });

  it("garde les mentions DUPLICATA et VENTE ANNULÉE", () => {
    afficher(ENTREPRISE, { ...VENTE, statut: "ANNULEE" }, true);
    const ticket = document.getElementById("sales-receipt")!;
    expect(ticket).toHaveTextContent("DUPLICATA");
    expect(ticket).toHaveTextContent("VENTE ANNULÉE");
  });

  it("fait tomber Prix × Qté sur le Montant de chaque ligne, remisée ou non", () => {
    const vente: Sale = { ...VENTE, montantTotal: 16700, montantHT: 16700, montantRecu: 16700, lignes: [
      ligne(1, "Malta", 1, 8000, 7500), ligne(2, "Fanta", 0.5, 6400, 3200), ligne(3, "Sangria Forte", 0.065, 14000, 900),
      ligne(4, "Huile 5 l", 3, 3500, 9000, 500),
    ] };

    for (const ligneDuTicket of totauxDuTicket(vente).lignes) {
      expect(Math.abs(ligneDuTicket.prix * ligneDuTicket.quantite - ligneDuTicket.montant)).toBeLessThan(0.01);
    }
  });

  it("dessine un tableau fermé : un cadre autour et des traits entre toutes les cellules", () => {
    afficher(ENTREPRISE, FACTURE_BERU);
    expect(screen.getByRole("table")).toHaveClass("border");
    for (const cellule of screen.getAllByRole("cell")) expect(cellule).toHaveClass("border");
    for (const entete of screen.getAllByRole("columnheader")) expect(entete).toHaveClass("border");
  });
});
