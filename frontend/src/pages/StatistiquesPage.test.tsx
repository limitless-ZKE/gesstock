import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../services/api";
import StatistiquesPage from "./StatistiquesPage";

vi.mock("../services/api", () => ({ api: { get: vi.fn() } }));
vi.mock("../components/EnTeteDocument", () => ({ default: () => null }));

const ESPACES = new RegExp(`[${String.fromCharCode(160)}${String.fromCharCode(8239)}]`, "g");
const normaliser = (texte: string | null) => (texte ?? "").replace(ESPACES, " ");

/** Rapport d'une période : 500 000 de ventes, 120 000 d'achats (2 bons), 15 000 de sorties de caisse saisies à la main. */
const RAPPORT = {
  debut: "2026-11-01",
  fin: "2026-11-04",
  chiffreAffaires: 500000,
  nombreVentes: 12,
  margeBrute: 140000,
  bilanDepenses: {
    achats: 120000,
    nombreBonsRecus: 2,
    autresDepenses: 15000,
    nombreDepenses: 1,
    totalDepenses: 135000,
    resultat: 365000,
    entreesDeFonds: 50000,
    bonsRecus: [
      { id: 7, numero: "APP-7", recuLe: "2026-11-02T08:30:00", fournisseur: "Brasseries du Sud", montant: 100000 },
      { id: 8, numero: "APP-8", recuLe: "2026-11-03T09:00:00", fournisseur: "Eaux du Nord", montant: 20000 },
    ],
    depenses: [
      { id: 3, date: "2026-11-03T14:00:00", motif: "Transport de marchandises", beneficiaire: "Moussa", effectuePar: "marie", montant: 15000 },
    ],
  },
  margesParCategorie: [],
  margesParArticle: [],
  sessionsCaisse: [],
  journalActivites: [],
};

beforeEach(() => {
  vi.mocked(api.get).mockReset().mockResolvedValue({ data: RAPPORT });
});

describe("statistiques : dépenses et résultat", () => {
  it("affiche le chiffre d'affaires tel quel, puis les achats, les autres dépenses, le résultat et les entrées de fonds", async () => {
    render(<StatistiquesPage />);

    const bilan = within(await screen.findByRole("region", { name: "Dépenses et résultat" }));
    expect(normaliser(screen.getByText("Chiffre d’affaires").parentElement?.textContent ?? "")).toContain("500 000");
    expect(normaliser(bilan.getByText("Achats").parentElement?.textContent ?? "")).toContain("120 000");
    expect(bilan.getByText("2 bon(s) d’approvisionnement réceptionné(s)")).toBeInTheDocument();
    expect(normaliser(bilan.getByText("Autres dépenses").parentElement?.textContent ?? "")).toContain("15 000");
    expect(normaliser(bilan.getByText("Résultat").parentElement?.textContent ?? "")).toContain("365 000");
    expect(normaliser(bilan.getByText("Entrées de fonds").parentElement?.textContent ?? "")).toContain("50 000");
    expect(bilan.getByText("Argent ajouté en caisse, hors chiffre d’affaires")).toBeInTheDocument();
  });

  it("détaille chaque achat et chaque sortie de caisse de la période", async () => {
    render(<StatistiquesPage />);

    const bilan = within(await screen.findByRole("region", { name: "Dépenses et résultat" }));
    const lignes = bilan.getAllByRole("row");
    expect(lignes).toHaveLength(4);   // en-tête + 2 bons + 1 sortie
    expect(lignes[1]).toHaveTextContent("Achat");
    expect(lignes[1]).toHaveTextContent("Bon APP-7");
    expect(lignes[1]).toHaveTextContent("Brasseries du Sud");
    expect(normaliser(lignes[1].textContent)).toContain("−100 000");
    expect(lignes[3]).toHaveTextContent("Sortie de caisse");
    expect(lignes[3]).toHaveTextContent("Transport de marchandises");
    expect(lignes[3]).toHaveTextContent("Moussa");
  });

  it("signale un résultat négatif et dit quand il n'y a aucune dépense", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { ...RAPPORT, bilanDepenses: {
      ...RAPPORT.bilanDepenses, achats: 0, nombreBonsRecus: 0, autresDepenses: 0, nombreDepenses: 0, totalDepenses: 0, resultat: -200,
      bonsRecus: [], depenses: [],
    } } });
    render(<StatistiquesPage />);

    const bilan = within(await screen.findByRole("region", { name: "Dépenses et résultat" }));
    expect(bilan.getByText("Aucune dépense sur cette période.")).toBeInTheDocument();
    expect(normaliser(bilan.getByText("Résultat").parentElement?.textContent ?? "")).toContain("-200");
  });
});

describe("statistiques : marge par produit", () => {
  it("affiche le taux de marge sur le prix de vente, et « Coût à vérifier » plutôt qu'un faux 100 %", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { ...RAPPORT, margesParArticle: [
      { articleId: 1, reference: "EAU-1", designation: "Eau minérale", quantiteVendue: 2, chiffreAffaires: 1000, coutAchat: 600, margeBrute: 400 },
      { articleId: 2, reference: "SIR-1", designation: "Sirop maison", quantiteVendue: 1, chiffreAffaires: 500, coutAchat: 0, margeBrute: 500 },
    ] } });
    render(<StatistiquesPage />);

    const tableau = within((await screen.findByText("Rentabilité détaillée par produit")).closest("section")!);
    expect(tableau.getByText("Marge %")).toBeInTheDocument();
    expect(tableau.getByText("Eau minérale").closest("tr")).toHaveTextContent("40 %");
    const sirop = tableau.getByText("Sirop maison").closest("tr")!;
    expect(within(sirop).getByText("Coût à vérifier")).toBeInTheDocument();
    expect(sirop).not.toHaveTextContent("100 %");
    expect(screen.getByText(/Certains articles vendus n’ont pas de coût d’achat/)).toBeInTheDocument();
  });
});

describe("statistiques : marge sans article à coût manquant", () => {
  it("n'affiche pas d'alerte quand tous les articles vendus ont un coût", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { ...RAPPORT, margesParArticle: [
      { articleId: 1, reference: "EAU-1", designation: "Eau minérale", quantiteVendue: 2, chiffreAffaires: 1000, coutAchat: 600, margeBrute: 400 },
    ] } });
    render(<StatistiquesPage />);

    await screen.findByText("Rentabilité détaillée par produit");
    expect(screen.queryByText(/Certains articles vendus n’ont pas de coût d’achat/)).not.toBeInTheDocument();
  });
});
