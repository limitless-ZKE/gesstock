import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../services/api";
import ApprovisionnementsPage from "./ApprovisionnementsPage";

vi.mock("../services/api", () => ({ api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }));

const ARTICLES = [
  { id: 1, reference: "BOI-001", designation: "Bière Flag 65 cl", prixAchat: 6000, prixVente: 7500, quantiteStock: 12.5, uniteMesure: "casier" },
  { id: 2, reference: "BOI-002", designation: "Eau minérale 1,5 L", prixAchat: 2500, prixVente: 3000, quantiteStock: 40, uniteMesure: "pack" },
  { id: 3, reference: "BOI-003", designation: "Jus de bissap", prixAchat: 900, prixVente: 1200, quantiteStock: 0, uniteMesure: null },
];
const FOURNISSEURS = [{ id: 9, nom: "Brasseries du Sud", telephone: null, email: null, adresse: null }];
const BON_RECU = {
  id: 50, numero: "APP-50", dateApprovisionnement: "2026-11-01", statut: "RECU", fournisseurId: 9, fournisseur: "Brasseries du Sud",
  montantTotal: 60000,
  lignes: [{ id: 501, articleId: 1, referenceArticle: "BOI-001", designationArticle: "Bière Flag 65 cl", quantite: 10, prixUnitaire: 6000,
    montantLigne: 60000, prixVente: null }],
};

beforeEach(() => {
  vi.mocked(api.get).mockReset();
  vi.mocked(api.post).mockReset();
  vi.mocked(api.put).mockReset();
  vi.mocked(api.get).mockImplementation(async (url: string) => {
    if (url === "/approvisionnements") return { data: [BON_RECU] };
    if (url === "/fournisseurs") return { data: FOURNISSEURS };
    if (url === "/articles") return { data: ARTICLES };
    throw new Error(`appel inattendu : ${url}`);
  });
  vi.mocked(api.post).mockResolvedValue({ data: {} });
  vi.mocked(api.put).mockResolvedValue({ data: {} });
});

afterEach(() => {
  vi.restoreAllMocks();
});

async function ouvrirNouveauBon() {
  const utilisateur = userEvent.setup();
  render(<ApprovisionnementsPage />);
  await screen.findByText("APP-50");
  await utilisateur.click(screen.getByRole("button", { name: /Nouveau bon/ }));
  return utilisateur;
}

describe("bon d'approvisionnement", () => {
  it("cherche l'article d'une ligne en tapant une partie de son nom, sans tenir compte des accents", async () => {
    const utilisateur = await ouvrirNouveauBon();

    await utilisateur.type(screen.getByRole("combobox", { name: "Article de la ligne 1" }), "biere");

    const options = within(screen.getByRole("listbox", { name: "Articles" })).getAllByRole("option");
    expect(options).toHaveLength(1);
    expect(options[0]).toHaveTextContent("Bière Flag 65 cl");
    expect(options[0]).toHaveTextContent("stock 12,5 casier");
  });

  it("pré-remplit le prix d'achat et le prix de revente avec les derniers prix connus", async () => {
    const utilisateur = await ouvrirNouveauBon();
    await utilisateur.type(screen.getByRole("combobox", { name: "Article de la ligne 1" }), "eau");
    await utilisateur.click(screen.getByRole("option", { name: /Eau minérale/ }));

    expect(screen.getByLabelText("Prix d’achat de la ligne 1")).toHaveValue(2500);
    expect(screen.getByLabelText("Prix de revente de la ligne 1")).toHaveValue(3000);
  });

  it("n'envoie le prix de revente que s'il a été changé, et accepte une quantité à virgule", async () => {
    const utilisateur = await ouvrirNouveauBon();
    await utilisateur.selectOptions(screen.getByDisplayValue("Sélectionner un fournisseur"), "9");
    await utilisateur.type(screen.getByRole("combobox", { name: "Article de la ligne 1" }), "flag");
    await utilisateur.click(screen.getByRole("option", { name: /Bière Flag/ }));
    const quantite = screen.getByRole("spinbutton", { name: "Quantité" });
    await utilisateur.clear(quantite);
    await utilisateur.type(quantite, "2.5");
    const prixRevente = screen.getByLabelText("Prix de revente de la ligne 1");
    await utilisateur.clear(prixRevente);
    await utilisateur.type(prixRevente, "8000");
    expect(screen.getByText(/Actuel : .* \(changera\)/)).toBeInTheDocument();

    await utilisateur.click(screen.getByRole("button", { name: "Créer le brouillon" }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/approvisionnements", expect.objectContaining({
      fournisseurId: 9,
      lignes: [{ articleId: 1, quantite: 2.5, prixUnitaire: 6000, prixVente: 8000 }],
    })));
  });

  it("corrige un bon déjà réceptionné après confirmation", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const utilisateur = userEvent.setup();
    render(<ApprovisionnementsPage />);
    await utilisateur.click(await screen.findByRole("button", { name: "Modifier le bon APP-50" }));

    const fenetre = screen.getByRole("dialog", { name: "Modifier le bon APP-50" });
    expect(fenetre).toHaveTextContent("Bon déjà réceptionné");
    expect(screen.getByRole("combobox", { name: "Article de la ligne 1" })).toHaveValue("Bière Flag 65 cl (BOI-001)");
    const quantite = screen.getByRole("spinbutton", { name: "Quantité" });
    await utilisateur.clear(quantite);
    await utilisateur.type(quantite, "8");

    await utilisateur.click(screen.getByRole("button", { name: "Enregistrer la correction" }));

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("déjà réceptionné"));
    await waitFor(() => expect(api.put).toHaveBeenCalledWith("/approvisionnements/50", expect.objectContaining({
      numero: "APP-50",
      lignes: [{ articleId: 1, quantite: 8, prixUnitaire: 6000, prixVente: null }],
    })));
    expect(await screen.findByText(/a été corrigé/)).toBeInTheDocument();
  });
});
