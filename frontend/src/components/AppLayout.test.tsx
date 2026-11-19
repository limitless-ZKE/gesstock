import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../auth/AuthContext";
import { useEntreprise } from "../entreprise/EntrepriseContext";
import { usePanier } from "../panier/PanierContext";
import { api } from "../services/api";
import AppLayout from "./AppLayout";

vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));
vi.mock("../entreprise/EntrepriseContext", () => ({ useEntreprise: vi.fn() }));
vi.mock("../panier/PanierContext", () => ({ usePanier: vi.fn() }));
vi.mock("../services/api", () => ({ api: { get: vi.fn() } }));

const TOUS_LES_LIENS = [
  "Tableau de bord", "Caisse & ventes", "Historique des ventes", "Mouvements de caisse", "Articles", "Inventaires",
  "Approvisionnements", "Fournisseurs", "Retours fournisseurs", "Clients & dettes", "Statistiques", "Journal des opérations", "Entreprise",
  "Administration",
];

function afficher(roles: string[]) {
  vi.mocked(useAuth).mockReturnValue({ user: { username: "marie", roles }, loading: false, login: vi.fn(), logout: vi.fn() });
  render(
    <MemoryRouter initialEntries={["/caisse"]}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/caisse" element={<p>Écran de caisse</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

const liensVisibles = () => TOUS_LES_LIENS.filter((nom) => screen.queryByRole("link", { name: nom }) !== null);

/**
 * Le menu n'est qu'un confort : le serveur refuse de toute façon (voir RbacMatrixTests côté Java). Mais un menu qui montre
 * à un caissier des écrans interdits, ou cache à un gestionnaire ceux de son métier, serait un défaut visible.
 */
describe("menu de navigation selon les rôles", () => {
  beforeEach(() => {
    vi.mocked(api.get).mockResolvedValue({ data: { statut: "OUVERTE" } });
    vi.mocked(useEntreprise).mockReturnValue({ entreprise: null, rafraichir: vi.fn(), logoUrl: () => null });
    vi.mocked(usePanier).mockReturnValue({ nombreArticles: 0 } as ReturnType<typeof usePanier>);
  });

  it("le caissier ne voit que la vente, les clients et la caisse", async () => {
    afficher(["CAISSIER"]);
    expect(await screen.findByText("Caisse ouverte")).toBeInTheDocument();
    expect(liensVisibles()).toEqual(["Caisse & ventes", "Historique des ventes", "Mouvements de caisse", "Clients & dettes"]);
  });

  it("le gestionnaire voit le stock, les fournisseurs et les inventaires, mais ni l'entreprise ni l'administration", async () => {
    afficher(["GESTIONNAIRE"]);
    await screen.findByText("Caisse ouverte");
    expect(liensVisibles()).toEqual(TOUS_LES_LIENS.filter((nom) => !["Entreprise", "Administration"].includes(nom)));
  });

  it("un responsable voit tout, y compris l'entreprise (nom, logo), sauf l'administration des comptes", async () => {
    afficher(["ROLE_MANAGER"]);
    await screen.findByText("Caisse ouverte");
    expect(liensVisibles()).toEqual(TOUS_LES_LIENS.filter((nom) => nom !== "Administration"));
  });

  it("l'administrateur voit tout", async () => {
    afficher(["ADMIN"]);
    await screen.findByText("Caisse ouverte");
    expect(liensVisibles()).toEqual(TOUS_LES_LIENS);
  });

  it("la liste des liens défile seule : sinon, sur un écran bas, les derniers écrans sont inaccessibles", async () => {
    afficher(["ADMIN"]);
    await screen.findByText("Caisse ouverte");
    // jsdom ne calcule aucune mise en page : on garde les deux classes qui rendent le défilement possible
    // (la vraie vérification se fait dans un navigateur, écran réduit).
    const zone = screen.getByRole("navigation").parentElement;
    expect(zone).toHaveClass("overflow-y-auto", "min-h-0", "flex-1");
  });

  it("porte le nom du magasin, pas celui du logiciel, quand l'entreprise est renseignée", async () => {
    vi.mocked(useEntreprise).mockReturnValue({
      entreprise: { nom: "Boutique Aminata" } as ReturnType<typeof useEntreprise>["entreprise"],
      rafraichir: vi.fn(),
      logoUrl: () => null,
    });
    afficher(["ADMIN"]);
    expect(await screen.findByText("Boutique Aminata")).toBeInTheDocument();
  });

  it("regroupe les liens par thème et masque le titre d'un groupe vide pour ce rôle", async () => {
    afficher(["CAISSIER"]);
    await screen.findByText("Caisse ouverte");
    expect(screen.getByText("Vente")).toBeInTheDocument();
    expect(screen.queryByText("Stock")).not.toBeInTheDocument();
    expect(screen.queryByText("Gestion")).not.toBeInTheDocument();
  });

  it("affiche l'écran demandé dans la zone de contenu", async () => {
    afficher(["CAISSIER"]);
    expect(await screen.findByText("Écran de caisse")).toBeInTheDocument();
  });

  it("indique une caisse fermée quand aucune session n'est ouverte (404)", async () => {
    vi.mocked(api.get).mockRejectedValue({ response: { status: 404 } });
    afficher(["CAISSIER"]);
    expect(await screen.findByText("Caisse fermée")).toBeInTheDocument();
  });

  it("indique une caisse indisponible quand l'état est inconnu (panne)", async () => {
    vi.mocked(api.get).mockRejectedValue({ response: { status: 500 } });
    afficher(["CAISSIER"]);
    expect(await screen.findByText("Caisse indisponible")).toBeInTheDocument();
  });

  it("rappelle dans le menu les articles en attente dans le panier de vente", async () => {
    vi.mocked(usePanier).mockReturnValue({ nombreArticles: 3 } as ReturnType<typeof usePanier>);
    afficher(["CAISSIER"]);
    await screen.findByText("Caisse ouverte");
    expect(screen.getByRole("link", { name: /Caisse & ventes/ })).toHaveTextContent("3");
  });

  it("n'affiche aucun compteur quand le panier est vide", async () => {
    afficher(["CAISSIER"]);
    await screen.findByText("Caisse ouverte");
    expect(screen.getByRole("link", { name: "Caisse & ventes" })).toBeInTheDocument();
  });
});
