import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { telecharger } from "../lib/telechargement";
import { api } from "../services/api";
import ArticlesPage from "./ArticlesPage";

vi.mock("../services/api", () => ({ api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }));
vi.mock("../lib/telechargement", () => ({ telecharger: vi.fn() }));

const article = (id: number, designation: string, stock: number, categorieId: number | null, categorie: string | null, seuil = 5) => ({
  id, reference: `REF-${id}`, designation, prixAchat: 600, coutMoyen: 600, prixVente: 1000, quantiteStock: stock, seuilAlerteStock: seuil,
  categorieId, categorie, uniteMesureId: null, uniteMesure: "casier",
});
const ARTICLES = [
  article(1, "Bière Flag", 12.5, 1, "Bières"),
  article(2, "Bière Castel", 3, 1, "Bières"),
  article(3, "Jus de bissap", 0, 2, "Jus"),
  article(4, "Eau minérale", 40, 2, "Jus"),
];
const CATEGORIES = [
  { id: 1, nom: "Bières", description: null, active: true, parentId: null },
  { id: 2, nom: "Jus", description: null, active: true, parentId: null },
];

beforeEach(() => {
  vi.mocked(api.get).mockReset().mockImplementation(async (url: string) => {
    if (url === "/articles") return { data: ARTICLES };
    if (url === "/articles/categories") return { data: CATEGORIES };
    if (url === "/referentiels/unites-mesure") return { data: [] };
    if (url.startsWith("/articles/export/")) return { data: new Blob(["x"]) };
    throw new Error(`appel inattendu : ${url}`);
  });
  vi.mocked(telecharger).mockReset().mockResolvedValue(undefined);
});

async function afficher() {
  const utilisateur = userEvent.setup();
  render(<ArticlesPage />);
  await screen.findByText("Bière Flag");
  return utilisateur;
}

describe("coût et marge", () => {
  const AVEC_COUT_MANQUANT = [article(1, "Bière Flag", 12.5, 1, "Bières"), { ...article(5, "Sirop maison", 8, 2, "Jus"), prixAchat: 0, coutMoyen: 0 }];

  beforeEach(() => {
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/articles") return { data: AVEC_COUT_MANQUANT };
      if (url === "/articles/categories") return { data: CATEGORIES };
      if (url === "/referentiels/unites-mesure") return { data: [] };
      throw new Error(`appel inattendu : ${url}`);
    });
  });

  it("montre la marge et son taux, et signale un coût à 0 au lieu d'une marge fausse", async () => {
    await afficher();

    const ligneBiere = screen.getByText("Bière Flag").closest("tr")!;
    expect(ligneBiere).toHaveTextContent(/400/);       // 1 000 − 600
    expect(ligneBiere).toHaveTextContent("40 %");     // sur le prix de vente
    const ligneSirop = screen.getByText("Sirop maison").closest("tr")!;
    expect(within(ligneSirop).getByText("Coût à vérifier")).toBeInTheDocument();
    expect(ligneSirop).not.toHaveTextContent("100 %");
    expect(screen.getByText("Coût à vérifier", { selector: "p" }).parentElement).toHaveTextContent("1");
  });

  it("le filtre « Coût à vérifier » ne garde que les articles dont le prix d'achat n'a jamais été saisi", async () => {
    const utilisateur = await afficher();

    await utilisateur.selectOptions(screen.getByLabelText("Coût d’achat"), "missing");

    expect(screen.getByText("Sirop maison")).toBeInTheDocument();
    expect(screen.queryByText("Bière Flag")).not.toBeInTheDocument();
  });

  it("ouvre la fenêtre d'import des articles", async () => {
    const utilisateur = await afficher();

    await utilisateur.click(screen.getByRole("button", { name: /Importer/ }));

    expect(screen.getByRole("dialog", { name: "Importer des articles" })).toBeInTheDocument();
  });
});

describe("stock disponible", () => {
  it("compte les articles disponibles à côté du stock bas et des ruptures", async () => {
    await afficher();

    expect(screen.getByText("Disponibles", { selector: "p" }).parentElement).toHaveTextContent("3");
    expect(screen.getByText("Stock bas", { selector: "p" }).parentElement).toHaveTextContent("1");
    expect(screen.getByText("En rupture", { selector: "p" }).parentElement).toHaveTextContent("1");
  });

  it("le filtre « Stock disponible » masque les ruptures", async () => {
    const utilisateur = await afficher();

    await utilisateur.selectOptions(screen.getByDisplayValue("Tous les niveaux"), "available");

    expect(screen.getByText("Bière Flag")).toBeInTheDocument();
    expect(screen.getByText("Eau minérale")).toBeInTheDocument();
    expect(screen.queryByText("Jus de bissap")).not.toBeInTheDocument();
  });
});

describe("liste des articles à imprimer ou exporter", () => {
  it("exporte en PDF le stock disponible, avec le stock, selon le filtre choisi", async () => {
    const utilisateur = await afficher();
    await utilisateur.selectOptions(screen.getByDisplayValue("Tous les niveaux"), "available");

    await utilisateur.click(screen.getByRole("button", { name: "PDF (imprimer)" }));

    await waitFor(() => expect(api.get).toHaveBeenCalledWith("/articles/export/pdf", { params: { stock: "disponible", avecStock: true }, responseType: "blob" }));
    expect(telecharger).toHaveBeenCalledWith(expect.any(Blob), "gesstock-articles-disponible.pdf");
  });

  it("décocher « Avec le stock » donne la liste simple, et la catégorie choisie est transmise", async () => {
    const utilisateur = await afficher();
    await utilisateur.selectOptions(screen.getByDisplayValue("Toutes les catégories"), "2");
    await utilisateur.click(screen.getByRole("checkbox", { name: "Avec le stock" }));

    await utilisateur.click(screen.getByRole("button", { name: "Excel" }));

    await waitFor(() => expect(api.get).toHaveBeenCalledWith("/articles/export/excel", { params: { stock: "tous", avecStock: false, categorieId: "2" }, responseType: "blob" }));
    expect(telecharger).toHaveBeenCalledWith(expect.any(Blob), "gesstock-articles-tous-simple.xlsx");
  });

  it("annonce le choix en clair au-dessus des boutons", async () => {
    const utilisateur = await afficher();
    await utilisateur.selectOptions(screen.getByDisplayValue("Tous les niveaux"), "low");

    expect(screen.getByText(/classée par catégorie, selon les filtres/)).toHaveTextContent("stock bas");
  });

  it("un export refusé par le serveur affiche le message sans rien télécharger", async () => {
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/articles") return { data: ARTICLES };
      if (url === "/articles/categories") return { data: CATEGORIES };
      if (url === "/referentiels/unites-mesure") return { data: [] };
      throw new Error("réseau coupé");
    });
    const utilisateur = await afficher();

    await utilisateur.click(screen.getByRole("button", { name: "PDF (imprimer)" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/L’export PDF a échoué|réseau/);
    expect(telecharger).not.toHaveBeenCalled();
    expect(within(document.body).queryByText("Préparation…")).not.toBeInTheDocument();
  });
});
