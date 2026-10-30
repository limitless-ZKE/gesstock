import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { telecharger } from "../lib/telechargement";
import { api } from "../services/api";
import { erreurHttp } from "../test/axios";
import JournalOperationsPage from "./JournalOperationsPage";

vi.mock("../services/api", () => ({ api: { get: vi.fn() } }));
vi.mock("../lib/telechargement", () => ({ telecharger: vi.fn() }));

const JOURNAL = {
  debut: "2026-10-09", fin: "2026-10-09", nombre: 4,
  parUtilisateur: [{ libelle: "admin", nombre: 2 }, { libelle: "marie", nombre: 1 }, { libelle: "Sans compte", nombre: 1 }],
  parOperation: [{ libelle: "Connexion refusée", nombre: 1 }, { libelle: "Vente annulée", nombre: 1 }, { libelle: "Vente validée", nombre: 1 }, { libelle: "Article modifié", nombre: 1 }],
  operations: [
    { id: 4, date: "2026-10-09T11:00:00", utilisateur: "admin", famille: "Catalogue", operation: "Article modifié", details: "Article EAU-001 « Eau 1,5 L » : prix de vente 400 → 450", element: "ARTICLE #3", poste: null },
    { id: 3, date: "2026-10-09T10:00:00", utilisateur: "admin", famille: "Ventes", operation: "Vente annulée", details: "Annulation de la vente au comptant VTE-1 (erreur)", element: "VENTE #1", poste: null },
    { id: 2, date: "2026-10-09T09:00:00", utilisateur: "marie", famille: "Ventes", operation: "Vente validée", details: "Validation de la vente VTE-1, total 1300", element: "VENTE #1", poste: null },
    { id: 1, date: "2026-10-09T08:00:00", utilisateur: null, famille: "Connexions et comptes", operation: "Connexion refusée", details: "Connexion refusée pour l'identifiant inconnu « pirate » (mot de passe incorrect ou compte inconnu), depuis le poste 192.168.1.20", element: "COMPTE", poste: "192.168.1.20" },
  ],
};

function afficher() {
  render(<JournalOperationsPage />);
  return userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
}

describe("journal des opérations", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 9, 12, 0));
    vi.mocked(telecharger).mockReset();
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/caisse/journal/caissiers") return { data: [{ username: "admin", actif: true }, { username: "marie", actif: true }, { username: "ancien", actif: false }] };
      if (url === "/journal-activites/operations") return { data: JOURNAL };
      if (url.endsWith("/pdf") || url.endsWith("/excel")) return { data: new Blob(["fichier"]) };
      throw new Error(`appel inattendu : ${url}`);
    });
  });
  afterEach(() => vi.useRealTimers());

  it("montre qui a fait quoi, en clair, avec le résumé de la journée", async () => {
    afficher();

    expect(await screen.findByText("Vente annulée", { selector: "td p" })).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith("/journal-activites/operations", { params: { debut: "2026-10-09", fin: "2026-10-09" } });
    expect(screen.getByText("Tous les utilisateurs", { selector: "header p" })).toBeInTheDocument();
    const ligneArticle = screen.getByText("Article EAU-001 « Eau 1,5 L » : prix de vente 400 → 450").closest("tr") as HTMLElement;
    expect(within(ligneArticle).getByText("admin")).toBeInTheDocument();
    expect(within(ligneArticle).getByText("Catalogue")).toBeInTheDocument();
    const ligneRefus = screen.getByText(/identifiant inconnu « pirate »/).closest("tr") as HTMLElement;
    expect(within(ligneRefus).getByText("—")).toBeInTheDocument();
    expect(screen.getByText("Connexions refusées").parentElement).toHaveTextContent("1");
    expect(screen.getByText("Par utilisateur :").parentElement).toHaveTextContent("admin 2 · marie 1 · Sans compte 1");
    expect(screen.getByRole("option", { name: "ancien (désactivé)" })).toBeInTheDocument();
  });

  it("se limite à un utilisateur et à une famille d'opérations, à l'écran comme à l'impression", async () => {
    const utilisateur = afficher();
    await screen.findByText("Vente annulée", { selector: "td p" });

    await utilisateur.selectOptions(screen.getByLabelText("Utilisateur"), "marie");
    await utilisateur.selectOptions(screen.getByLabelText("Opérations"), "VENTES");

    await waitFor(() => expect(api.get).toHaveBeenLastCalledWith("/journal-activites/operations",
      { params: { debut: "2026-10-09", fin: "2026-10-09", utilisateur: "marie", famille: "VENTES" } }));
    expect(screen.getByText("Utilisateur : marie · Ventes")).toBeInTheDocument();
    await screen.findByText("Vente annulée", { selector: "td p" });
    await utilisateur.click(screen.getByRole("button", { name: /PDF/ }));
    await waitFor(() => expect(telecharger).toHaveBeenCalledWith(expect.any(Blob), "gesstock-journal-operations-marie-2026-10-09-2026-10-09.pdf"));
    expect(api.get).toHaveBeenCalledWith("/journal-activites/operations/pdf",
      { params: { debut: "2026-10-09", fin: "2026-10-09", utilisateur: "marie", famille: "VENTES" }, responseType: "blob" });
  });

  it("cherche dans les opérations affichées sans rappeler le serveur", async () => {
    const utilisateur = afficher();
    await screen.findByText("Vente annulée", { selector: "td p" });
    const appels = vi.mocked(api.get).mock.calls.length;

    await utilisateur.type(screen.getByLabelText("Rechercher une opération"), "pirate");

    expect(screen.getByText(/identifiant inconnu « pirate »/)).toBeInTheDocument();
    expect(screen.queryByText("Vente annulée", { selector: "td p" })).not.toBeInTheDocument();
    expect(api.get).toHaveBeenCalledTimes(appels);
  });

  it("dit pourquoi le journal est vide, ou que le serveur refuse", async () => {
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/caisse/journal/caissiers") return { data: [] };
      return { data: { ...JOURNAL, nombre: 0, parUtilisateur: [], parOperation: [], operations: [] } };
    });
    afficher();
    expect(await screen.findByText("Aucune opération enregistrée sur cette période.")).toBeInTheDocument();
  });

  it("annonce un refus du serveur (un caissier n'a pas accès au journal)", async () => {
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/caisse/journal/caissiers") throw erreurHttp(403, { detail: "Vous n'avez pas les droits nécessaires pour cette action", code: "ACCES_REFUSE" });
      throw erreurHttp(403, { detail: "Vous n'avez pas les droits nécessaires pour cette action", code: "ACCES_REFUSE" });
    });
    afficher();
    expect(await screen.findByRole("alert")).toHaveTextContent("Vous n'avez pas les droits nécessaires pour cette action");
  });
});
