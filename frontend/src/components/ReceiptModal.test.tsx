import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useEntreprise, type Entreprise } from "../entreprise/EntrepriseContext";
import { imprimer } from "../lib/impression";
import ReceiptModal, { type Sale } from "./ReceiptModal";

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

function afficher(entreprise: Entreprise | null) {
  vi.mocked(useEntreprise).mockReturnValue({ entreprise, rafraichir: vi.fn(), logoUrl: vi.fn() });
  render(<ReceiptModal sale={VENTE} onClose={vi.fn()} />);
}

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
