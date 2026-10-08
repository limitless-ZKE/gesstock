import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../auth/AuthContext";
import { api } from "../services/api";
import InventairesPage from "./InventairesPage";

vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));
vi.mock("../services/api", () => ({ api: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }));

const inventaire = (id: number, statut: string, extra: Record<string, unknown> = {}) => ({
  id, code: `INV-20261008-00${id}`, statut, perimetre: "TOUT", categorie: null, commentaire: null,
  ouvertLe: "2026-10-08T08:00:00", ouvertPar: "gestionnaire", soumisLe: null, valideLe: null, validePar: null,
  nombreLignes: 2, nombreComptees: 0, nombreEcarts: 0, valeurEcartTotal: 0, ...extra,
});
const INVENTAIRES = [inventaire(1, "EN_SAISIE"), inventaire(2, "SOUMIS", { nombreComptees: 2, nombreEcarts: 1, valeurEcartTotal: -4000 })];

const ligne = (articleId: number, designation: string, theorique: number, extra: Record<string, unknown> = {}) => ({
  articleId, reference: `REF-${articleId}`, designation, categorie: null, unite: null, stockTheorique: theorique,
  stockCompte: null, ecart: null, coutUnitaire: 2000, valeurEcart: null, motif: null, commentaire: null, ecartApplique: null, ...extra,
});
const LIGNES = [ligne(10, "Téléphone", 10), ligne(11, "Chargeur", 20)];

function afficher(roles: string[]) {
  vi.mocked(useAuth).mockReturnValue({ user: { username: "u", roles }, loading: false, login: vi.fn(), logout: vi.fn() });
  render(<InventairesPage />);
  return userEvent.setup();
}

describe("InventairesPage", () => {
  beforeEach(() => {
    vi.mocked(api.get).mockReset().mockImplementation(async (url: string) => {
      if (url === "/inventaires") return { data: INVENTAIRES };
      if (url === "/articles/categories") return { data: [{ id: 1, nom: "Électronique" }] };
      if (/^\/inventaires\/\d+\/lignes$/.test(url)) return { data: LIGNES, headers: { "x-total-count": "2" } };
      throw new Error(`GET inattendu : ${url}`);
    });
    vi.mocked(api.post).mockReset().mockResolvedValue({ data: {} });
    vi.mocked(api.put).mockReset();
    vi.spyOn(window, "confirm").mockReturnValue(true);
  });

  it("liste les inventaires avec leur statut et la valeur de l'écart", async () => {
    afficher(["GESTIONNAIRE"]);
    expect(await screen.findByText("INV-20261008-001")).toBeInTheDocument();
    expect(screen.getByText("En saisie")).toBeInTheDocument();
    expect(screen.getByText("À valider")).toBeInTheDocument();
    expect(screen.getByText("0 / 2")).toBeInTheDocument();
    expect(screen.getByText("2 / 2")).toBeInTheDocument();
  });

  it("enregistre un comptage dès qu'on quitte la case, et réclame un motif pour un écart", async () => {
    vi.mocked(api.put).mockResolvedValue({ data: ligne(10, "Téléphone", 10, { stockCompte: 8, ecart: -2, valeurEcart: -4000 }) });
    const utilisateur = afficher(["GESTIONNAIRE"]);
    await utilisateur.click(await screen.findByText("INV-20261008-001"));

    const case1 = await screen.findByLabelText("Quantité comptée de Téléphone");
    await utilisateur.type(case1, "8");
    await utilisateur.tab();   // quitter la case enregistre

    await waitFor(() => expect(api.put).toHaveBeenCalledWith("/inventaires/1/lignes/10", { stockCompte: 8, motif: null, commentaire: null }));
    const motif = await screen.findByLabelText("Motif de l’écart de Téléphone");
    expect(motif).toHaveDisplayValue("Motif requis");

    vi.mocked(api.put).mockResolvedValue({ data: ligne(10, "Téléphone", 10, { stockCompte: 8, ecart: -2, motif: "CASSE" }) });
    await utilisateur.selectOptions(motif, "CASSE");
    await waitFor(() => expect(api.put).toHaveBeenLastCalledWith("/inventaires/1/lignes/10", { stockCompte: 8, motif: "CASSE", commentaire: null }));
  });

  it("n'envoie rien tant que la case est vide ou inchangée", async () => {
    const utilisateur = afficher(["GESTIONNAIRE"]);
    await utilisateur.click(await screen.findByText("INV-20261008-001"));
    const case1 = await screen.findByLabelText("Quantité comptée de Téléphone");
    await utilisateur.click(case1);
    await utilisateur.tab();
    expect(api.put).not.toHaveBeenCalled();
  });

  it("un gestionnaire soumet mais ne valide pas : il attend un responsable", async () => {
    const utilisateur = afficher(["GESTIONNAIRE"]);
    await utilisateur.click(await screen.findByText("INV-20261008-002"));

    expect(await screen.findByText(/En attente de validation par un responsable/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Valider/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Annuler/ })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Quantité comptée de Téléphone")).not.toBeInTheDocument();   // plus de saisie une fois soumis
  });

  it("un responsable valide après confirmation, l'écart est appliqué par le serveur", async () => {
    const utilisateur = afficher(["MANAGER"]);
    await utilisateur.click(await screen.findByText("INV-20261008-002"));
    await utilisateur.click(await screen.findByRole("button", { name: /Valider/ }));

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("ne pourront plus être modifiés"));
    expect(api.post).toHaveBeenCalledWith("/inventaires/2/validation");
    expect(await screen.findByRole("status")).toHaveTextContent("écarts sont appliqués au stock");
  });

  it("un responsable peut reprendre la saisie ou annuler un inventaire soumis", async () => {
    const utilisateur = afficher(["ADMIN"]);
    await utilisateur.click(await screen.findByText("INV-20261008-002"));
    await utilisateur.click(await screen.findByRole("button", { name: /Reprendre la saisie/ }));
    expect(api.post).toHaveBeenCalledWith("/inventaires/2/reprise");

    await utilisateur.click(await screen.findByRole("button", { name: /Annuler/ }));
    expect(api.post).toHaveBeenLastCalledWith("/inventaires/2/annulation");
  });

  it("ouvre un inventaire limité à une catégorie", async () => {
    const utilisateur = afficher(["GESTIONNAIRE"]);
    await utilisateur.click(await screen.findByRole("button", { name: /Ouvrir un inventaire/ }));
    const boite = await screen.findByRole("dialog", { name: "Ouvrir un inventaire" });
    expect(boite).toBeInTheDocument();

    await utilisateur.selectOptions(screen.getByLabelText("Périmètre"), "CATEGORIE");
    await utilisateur.selectOptions(await screen.findByLabelText("Catégorie"), "1");
    await utilisateur.click(screen.getByRole("button", { name: "Ouvrir" }));

    expect(api.post).toHaveBeenCalledWith("/inventaires", { perimetre: "CATEGORIE", categorieId: 1, commentaire: null });
  });
});
