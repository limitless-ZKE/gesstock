import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { telecharger } from "../lib/telechargement";
import { api } from "../services/api";
import { erreurHttp } from "../test/axios";
import ImportArticlesModal, { type ApercuImport } from "./ImportArticlesModal";

vi.mock("../services/api", () => ({ api: { get: vi.fn(), post: vi.fn() } }));
vi.mock("../lib/telechargement", () => ({ telecharger: vi.fn() }));

const APERCU_VALIDE: ApercuImport = {
  nombreDeLignes: 3, nouveaux: 1, modifies: 1, identiques: 1, erreurs: 0, avertissements: 1,
  categoriesACreer: ["Boissons"], colonnesIgnorees: ["Couleur"], importable: true,
  lignes: [
    { numero: 2, reference: "NEW-001", designation: "Jus de bissap", statut: "NOUVEAU", changements: ["stock de départ 12.5"], avertissements: ["La catégorie « Boissons » n'existe pas : elle sera créée."], erreurs: [] },
    { numero: 3, reference: "ALIM-001", designation: "Riz local", statut: "MODIFIE", changements: ["prix d'achat 900 → 950"], avertissements: [], erreurs: [] },
    { numero: 4, reference: "ALIM-002", designation: "Huile végétale", statut: "IDENTIQUE", changements: [], avertissements: [], erreurs: [] },
  ],
};

const APERCU_EN_ERREUR: ApercuImport = {
  ...APERCU_VALIDE, nouveaux: 0, modifies: 0, identiques: 0, erreurs: 1, avertissements: 0, categoriesACreer: [], colonnesIgnorees: [], importable: false,
  lignes: [{ numero: 5, reference: "ERR-001", designation: "", statut: "ERREUR", changements: [], avertissements: [], erreurs: ["La désignation est vide.", "« Prix d'achat » : « abc » n'est pas un nombre."] }],
};

const fichier = new File(["Référence;Désignation\nNEW-001;Jus"], "articles.csv", { type: "text/csv" });

beforeEach(() => {
  vi.mocked(api.get).mockReset().mockResolvedValue({ data: new Blob(["x"]) });
  vi.mocked(api.post).mockReset();
  vi.mocked(telecharger).mockReset().mockResolvedValue(undefined);
});

async function choisirLeFichier(apercu: ApercuImport) {
  vi.mocked(api.post).mockResolvedValueOnce({ data: apercu });
  const onImported = vi.fn();
  const onClose = vi.fn();
  const utilisateur = userEvent.setup();
  render(<ImportArticlesModal onClose={onClose} onImported={onImported} />);
  await utilisateur.upload(screen.getByLabelText("Fichier d’articles"), fichier);
  return { utilisateur, onImported, onClose };
}

describe("import d'articles", () => {
  it("montre l'aperçu ligne par ligne avant de rien enregistrer", async () => {
    await choisirLeFichier(APERCU_VALIDE);

    expect(await screen.findByText(/3 ligne\(s\) lue\(s\)/)).toBeInTheDocument();
    const [, formData] = vi.mocked(api.post).mock.calls[0];
    expect(vi.mocked(api.post).mock.calls[0][0]).toBe("/articles/import/apercu");
    expect((formData as FormData).get("fichier")).toBe(fichier);
    expect(screen.getByText("Nouveaux").previousElementSibling).toHaveTextContent("1");
    expect(screen.getByText("Catégories qui seront créées :").parentElement).toHaveTextContent("Boissons");
    expect(screen.getByText(/Colonnes non reconnues, ignorées : Couleur/)).toBeInTheDocument();
    const tableau = screen.getByRole("table");
    expect(within(tableau).getByText("Jus de bissap")).toBeInTheDocument();
    expect(within(tableau).getByText("prix d'achat 900 → 950")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Importer 2 article(s)" })).toBeEnabled();
    expect(api.post).toHaveBeenCalledTimes(1);   // l'aperçu n'importe rien
  });

  it("confirme l'import, prévient l'écran et dit ce qui a été fait", async () => {
    const { utilisateur, onImported } = await choisirLeFichier(APERCU_VALIDE);
    await screen.findByRole("button", { name: "Importer 2 article(s)" });
    vi.mocked(api.post).mockResolvedValueOnce({ data: { crees: 1, modifies: 1, identiques: 1, categoriesCreees: 1 } });

    await utilisateur.click(screen.getByRole("button", { name: "Importer 2 article(s)" }));

    await waitFor(() => expect(onImported).toHaveBeenCalledWith("Import terminé : 1 article(s) créé(s), 1 modifié(s), 1 catégorie(s) créée(s)."));
    expect(vi.mocked(api.post).mock.calls[1][0]).toBe("/articles/import");
  });

  it("bloque l'import tant qu'une ligne est en erreur et explique quoi corriger", async () => {
    await choisirLeFichier(APERCU_EN_ERREUR);

    expect(await screen.findByText("La désignation est vide.")).toBeInTheDocument();
    expect(screen.getByText("« Prix d'achat » : « abc » n'est pas un nombre.")).toBeInTheDocument();
    expect(screen.getByText(/tant qu’il y a une erreur, rien ne peut être importé/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Importer" })).toBeDisabled();
  });

  it("affiche le message du serveur quand le fichier est illisible", async () => {
    vi.mocked(api.post).mockRejectedValueOnce(erreurHttp(400, { detail: "Il manque la colonne « Désignation » dans la première ligne du fichier." }));
    const utilisateur = userEvent.setup();
    render(<ImportArticlesModal onClose={vi.fn()} onImported={vi.fn()} />);

    await utilisateur.upload(screen.getByLabelText("Fichier d’articles"), fichier);

    expect(await screen.findByRole("alert")).toHaveTextContent("Il manque la colonne « Désignation »");
    expect(screen.getByRole("button", { name: "Importer" })).toBeDisabled();
  });

  it("télécharge le modèle vide ou le catalogue actuel", async () => {
    const utilisateur = userEvent.setup();
    render(<ImportArticlesModal onClose={vi.fn()} onImported={vi.fn()} />);

    await utilisateur.click(screen.getByRole("button", { name: /Télécharger le modèle vide/ }));
    await waitFor(() => expect(telecharger).toHaveBeenCalledWith(expect.any(Blob), "gesstock-modele-import-articles.xlsx"));
    expect(api.get).toHaveBeenCalledWith("/articles/import/modele", { params: { avecArticles: false }, responseType: "blob" });

    await utilisateur.click(screen.getByRole("button", { name: /Télécharger mon catalogue/ }));
    await waitFor(() => expect(telecharger).toHaveBeenCalledWith(expect.any(Blob), "gesstock-articles-modifiables.xlsx"));
    expect(api.get).toHaveBeenLastCalledWith("/articles/import/modele", { params: { avecArticles: true }, responseType: "blob" });
  });
});
