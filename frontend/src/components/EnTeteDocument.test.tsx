import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useEntreprise, type Entreprise } from "../entreprise/EntrepriseContext";
import EnTeteDocument from "./EnTeteDocument";

vi.mock("../services/api", () => ({ api: {}, API_BASE_URL: "http://serveur/api" }));
vi.mock("../entreprise/EntrepriseContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../entreprise/EntrepriseContext")>()),
  useEntreprise: vi.fn(),
}));

const ENTREPRISE: Entreprise = {
  nom: "Baobab SARL", adresse: "12 rue du Marché, Dakar", telephone: "+221 33 800 00 00", email: "contact@baobab.example",
  identifiantLegal: "RCCM SN-DKR-2020-B-1234", siteWeb: "www.baobab.example", piedDePage: "Merci !", logoSurTickets: true,
  logo: { empreinte: "empreinte-1", largeur: 600, hauteur: 200 }, misAJourLe: "2026-10-08T10:00:00", misAJourPar: "admin",
};

function afficher(variante: "ticket" | "a4", entreprise: Entreprise | null = ENTREPRISE, apercu?: Entreprise | null) {
  vi.mocked(useEntreprise).mockReturnValue({ entreprise, rafraichir: vi.fn(), logoUrl: vi.fn() });
  return render(<EnTeteDocument variante={variante} entreprise={apercu} />);
}

describe("EnTeteDocument", () => {
  it("imprime nom et coordonnées de l'entreprise, jamais un nom écrit en dur", () => {
    afficher("ticket");
    expect(screen.getByText("Baobab SARL")).toBeInTheDocument();
    expect(screen.getByText("12 rue du Marché, Dakar")).toBeInTheDocument();
    expect(screen.getByText("Tél. +221 33 800 00 00 · contact@baobab.example")).toBeInTheDocument();
    expect(screen.getByText("RCCM SN-DKR-2020-B-1234")).toBeInTheDocument();
    expect(screen.queryByText(/gesstock/i)).not.toBeInTheDocument();
  });

  it("n'écrit que les lignes renseignées", () => {
    afficher("a4", { ...ENTREPRISE, adresse: null, telephone: null, email: null, identifiantLegal: null, siteWeb: null });
    expect(screen.getByText("Baobab SARL")).toBeInTheDocument();
    expect(screen.queryByText(/Tél\./)).not.toBeInTheDocument();
    expect(document.querySelectorAll("p")).toHaveLength(1);
  });

  it("demande au serveur le logo au format de l'emplacement : ticket pour le ticket, en-tête pour le A4", () => {
    const { unmount } = afficher("ticket");
    expect(screen.getByAltText("Logo Baobab SARL")).toHaveAttribute("src", "http://serveur/api/entreprise/logo?taille=ticket&v=empreinte-1");
    unmount();
    afficher("a4");
    expect(screen.getByAltText("Logo Baobab SARL")).toHaveAttribute("src", "http://serveur/api/entreprise/logo?taille=entete&v=empreinte-1");
  });

  it("borne la taille d'impression du logo en millimètres selon le support", () => {
    // style en ligne lu directement : le style « calculé » de jsdom ne comprend pas les millimètres
    const { unmount } = afficher("ticket");
    const ticket = screen.getByAltText("Logo Baobab SARL");
    expect([ticket.style.maxWidth, ticket.style.maxHeight]).toEqual(["42mm", "18mm"]);
    unmount();
    afficher("a4");
    const a4 = screen.getByAltText("Logo Baobab SARL");
    expect([a4.style.maxWidth, a4.style.maxHeight]).toEqual(["60mm", "28mm"]);
  });

  it("retire le logo des tickets si l'entreprise le demande, mais le garde sur les documents A4", () => {
    const { unmount } = afficher("ticket", { ...ENTREPRISE, logoSurTickets: false });
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByText("Baobab SARL")).toBeInTheDocument();
    unmount();
    afficher("a4", { ...ENTREPRISE, logoSurTickets: false });
    expect(screen.getByRole("img")).toBeInTheDocument();
  });

  it("n'affiche aucune image quand il n'y a pas de logo", () => {
    afficher("ticket", { ...ENTREPRISE, logo: null });
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("omet un logo impossible à charger plutôt que d'imprimer une icône d'image cassée", () => {
    afficher("a4");
    fireEvent.error(screen.getByAltText("Logo Baobab SARL"));
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByText("Baobab SARL")).toBeInTheDocument();
  });

  it("ne rend rien tant que l'entreprise n'est pas chargée", () => {
    const { container } = afficher("ticket", null);
    expect(container).toBeEmptyDOMElement();
  });

  it("affiche les valeurs d'aperçu à la place de l'entreprise enregistrée", () => {
    afficher("ticket", ENTREPRISE, { ...ENTREPRISE, nom: "Nom en cours de saisie" });
    expect(screen.getByText("Nom en cours de saisie")).toBeInTheDocument();
    expect(screen.queryByText("Baobab SARL")).not.toBeInTheDocument();
  });
});
