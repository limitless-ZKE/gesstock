import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../services/api";
import { erreurHttp } from "../test/axios";
import { telecharger } from "../lib/telechargement";
import ApprovisionnementsPage from "./ApprovisionnementsPage";

vi.mock("../services/api", () => ({ api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }));
vi.mock("../lib/telechargement", () => ({ telecharger: vi.fn() }));

const ARTICLES = [
  { id: 1, reference: "BOI-001", designation: "Bière Flag 65 cl", prixAchat: 6000, prixVente: 7500, quantiteStock: 12.5, uniteMesure: "casier" },
  { id: 2, reference: "BOI-002", designation: "Eau minérale 1,5 L", prixAchat: 2500, prixVente: 3000, quantiteStock: 40, uniteMesure: "pack" },
  { id: 3, reference: "BOI-003", designation: "Jus de bissap", prixAchat: 900, prixVente: 1200, quantiteStock: 0, uniteMesure: null },
];
const FOURNISSEURS = [{ id: 9, nom: "Brasseries du Sud", telephone: null, email: null, adresse: null }];
const BON_A_RECEVOIR = {
  id: 51, numero: "APP-51", dateApprovisionnement: "2026-11-02", statut: "BROUILLON", fournisseurId: 9, fournisseur: "Brasseries du Sud",
  montantTotal: 30000,
  lignes: [{ id: 511, articleId: 2, referenceArticle: "BOI-002", designationArticle: "Eau minérale 1,5 L", quantite: 12, prixUnitaire: 2500,
    montantLigne: 30000, prixVente: null }],
};
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
    if (url === "/approvisionnements") return { data: [BON_RECU, BON_A_RECEVOIR] };
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

  it("la réception propose de payer en espèces de la caisse, case cochée par défaut", async () => {
    const utilisateur = userEvent.setup();
    render(<ApprovisionnementsPage />);
    await screen.findByText("APP-51");
    await utilisateur.click(screen.getByRole("button", { name: "Valider réception" }));

    const fenetre = screen.getByRole("dialog", { name: "Réceptionner le bon APP-51" });
    expect(within(fenetre).getByRole("checkbox", { name: /Payer en espèces de la caisse/ })).toBeChecked();
    expect(fenetre).toHaveTextContent("plus de dette envers le fournisseur");
    await utilisateur.click(within(fenetre).getByRole("button", { name: "Confirmer la réception" }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/approvisionnements/51/validation", { payerEnEspeces: true }));
    expect(await screen.findByText(/réceptionné et payé en espèces de la caisse/)).toBeInTheDocument();
  });

  it("décochée, la réception garde la dette du fournisseur", async () => {
    const utilisateur = userEvent.setup();
    render(<ApprovisionnementsPage />);
    await screen.findByText("APP-51");
    await utilisateur.click(screen.getByRole("button", { name: "Valider réception" }));
    const fenetre = screen.getByRole("dialog", { name: "Réceptionner le bon APP-51" });

    await utilisateur.click(within(fenetre).getByRole("checkbox", { name: /Payer en espèces de la caisse/ }));
    expect(fenetre).toHaveTextContent("une dette envers le fournisseur est créée");
    await utilisateur.click(within(fenetre).getByRole("button", { name: "Confirmer la réception" }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/approvisionnements/51/validation", { payerEnEspeces: false }));
    expect(await screen.findByText(/une dette envers le fournisseur reste à régler/)).toBeInTheDocument();
  });

  it("si la caisse ne peut pas payer, le message du serveur reste dans la fenêtre et rien n'est enregistré", async () => {
    vi.mocked(api.post).mockRejectedValue(erreurHttp(409, { detail: "Paiement en espèces impossible : La sortie dépasse le solde théorique disponible de 1000. Ajoutez une entrée de fonds (responsable) ou décochez « Payer en espèces de la caisse ».", code: "CONFLIT" }));
    const utilisateur = userEvent.setup();
    render(<ApprovisionnementsPage />);
    await screen.findByText("APP-51");
    await utilisateur.click(screen.getByRole("button", { name: "Valider réception" }));
    const fenetre = screen.getByRole("dialog", { name: "Réceptionner le bon APP-51" });

    await utilisateur.click(within(fenetre).getByRole("button", { name: "Confirmer la réception" }));

    expect(await within(fenetre).findByRole("alert")).toHaveTextContent("Paiement en espèces impossible");
    expect(screen.getByRole("dialog", { name: "Réceptionner le bon APP-51" })).toBeInTheDocument();
  });

  it("imprime un bon en PDF, qu'il soit déjà réceptionné ou encore à réceptionner", async () => {
    const pdf = new Blob(["%PDF-1.4"], { type: "application/pdf" });
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/approvisionnements") return { data: [BON_RECU, BON_A_RECEVOIR] };
      if (url === "/fournisseurs") return { data: FOURNISSEURS };
      if (url === "/articles") return { data: ARTICLES };
      if (url.endsWith("/export/pdf")) return { data: pdf };
      throw new Error(`appel inattendu : ${url}`);
    });
    const utilisateur = userEvent.setup();
    render(<ApprovisionnementsPage />);

    await utilisateur.click(await screen.findByRole("button", { name: "Imprimer le bon APP-50" }));
    await waitFor(() => expect(telecharger).toHaveBeenCalledWith(pdf, "gesstock-bon-APP-50.pdf"));
    expect(api.get).toHaveBeenCalledWith("/approvisionnements/50/export/pdf", { responseType: "blob" });
    expect(await screen.findByText(/Le bon APP-50 est prêt en PDF/)).toBeInTheDocument();

    await utilisateur.click(screen.getByRole("button", { name: "Imprimer le bon APP-51" }));
    await waitFor(() => expect(telecharger).toHaveBeenCalledWith(pdf, "gesstock-bon-APP-51.pdf"));
  });

  it("affiche le message du serveur quand l'impression du bon échoue", async () => {
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/approvisionnements") return { data: [BON_RECU] };
      if (url === "/fournisseurs") return { data: FOURNISSEURS };
      if (url === "/articles") return { data: ARTICLES };
      throw erreurHttp(404, { message: "Approvisionnement introuvable: 50" });
    });
    const utilisateur = userEvent.setup();
    render(<ApprovisionnementsPage />);

    await utilisateur.click(await screen.findByRole("button", { name: "Imprimer le bon APP-50" }));

    expect(await screen.findByText(/L’impression du bon APP-50 a échoué|Approvisionnement introuvable/)).toBeInTheDocument();
    expect(telecharger).not.toHaveBeenCalled();
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

describe("classer les bons par période de date", () => {
  const BON_ANCIEN = { ...BON_RECU, id: 40, numero: "APP-40", dateApprovisionnement: "2026-09-15", montantTotal: 5000, lignes: [] };

  beforeEach(() => {
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/approvisionnements") return { data: [BON_RECU, BON_A_RECEVOIR, BON_ANCIEN] };
      if (url === "/fournisseurs") return { data: FOURNISSEURS };
      if (url === "/articles") return { data: ARTICLES };
      throw new Error(`appel inattendu : ${url}`);
    });
  });

  const ordreDesBons = () => screen.getAllByText(/^APP-\d+$/).map((cellule) => cellule.textContent);

  async function afficher() {
    const utilisateur = userEvent.setup();
    render(<ApprovisionnementsPage />);
    await screen.findByText("APP-50");
    return utilisateur;
  }

  it("classe du plus récent au plus ancien, ou l'inverse", async () => {
    const utilisateur = await afficher();
    expect(ordreDesBons()).toEqual(["APP-51", "APP-50", "APP-40"]);

    await utilisateur.selectOptions(screen.getByLabelText("Classer les bons"), "ancien");

    expect(ordreDesBons()).toEqual(["APP-40", "APP-50", "APP-51"]);
  });

  it("ne garde que les bons de la période choisie à la main, avec leur total", async () => {
    const utilisateur = await afficher();
    expect(screen.queryByLabelText("Du")).not.toBeInTheDocument();

    await utilisateur.selectOptions(screen.getByLabelText("Période des bons"), "custom");
    await utilisateur.type(screen.getByLabelText("Du"), "2026-11-02");

    expect(ordreDesBons()).toEqual(["APP-51"]);
    expect(screen.getByText(/1 bon\(s\) · total/)).toBeInTheDocument();
    expect(screen.getByText("À partir du 02/11/2026")).toBeInTheDocument();

    await utilisateur.type(screen.getByLabelText("Au"), "2026-11-02");
    expect(ordreDesBons()).toEqual(["APP-51"]);
    await utilisateur.clear(screen.getByLabelText("Du"));
    await utilisateur.type(screen.getByLabelText("Du"), "2026-09-01");
    expect(ordreDesBons()).toEqual(["APP-51", "APP-50", "APP-40"]);   // du 1er septembre au 2 novembre : les trois bons
    expect(screen.getByText("Du 01/09/2026 au 02/11/2026")).toBeInTheDocument();
  });

  it("propose ce mois et le mois dernier selon la date du jour", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 10, 5, 10, 0, 0));   // le 5 novembre 2026
    try {
      const utilisateur = await afficher();

      await utilisateur.selectOptions(screen.getByLabelText("Période des bons"), "month");
      expect(ordreDesBons()).toEqual(["APP-51", "APP-50"]);
      expect(screen.getByText("Du 01/11/2026 au 30/11/2026")).toBeInTheDocument();

      await utilisateur.selectOptions(screen.getByLabelText("Période des bons"), "lastMonth");
      expect(screen.getByText("Aucun bon ne correspond à ces filtres.")).toBeInTheDocument();

      await utilisateur.selectOptions(screen.getByLabelText("Période des bons"), "all");
      expect(ordreDesBons()).toEqual(["APP-51", "APP-50", "APP-40"]);
    } finally {
      vi.useRealTimers();
    }
  });
});
