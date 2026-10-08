import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../auth/AuthContext";
import { useEntreprise, type Entreprise } from "../entreprise/EntrepriseContext";
import { api } from "../services/api";
import { erreurHttp } from "../test/axios";
import EntreprisePage from "./EntreprisePage";

vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));
vi.mock("../services/api", () => ({ api: { put: vi.fn(), post: vi.fn(), delete: vi.fn() }, API_BASE_URL: "http://serveur/api" }));
vi.mock("../entreprise/EntrepriseContext", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../entreprise/EntrepriseContext")>()),
  useEntreprise: vi.fn(),
}));

const ENTREPRISE: Entreprise = {
  nom: "Baobab SARL", adresse: "12 rue du Marché", telephone: "+221 33 800 00 00", email: "contact@baobab.example",
  identifiantLegal: "RCCM 123", siteWeb: null, piedDePage: "Merci !", logoSurTickets: true,
  logo: { empreinte: "empreinte-1", largeur: 600, hauteur: 200 }, misAJourLe: "2026-10-08T10:00:00", misAJourPar: "admin",
};

const rafraichir = vi.fn();

function afficher(roles: string[], entreprise: Entreprise | null = ENTREPRISE) {
  vi.mocked(useAuth).mockReturnValue({ user: { username: "u", roles }, loading: false, login: vi.fn(), logout: vi.fn() });
  vi.mocked(useEntreprise).mockReturnValue({ entreprise, rafraichir, logoUrl: vi.fn() });
  render(<EntreprisePage />);
  return userEvent.setup({ applyAccept: false });   // le serveur, pas le sélecteur de fichiers, fait foi sur le format
}

const png = (octets = 1000, nom = "logo.png", type = "image/png") => new File([new Uint8Array(octets)], nom, { type });

