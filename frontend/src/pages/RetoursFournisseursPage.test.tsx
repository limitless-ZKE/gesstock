import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../auth/AuthContext";
import { api } from "../services/api";
import { erreurHttp } from "../test/axios";
import RetoursFournisseursPage from "./RetoursFournisseursPage";

vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));
vi.mock("../services/api", () => ({ api: { get: vi.fn(), post: vi.fn() } }));

const normaliser = (texte: string) => texte.replace(/[\u00a0\u202f]/g, " ");

const RETOURS = [
  {
    id: 1, numero: "RF-20261008-001", statut: "BROUILLON", fournisseur: "Grossiste Dakar", numeroBon: "BR-5",
    motif: "Écrans défectueux", dateCreation: "2026-10-08T09:00:00", creePar: "gestionnaire", dateValidation: null, validePar: null,
    montantTotal: 8000,
    lignes: [{ ligneApprovisionnementId: 21, articleId: 2, reference: "ELEC-001", designation: "Téléphone", quantite: 4, coutUnitaire: 2000, montant: 8000 }],
  },
  {
    id: 2, numero: "RF-20261007-001", statut: "VALIDE", fournisseur: "Grossiste Dakar", numeroBon: "BR-4",
    motif: "Erreur de livraison", dateCreation: "2026-10-07T09:00:00", creePar: "gestionnaire", dateValidation: "2026-10-07T11:00:00", validePar: "manager",
    montantTotal: 3000, lignes: [],
  },
];
const BONS = [
  { id: 5, numero: "BR-5", fournisseur: "Grossiste Dakar", statut: "RECU", dateApprovisionnement: "2026-10-01" },
  { id: 6, numero: "BR-6", fournisseur: "Grossiste Dakar", statut: "BROUILLON", dateApprovisionnement: "2026-10-08" },
];
const LIGNES_RETOURNABLES = [{
  ligneApprovisionnementId: 21, articleId: 2, reference: "ELEC-001", designation: "Téléphone",
  quantiteRecue: 10, quantiteDejaRetournee: 4, quantiteRetournable: 6, coutUnitaire: 2000, stockActuel: 5,
}];

function afficher(roles: string[]) {
  vi.mocked(useAuth).mockReturnValue({ user: { username: "u", roles }, loading: false, login: vi.fn(), logout: vi.fn() });
  render(<RetoursFournisseursPage />);
  return userEvent.setup();
}

describe("RetoursFournisseursPage", () => {
  beforeEach(() => {
    vi.mocked(api.get).mockReset().mockImplementation(async (url: string) => {
      if (url === "/retours-fournisseurs") return { data: RETOURS };
      if (url === "/approvisionnements") return { data: BONS };
      if (url === "/retours-fournisseurs/bons/5/lignes") return { data: LIGNES_RETOURNABLES };
      throw new Error(`GET inattendu : ${url}`);
    });
    vi.mocked(api.post).mockReset().mockResolvedValue({ data: { numero: "RF-20261008-002" } });
    vi.spyOn(window, "confirm").mockReturnValue(true);
  });

  it("liste les retours avec leur statut et l'avoir en jeu", async () => {
    afficher(["GESTIONNAIRE"]);
    expect(await screen.findByRole("button", { name: /RF-20261008-001/ })).toBeInTheDocument();
    expect(screen.getByText("À valider")).toBeInTheDocument();
    expect(screen.getByText("Validé")).toBeInTheDocument();
    expect(screen.getByText(normaliser("8 000 F CFA"), { exact: false, normalizer: normaliser })).toBeInTheDocument();
  });

  it("réserve la validation aux responsables : un gestionnaire ne peut qu'annuler son brouillon", async () => {
    afficher(["GESTIONNAIRE"]);
    await screen.findByRole("button", { name: /RF-20261008-001/ });
    expect(screen.queryByRole("button", { name: /Valider/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Annuler/ })).toBeInTheDocument();
  });

  it("un responsable valide après confirmation, puis la liste est rechargée", async () => {
    const utilisateur = afficher(["MANAGER"]);
    await utilisateur.click(await screen.findByRole("button", { name: /Valider/ }));

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("Cette opération ne s’annule pas"));
    expect(api.post).toHaveBeenCalledWith("/retours-fournisseurs/1/validation");
    await waitFor(() => expect(vi.mocked(api.get).mock.calls.filter(([url]) => url === "/retours-fournisseurs")).toHaveLength(2));
    expect(await screen.findByRole("status")).toHaveTextContent("stock réduit, avoir crédité");
  });

  it("ne fait rien si le responsable renonce à la confirmation", async () => {
    vi.mocked(window.confirm).mockReturnValue(false);
    const utilisateur = afficher(["ADMIN"]);
    await utilisateur.click(await screen.findByRole("button", { name: /Valider/ }));
    expect(api.post).not.toHaveBeenCalled();
  });

  it("affiche le refus du serveur (stock vendu depuis)", async () => {
    vi.mocked(api.post).mockRejectedValue(erreurHttp(409, { detail: "Stock insuffisant pour rendre 4 × ELEC-001" }));
    const utilisateur = afficher(["MANAGER"]);
    await utilisateur.click(await screen.findByRole("button", { name: /Valider/ }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Stock insuffisant pour rendre 4 × ELEC-001");
  });

  it("déplie un retour pour voir son motif et ses lignes", async () => {
    const utilisateur = afficher(["MANAGER"]);
    await utilisateur.click(await screen.findByRole("button", { name: /RF-20261008-001/ }));
    expect(screen.getByText(/Écrans défectueux/)).toBeInTheDocument();
    expect(screen.getByText("Téléphone")).toBeInTheDocument();
  });

  it("prépare un retour : seuls les bons reçus sont proposés, les quantités sont bornées, le motif est exigé", async () => {
    const utilisateur = afficher(["GESTIONNAIRE"]);
    await utilisateur.click(await screen.findByRole("button", { name: /Nouveau retour/ }));

    const choixBon = await screen.findByLabelText(/Bon reçu à l.origine du retour/);
    expect(screen.queryByRole("option", { name: /BR-6/ })).not.toBeInTheDocument();   // un brouillon n'a rien reçu
    await utilisateur.selectOptions(choixBon, "5");

    const quantite = await screen.findByLabelText("Quantité rendue de Téléphone");
    expect(quantite).toHaveAttribute("max", "5");   // min(6 retournables, 5 en rayon)
    const preparer = screen.getByRole("button", { name: /Préparer le retour/ });
    expect(preparer).toBeDisabled();

    await utilisateur.type(quantite, "3");
    expect(screen.getByText(/Avoir attendu/)).toHaveTextContent(normaliser("6 000 F CFA"));
    await utilisateur.type(screen.getByLabelText("Motif du retour"), "Défaut de fabrication");
    expect(preparer).toBeEnabled();
    await utilisateur.click(preparer);

    expect(api.post).toHaveBeenCalledWith("/retours-fournisseurs", {
      approvisionnementId: 5,
      motif: "Défaut de fabrication",
      lignes: [{ ligneApprovisionnementId: 21, quantite: 3 }],
    });
    expect(await screen.findByRole("status")).toHaveTextContent("RF-20261008-002");
  });
});
