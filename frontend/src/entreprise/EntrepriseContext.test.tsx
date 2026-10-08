import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../auth/AuthContext";
import { api } from "../services/api";
import { erreurHttp } from "../test/axios";
import { EntrepriseProvider, logoUrlDe, useEntreprise, type Entreprise } from "./EntrepriseContext";

vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));
vi.mock("../services/api", () => ({ api: { get: vi.fn() }, API_BASE_URL: "http://serveur/api" }));

const ENTREPRISE: Entreprise = {
  nom: "Baobab SARL", adresse: "12 rue du Marché", telephone: "+221 33 800 00 00", email: null, identifiantLegal: "RCCM 123",
  siteWeb: null, piedDePage: "Merci !", logoSurTickets: true, logo: { empreinte: "abc 123", largeur: 600, hauteur: 200 },
  misAJourLe: "2026-10-08T10:00:00", misAJourPar: "admin",
};

function Sonde() {
  const { entreprise, rafraichir, logoUrl } = useEntreprise();
  return (
    <div>
      <p>{entreprise ? `entreprise : ${entreprise.nom}` : "aucune entreprise"}</p>
      <p>ticket : {logoUrl("ticket") ?? "aucun logo"}</p>
      <button onClick={() => void rafraichir()}>recharger</button>
    </div>
  );
}

function connecte(oui: boolean) {
  vi.mocked(useAuth).mockReturnValue({ user: oui ? { username: "marie", roles: ["CAISSIER"] } : null, loading: false, login: vi.fn(), logout: vi.fn() });
}

describe("EntrepriseProvider", () => {
  beforeEach(() => {
    vi.mocked(api.get).mockReset().mockResolvedValue({ data: ENTREPRISE });
  });

  it("charge l'entreprise après la connexion et donne l'adresse du logo avec son empreinte", async () => {
    connecte(true);
    render(<EntrepriseProvider><Sonde /></EntrepriseProvider>);

    expect(await screen.findByText("entreprise : Baobab SARL")).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith("/entreprise");
    expect(screen.getByText("ticket : http://serveur/api/entreprise/logo?taille=ticket&v=abc%20123")).toBeInTheDocument();
  });

  it("n'appelle pas le serveur tant que personne n'est connecté", () => {
    connecte(false);
    render(<EntrepriseProvider><Sonde /></EntrepriseProvider>);

    expect(screen.getByText("aucune entreprise")).toBeInTheDocument();
    expect(api.get).not.toHaveBeenCalled();
  });

  it("ne bloque jamais l'écran quand le serveur échoue : les documents s'impriment sans en-tête", async () => {
    connecte(true);
    vi.mocked(api.get).mockRejectedValue(erreurHttp(500, { detail: "Erreur interne" }));
    render(<EntrepriseProvider><Sonde /></EntrepriseProvider>);

    await waitFor(() => expect(api.get).toHaveBeenCalled());
    expect(screen.getByText("aucune entreprise")).toBeInTheDocument();
  });

  it("se recharge à la demande et reprend le nouveau logo", async () => {
    connecte(true);
    render(<EntrepriseProvider><Sonde /></EntrepriseProvider>);
    await screen.findByText("entreprise : Baobab SARL");

    vi.mocked(api.get).mockResolvedValue({ data: { ...ENTREPRISE, nom: "Baobab Group", logo: { empreinte: "nouveau", largeur: 10, hauteur: 10 } } });
    await userEvent.setup().click(screen.getByText("recharger"));

    expect(await screen.findByText("entreprise : Baobab Group")).toBeInTheDocument();
    expect(screen.getByText(/v=nouveau/)).toBeInTheDocument();
  });

  it("n'annonce aucun logo quand il n'y en a pas", async () => {
    connecte(true);
    vi.mocked(api.get).mockResolvedValue({ data: { ...ENTREPRISE, logo: null } });
    render(<EntrepriseProvider><Sonde /></EntrepriseProvider>);

    expect(await screen.findByText("ticket : aucun logo")).toBeInTheDocument();
  });

  it("refuse de fonctionner hors du fournisseur", () => {
    const erreurs = vi.spyOn(console, "error").mockImplementation(() => undefined);
    expect(() => render(<Sonde />)).toThrow("EntrepriseProvider");
    erreurs.mockRestore();
  });
});

describe("logoUrlDe", () => {
  it("demande le format voulu et change d'adresse quand le logo change", () => {
    const premier = logoUrlDe(ENTREPRISE, "entete");
    const second = logoUrlDe({ ...ENTREPRISE, logo: { empreinte: "autre", largeur: 1, hauteur: 1 } }, "entete");

    expect(premier).toContain("taille=entete");
    expect(second).not.toEqual(premier);
    expect(logoUrlDe(null, "ticket")).toBeNull();
    expect(logoUrlDe({ ...ENTREPRISE, logo: null }, "ticket")).toBeNull();
  });
});