describe("EntreprisePage", () => {
  beforeEach(() => {
    rafraichir.mockReset().mockResolvedValue(undefined);
    vi.mocked(api.put).mockReset().mockResolvedValue({ data: {} });
    vi.mocked(api.post).mockReset().mockResolvedValue({ data: {} });
    vi.mocked(api.delete).mockReset().mockResolvedValue({ data: {} });
    vi.spyOn(window, "confirm").mockReturnValue(true);
  });

  it("affiche les informations enregistrées", () => {
    afficher(["MANAGER"]);
    expect(screen.getByLabelText("Nom de l’entreprise")).toHaveValue("Baobab SARL");
    expect(screen.getByLabelText("Adresse")).toHaveValue("12 rue du Marché");
    expect(screen.getByLabelText("RCCM / NINEA / SIRET")).toHaveValue("RCCM 123");
    expect(screen.getByLabelText("Site web")).toHaveValue("");
    expect(screen.getByRole("img", { name: "Logo actuel" })).toBeInTheDocument();
  });

  it("enregistre les modifications puis recharge l'entreprise pour les documents", async () => {
    const utilisateur = afficher(["MANAGER"]);
    const nom = screen.getByLabelText("Nom de l’entreprise");
    await utilisateur.clear(nom);
    await utilisateur.type(nom, "Baobab Group");
    await utilisateur.type(screen.getByLabelText("Site web"), "www.baobab.example");
    await utilisateur.click(screen.getByLabelText("Imprimer le logo sur les tickets 80 mm"));
    await utilisateur.click(screen.getByRole("button", { name: /Enregistrer/ }));

    expect(api.put).toHaveBeenCalledWith("/entreprise", {
      nom: "Baobab Group", adresse: "12 rue du Marché", telephone: "+221 33 800 00 00", email: "contact@baobab.example",
      identifiantLegal: "RCCM 123", siteWeb: "www.baobab.example", piedDePage: "Merci !", logoSurTickets: false,
    });
    await waitFor(() => expect(rafraichir).toHaveBeenCalledTimes(1));
    expect(await screen.findByRole("status")).toHaveTextContent("Informations enregistrées");
  });

  it("montre en direct, dans l'aperçu, ce que les documents imprimeront", async () => {
    const utilisateur = afficher(["ADMIN"]);
    const ticket = screen.getByRole("region", { name: "Aperçu du ticket" });
    expect(ticket).toHaveTextContent("Baobab SARL");

    const nom = screen.getByLabelText("Nom de l’entreprise");
    await utilisateur.clear(nom);
    await utilisateur.type(nom, "Nouveau commerce");

    expect(ticket).toHaveTextContent("Nouveau commerce");
    expect(screen.getByRole("region", { name: "Aperçu de l’en-tête A4" })).toHaveTextContent("Nouveau commerce");
    expect(api.put).not.toHaveBeenCalled();   // simple aperçu : rien n'est enregistré
  });

  it("empêche d'enregistrer sans nom", async () => {
    const utilisateur = afficher(["ADMIN"]);
    await utilisateur.clear(screen.getByLabelText("Nom de l’entreprise"));
    expect(screen.getByRole("button", { name: /Enregistrer/ })).toBeDisabled();
  });

  it("affiche le refus du serveur", async () => {
    vi.mocked(api.put).mockRejectedValue(erreurHttp(400, { detail: "Les données envoyées sont invalides" }));
    const utilisateur = afficher(["ADMIN"]);
    await utilisateur.click(screen.getByRole("button", { name: /Enregistrer/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Les données envoyées sont invalides");
    expect(rafraichir).not.toHaveBeenCalled();
  });

  it("envoie un logo choisi, en multipart, puis recharge", async () => {
    const utilisateur = afficher(["MANAGER"]);
    const fichier = png(5000);
    await utilisateur.upload(screen.getByLabelText("Choisir un fichier de logo"), fichier);

    expect(api.post).toHaveBeenCalledTimes(1);
    const [chemin, donnees] = vi.mocked(api.post).mock.calls[0];
    expect(chemin).toBe("/entreprise/logo");
    expect((donnees as FormData).get("fichier")).toBe(fichier);
    await waitFor(() => expect(rafraichir).toHaveBeenCalled());
    expect(await screen.findByRole("status")).toHaveTextContent("Logo enregistré");
  });

  it("refuse tout de suite un fichier qui n'est pas du PNG ou du JPEG, ou trop lourd, sans appeler le serveur", async () => {
    const utilisateur = afficher(["MANAGER"]);
    const champ = screen.getByLabelText("Choisir un fichier de logo");

    await utilisateur.upload(champ, png(100, "logo.svg", "image/svg+xml"));
    expect(await screen.findByRole("alert")).toHaveTextContent("PNG ou JPEG");

    await utilisateur.upload(champ, png(3 * 1024 * 1024));
    expect(await screen.findByRole("alert")).toHaveTextContent("2 Mo");
    expect(api.post).not.toHaveBeenCalled();
  });

  it("affiche l'explication du serveur quand il refuse l'image", async () => {
    vi.mocked(api.post).mockRejectedValue(erreurHttp(400, { detail: "Image illisible ou corrompue : envoyez un PNG ou un JPEG valide" }));
    const utilisateur = afficher(["ADMIN"]);
    await utilisateur.upload(screen.getByLabelText("Choisir un fichier de logo"), png());

    expect(await screen.findByRole("alert")).toHaveTextContent("Image illisible ou corrompue");
  });

  it("retire le logo après confirmation seulement", async () => {
    const utilisateur = afficher(["ADMIN"]);

    vi.mocked(window.confirm).mockReturnValueOnce(false);
    await utilisateur.click(screen.getByRole("button", { name: /Retirer/ }));
    expect(api.delete).not.toHaveBeenCalled();

    await utilisateur.click(screen.getByRole("button", { name: /Retirer/ }));
    expect(api.delete).toHaveBeenCalledWith("/entreprise/logo");
    await waitFor(() => expect(rafraichir).toHaveBeenCalled());
  });

  it("propose « Ajouter » plutôt que « Remplacer » quand il n'y a pas encore de logo", () => {
    afficher(["ADMIN"], { ...ENTREPRISE, logo: null });
    expect(screen.getByRole("button", { name: /Ajouter un logo/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Retirer/ })).not.toBeInTheDocument();
    expect(screen.getByText("Aucun logo")).toBeInTheDocument();
  });

  it("est en lecture seule pour un rôle sans droit de modification", () => {
    afficher(["GESTIONNAIRE"]);
    expect(screen.getByText(/Seuls un responsable/)).toBeInTheDocument();
    expect(screen.getByLabelText("Nom de l’entreprise")).toBeDisabled();
    expect(screen.queryByRole("button", { name: /Enregistrer/ })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Choisir un fichier de logo")).not.toBeInTheDocument();
  });

  it("patiente tant que l'entreprise n'est pas chargée", () => {
    afficher(["ADMIN"], null);
    expect(screen.getByText(/Chargement/)).toBeInTheDocument();
  });
});
