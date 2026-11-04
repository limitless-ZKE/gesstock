import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../services/api";
import { erreurHttp } from "../test/axios";
import DashboardPage from "./DashboardPage";

vi.mock("../services/api", () => ({ api: { get: vi.fn() } }));

const ESPACES_INSECABLES = new RegExp(`[${String.fromCharCode(160)}${String.fromCharCode(8239)}]`, "g");
const normaliser = (texte: string | null) => (texte ?? "").replace(ESPACES_INSECABLES, " ");

const STATS = {
  journee: "2026-10-09",
  chiffreAffairesJour: 1173900,
  nombreVentesJour: 3,
  totalCreances: 45000,
  alertesStockCritique: 2,
  valeurStockRevente: 8750000,
  nombreArticlesDisponibles: 41,
  ventes7Jours: [
    { journee: "2026-10-03", nombreVentes: 0, chiffreAffaires: 0 },
    { journee: "2026-10-04", nombreVentes: 2, chiffreAffaires: 250000 },
    { journee: "2026-10-05", nombreVentes: 0, chiffreAffaires: 0 },
    { journee: "2026-10-06", nombreVentes: 4, chiffreAffaires: 1080000 },
    { journee: "2026-10-07", nombreVentes: 1, chiffreAffaires: 480000 },
    { journee: "2026-10-08", nombreVentes: 0, chiffreAffaires: 0 },
    { journee: "2026-10-09", nombreVentes: 3, chiffreAffaires: 1173900 },
  ],
  topArticles: [
    { articleId: 1, reference: "ALIM-001", designation: "Riz local", quantiteVendue: 15 },
    { articleId: 2, reference: "MOB-001", designation: "Chaise de bureau", quantiteVendue: 1 },
  ],
};

function afficher() {
  render(<MemoryRouter><DashboardPage /></MemoryRouter>);
}

describe("tableau de bord", () => {
  beforeEach(() => {
    vi.mocked(api.get).mockResolvedValue({ data: STATS });
  });

  it("met le chiffre d'affaires du jour en avant, puis les trois autres chiffres", async () => {
    afficher();
    const chiffres = within(await screen.findByRole("region", { name: "Chiffres du jour" }));
    expect(normaliser(chiffres.getByText(/1 173 900/).textContent)).toContain("1 173 900");
    expect(chiffres.getByText("Ventes réalisées")).toBeInTheDocument();
    expect(normaliser(chiffres.getByText(/45 000/).textContent)).toContain("45 000");
    expect(chiffres.getByText("2 articles")).toBeInTheDocument();
    expect(chiffres.getByRole("link", { name: "Voir le catalogue" })).toHaveAttribute("href", "/articles");
  });

  it("montre la valeur du stock disponible au prix de revente et le nombre d'articles disponibles", async () => {
    afficher();
    const chiffres = within(await screen.findByRole("region", { name: "Chiffres du jour" }));
    expect(chiffres.getByText("Valeur du stock à la revente")).toBeInTheDocument();
    expect(normaliser(chiffres.getByText(/8 750 000/).textContent)).toContain("8 750 000");
    expect(chiffres.getByText("Stock disponible × prix de vente")).toBeInTheDocument();
    expect(chiffres.getByText("41 articles")).toBeInTheDocument();
    expect(chiffres.getByRole("link", { name: "Voir le stock" })).toHaveAttribute("href", "/articles");
  });

  it("accorde « article » au singulier et n'invite pas à ouvrir le catalogue quand rien n'est à surveiller", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { ...STATS, alertesStockCritique: 0 } });
    afficher();
    expect(await screen.findByText("0 article")).toBeInTheDocument();
    expect(screen.getByText("Aucun article sous le seuil")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Voir le catalogue" })).not.toBeInTheDocument();
  });

  it("dessine une colonne cliquable par jour, avec le détail lisible au clavier", async () => {
    afficher();
    await screen.findByRole("region", { name: "Chiffres du jour" });
    const jours = await screen.findAllByRole("img", { name: /ventes?$/ });
    expect(jours).toHaveLength(7);
    const hier = jours[5].getAttribute("aria-label");
    expect(normaliser(hier)).toContain("jeudi 8 octobre");
    expect(normaliser(jours[6].getAttribute("aria-label"))).toContain("1 173 900");
    expect(normaliser(jours[6].getAttribute("aria-label"))).toContain("3 ventes");
    expect(jours[6]).toHaveAttribute("tabindex", "0");
  });

  it("n'écrit la valeur que sur la journée en cours et sur le meilleur jour, pas sur chaque colonne", async () => {
    // aujourd'hui (300 000) n'est pas le meilleur jour (1 080 000, le 6) : deux valeurs écrites, sur les 5 autres colonnes aucune
    const aujourdhui = { journee: "2026-10-09", nombreVentes: 1, chiffreAffaires: 300000 };
    vi.mocked(api.get).mockResolvedValue({ data: { ...STATS, ventes7Jours: [...STATS.ventes7Jours.slice(0, 6), aujourdhui] } });
    afficher();
    await screen.findByRole("region", { name: "Chiffres du jour" });
    const graphique = screen.getByRole("group", { name: /Colonnes du chiffre d’affaires/ });
    const valeurs = Array.from(graphique.querySelectorAll("text"))
      .map((t) => normaliser(t.textContent))
      .filter((t) => /^\d{1,3}( \d{3})+$/.test(t));
    expect(valeurs).toEqual(["1 080 000", "300 000"]);
  });

  it("quand aujourd'hui est aussi le meilleur jour, la valeur n'est écrite qu'une fois", async () => {
    afficher();
    await screen.findByRole("region", { name: "Chiffres du jour" });
    const graphique = screen.getByRole("group", { name: /Colonnes du chiffre d’affaires/ });
    const valeurs = Array.from(graphique.querySelectorAll("text"))
      .map((t) => normaliser(t.textContent))
      .filter((t) => /^\d{1,3}( \d{3})+$/.test(t));
    expect(valeurs).toEqual(["1 173 900"]);
  });

  it("donne les mêmes chiffres en tableau, sans dépendre du dessin", async () => {
    afficher();
    await screen.findByRole("region", { name: "Chiffres du jour" });
    const tableau = screen.getByRole("table");
    expect(within(tableau).getAllByRole("row")).toHaveLength(8);
    expect(normaliser(within(tableau).getByText(/1 080 000/).textContent)).toContain("1 080 000");
  });

  it("classe les articles les plus vendus avec le bon pluriel", async () => {
    afficher();
    expect(await screen.findByText("15 unités")).toBeInTheDocument();
    expect(screen.getByText("1 unité")).toBeInTheDocument();
    expect(screen.getByText("Réf. ALIM-001")).toBeInTheDocument();
  });

  it("dit quoi faire quand il n'y a encore aucune vente", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { ...STATS, ventes7Jours: [], topArticles: [] } });
    afficher();
    expect(await screen.findByText(/Aucune vente sur ces 7 jours/)).toBeInTheDocument();
    expect(screen.getByText("Pas encore de ventes cette semaine.")).toBeInTheDocument();
  });

  it("annonce l'erreur avec un moyen de réessayer", async () => {
    vi.mocked(api.get).mockRejectedValue(erreurHttp(500));
    afficher();
    expect(await screen.findByRole("alert")).toHaveTextContent("Réessayer");
  });
});
