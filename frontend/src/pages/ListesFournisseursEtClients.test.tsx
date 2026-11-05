import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { telecharger } from "../lib/telechargement";
import { api } from "../services/api";
import ClientsDettesPage from "./ClientsDettesPage";
import FournisseursPage from "./FournisseursPage";

vi.mock("../services/api", () => ({ api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }));
vi.mock("../lib/telechargement", () => ({ telecharger: vi.fn() }));
vi.mock("../auth/AuthContext", () => ({ useAuth: () => ({ user: { username: "admin", roles: ["ADMIN"] } }) }));
vi.mock("../entreprise/EntrepriseContext", () => ({ useEntreprise: () => ({ entreprise: null, rafraichir: vi.fn(), logoUrl: vi.fn() }) }));

const jourIl = (joursAvant: number) => new Date(Date.now() - joursAvant * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

describe("liste des fournisseurs à imprimer", () => {
  const FOURNISSEURS = [
    { id: 1, nom: "Brasseries du Sud", telephone: "77 111 11 11", email: null, adresse: null },
    { id: 2, nom: "Grossiste Sall", telephone: null, email: null, adresse: null },
  ];

  beforeEach(() => {
    vi.mocked(api.get).mockReset().mockImplementation(async (url: string) => {
      if (url === "/fournisseurs") return { data: FOURNISSEURS };
      if (url === "/fournisseurs/comptes") return { data: [{ fournisseurId: 1, soldeDu: 45000, creditDisponible: 0 }, { fournisseurId: 2, soldeDu: 0, creditDisponible: 0 }] };
      if (url.startsWith("/fournisseurs/export/")) return { data: new Blob(["x"]) };
      throw new Error(`appel inattendu : ${url}`);
    });
    vi.mocked(telecharger).mockReset().mockResolvedValue(undefined);
  });

  it("filtre les fournisseurs avec ou sans dette, comme la liste imprimée", async () => {
    const utilisateur = userEvent.setup();
    render(<MemoryRouter><FournisseursPage /></MemoryRouter>);
    await screen.findByText("Brasseries du Sud");

    await utilisateur.selectOptions(screen.getByLabelText("Dette envers le fournisseur"), "with");
    expect(screen.getByText("Brasseries du Sud")).toBeInTheDocument();
    expect(screen.queryByText("Grossiste Sall")).not.toBeInTheDocument();

    await utilisateur.selectOptions(screen.getByLabelText("Dette envers le fournisseur"), "without");
    expect(screen.getByText("Grossiste Sall")).toBeInTheDocument();
    expect(screen.queryByText("Brasseries du Sud")).not.toBeInTheDocument();
  });

  it("imprime en PDF et exporte en Excel avec le filtre et la recherche de l'écran", async () => {
    const utilisateur = userEvent.setup();
    render(<MemoryRouter><FournisseursPage /></MemoryRouter>);
    await screen.findByText("Brasseries du Sud");
    await utilisateur.selectOptions(screen.getByLabelText("Dette envers le fournisseur"), "with");
    await utilisateur.type(screen.getByPlaceholderText("Nom, téléphone, email…"), "sud");

    await utilisateur.click(screen.getByRole("button", { name: /PDF \(imprimer\)/ }));
    await waitFor(() => expect(telecharger).toHaveBeenCalledWith(expect.any(Blob), "gesstock-fournisseurs-avec-dette.pdf"));
    expect(api.get).toHaveBeenCalledWith("/fournisseurs/export/pdf", { params: { filtre: "avec-dette", recherche: "sud" }, responseType: "blob" });

    await utilisateur.click(screen.getByRole("button", { name: /Excel/ }));
    await waitFor(() => expect(telecharger).toHaveBeenCalledWith(expect.any(Blob), "gesstock-fournisseurs-avec-dette.xlsx"));
  });
});

describe("liste des clients à imprimer", () => {
  const CLIENTS = [
    { id: 1, nom: "Aminata Diop", email: null, telephone: "77 000 00 01", adresse: null, nombreAchats: 4 },
    { id: 2, nom: "Moussa Fall", email: null, telephone: null, adresse: null, nombreAchats: 2 },
    { id: 3, nom: "Fatou Ndiaye", email: null, telephone: null, adresse: null, nombreAchats: 1 },
  ];
  const dette = (id: number, clientId: number, client: string, joursAvant: number, solde: number) => ({
    id, clientId, client, dateCreation: jourIl(joursAvant), venteId: id, numeroVente: `VTE-${id}`, montantInitial: solde, montantRegle: 0, solde, statut: "EN_COURS",
  });
  const DETTES = [dette(1, 1, "Aminata Diop", 70, 12000), dette(2, 2, "Moussa Fall", 5, 3000)];

  beforeEach(() => {
    vi.mocked(api.get).mockReset().mockImplementation(async (url: string) => {
      if (url === "/clients") return { data: CLIENTS };
      if (url === "/articles") return { data: [] };
      if (url === "/proformas") return { data: [] };
      if (url === "/dettes-clients") return { data: DETTES };
      if (url.startsWith("/clients/export/")) return { data: new Blob(["x"]) };
      throw new Error(`appel inattendu : ${url}`);
    });
    vi.mocked(telecharger).mockReset().mockResolvedValue(undefined);
  });

  async function afficher() {
    const utilisateur = userEvent.setup();
    render(<MemoryRouter><ClientsDettesPage /></MemoryRouter>);
    await screen.findByText("Aminata Diop");
    return utilisateur;
  }

  it("montre ce que doit chaque client et depuis combien de jours", async () => {
    await afficher();

    const ligneAminata = screen.getByText("Aminata Diop").closest("tr")!;
    expect(ligneAminata).toHaveTextContent(/12\s?000/);
    expect(ligneAminata).toHaveTextContent("depuis 70 j");
    expect(within(screen.getByText("Fatou Ndiaye").closest("tr")!).getAllByRole("cell")[4]).toHaveTextContent("—");   // colonne « Dette en cours »
  });

  it("propose les filtres de dette et les applique à la liste, du plus gros débiteur au plus petit", async () => {
    const utilisateur = await afficher();
    const filtre = screen.getByLabelText("Dette des clients");

    await utilisateur.selectOptions(filtre, "withDebt");
    expect(screen.queryByText("Fatou Ndiaye")).not.toBeInTheDocument();
    const noms = screen.getAllByRole("row").map((ligne) => ligne.textContent ?? "");
    expect(noms.findIndex((texte) => texte.includes("Aminata Diop"))).toBeLessThan(noms.findIndex((texte) => texte.includes("Moussa Fall")));

    await utilisateur.selectOptions(filtre, "noDebt");
    expect(screen.getByText("Fatou Ndiaye")).toBeInTheDocument();
    expect(screen.queryByText("Aminata Diop")).not.toBeInTheDocument();

    await utilisateur.selectOptions(filtre, "old60");
    expect(screen.getByText("Aminata Diop")).toBeInTheDocument();      // 70 jours
    expect(screen.queryByText("Moussa Fall")).not.toBeInTheDocument(); // 5 jours
  });

  it("imprime exactement la liste filtrée : même filtre, même nombre de jours, même recherche", async () => {
    const utilisateur = await afficher();
    await utilisateur.selectOptions(screen.getByLabelText("Dette des clients"), "old60");
    await utilisateur.type(screen.getByPlaceholderText("Rechercher un client…"), "diop");

    await utilisateur.click(screen.getByRole("button", { name: /PDF \(imprimer\)/ }));

    await waitFor(() => expect(telecharger).toHaveBeenCalledWith(expect.any(Blob), "gesstock-clients-dette-ancienne-60j.pdf"));
    expect(api.get).toHaveBeenCalledWith("/clients/export/pdf", { params: { filtre: "dette-ancienne", jours: 60, recherche: "diop" }, responseType: "blob" });
  });
});
