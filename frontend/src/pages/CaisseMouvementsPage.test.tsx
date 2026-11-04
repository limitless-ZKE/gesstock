import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../auth/AuthContext";
import { api } from "../services/api";
import { erreurHttp } from "../test/axios";
import CaisseMouvementsPage from "./CaisseMouvementsPage";

vi.mock("../services/api", () => ({ api: { get: vi.fn(), post: vi.fn() } }));
const MOUVEMENTS = [
  { id: 1, sessionId: 4, type: "ENTREE", origine: "MANUEL", motif: "Apport du patron", beneficiaire: "Patron", montant: 20000, dateMouvement: "2026-11-04T08:00:00", effectuePar: "patron" },
  { id: 2, sessionId: 4, type: "SORTIE", origine: "MANUEL", motif: "Transport", beneficiaire: "Moussa", montant: 2500, dateMouvement: "2026-11-04T09:00:00", effectuePar: "caissier" },
  { id: 3, sessionId: 4, type: "SORTIE", origine: "REGLEMENT_FOURNISSEUR", motif: "Règlement fournisseur : bon APP-1", beneficiaire: "Brasseries du Sud", montant: 10000, dateMouvement: "2026-11-04T10:00:00", effectuePar: "patron" },
];
vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));

const SESSION = {
  id: 4, code: "CAISSE-1", statut: "OUVERTE", fondDeCaisse: 1000, soldeTheorique: 246000, encaissementsEspeces: 245000, reglementsClientsEspeces: 0, entreesDeFonds: 0, sortiesCaisse: 0,
};

describe("mouvements de caisse : session en cours et journal", () => {
  beforeEach(() => {
    vi.mocked(useAuth).mockReturnValue({ user: { username: "patron", roles: ["ADMIN"] }, loading: false, login: vi.fn(), logout: vi.fn() });
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/referentiels/sessions-caisse/ouverte") return { data: SESSION };
      if (url === "/caisse/mouvements") return { data: [] };
      if (url === "/caisse/journal/caissiers") return { data: [] };
      if (url === "/caisse/journal") return { data: { debut: "2026-10-09", fin: "2026-10-09", totaux: { nombreSessions: 0, sessionsNonCloturees: 0, nombreVentes: 0, nombreVentesAnnulees: 0, encaissementsEspeces: 0, reglementsClientsEspeces: 0, sorties: 0, especesComptees: 0, ecarts: 0 }, sessions: [] } };
      throw new Error(`appel inattendu : ${url}`);
    });
  });

  it("s'ouvre sur la session en cours, avec le bouton de nouvelle sortie", async () => {
    render(<CaisseMouvementsPage />);
    expect(await screen.findByText("Solde théorique disponible")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Session en cours" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("button", { name: /Enregistrer une sortie/ })).toBeEnabled();
    expect(api.get).not.toHaveBeenCalledWith("/caisse/journal", expect.anything());
  });

  it("liste les entrées de fonds et les sorties, avec leur sens et l'origine d'un paiement fournisseur", async () => {
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/referentiels/sessions-caisse/ouverte") return { data: { ...SESSION, entreesDeFonds: 20000, sortiesCaisse: 12500 } };
      if (url === "/caisse/mouvements") return { data: MOUVEMENTS };
      throw new Error(`appel inattendu : ${url}`);
    });
    render(<CaisseMouvementsPage />);

    expect(await screen.findByText("Apport du patron")).toBeInTheDocument();
    const lignes = screen.getAllByRole("row");
    expect(lignes[1]).toHaveTextContent("Entrée de fonds");
    expect(lignes[1]).toHaveTextContent("+20");
    expect(lignes[2]).toHaveTextContent("Sortie");
    expect(lignes[2]).toHaveTextContent("−2");
    expect(lignes[3]).toHaveTextContent("Paiement fournisseur");
    expect(screen.getByText("Entrées de fonds", { selector: "p" })).toBeInTheDocument();
  });

  it("un responsable enregistre une entrée de fonds, sans plafond de montant", async () => {
    const utilisateur = userEvent.setup();
    vi.mocked(api.post).mockResolvedValue({ data: {} });
    render(<CaisseMouvementsPage />);
    await utilisateur.click(await screen.findByRole("button", { name: /Enregistrer une entrée de fonds/ }));

    const fenetre = screen.getByRole("dialog", { name: "Nouvelle entrée de fonds" });
    await utilisateur.type(within(fenetre).getByLabelText(/Motif de l’entrée/), "Apport du patron");
    await utilisateur.type(within(fenetre).getByLabelText(/Provenance/), "Patron");
    await utilisateur.type(within(fenetre).getByLabelText(/Montant/), "900000");
    await utilisateur.click(within(fenetre).getByRole("button", { name: "Confirmer l’entrée" }));

    expect(api.post).toHaveBeenCalledWith("/caisse/mouvements/entrees", { motif: "Apport du patron", beneficiaire: "Patron", montant: 900000 });
    expect(await screen.findByText(/Entrée de fonds enregistrée/)).toBeInTheDocument();
  });

  it("un caissier peut faire une sortie mais ne voit pas le bouton d'entrée de fonds", async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { username: "marie", roles: ["CAISSIER"] }, loading: false, login: vi.fn(), logout: vi.fn() });
    render(<CaisseMouvementsPage />);

    expect(await screen.findByRole("button", { name: /Enregistrer une sortie/ })).toBeEnabled();
    expect(screen.queryByRole("button", { name: /entrée de fonds/ })).not.toBeInTheDocument();
  });

  it("bascule sur le journal de caisse par période, sans le bouton de sortie", async () => {
    const utilisateur = userEvent.setup();
    render(<CaisseMouvementsPage />);
    await screen.findByText("Solde théorique disponible");

    await utilisateur.click(screen.getByRole("tab", { name: "Journal de caisse" }));

    expect(await screen.findByText("Aucune session de caisse n’a été ouverte sur cette période.")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Journal de caisse" })).toHaveAttribute("aria-selected", "true");
    expect(screen.queryByRole("button", { name: /Enregistrer une sortie/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "PDF (imprimer)" })).toBeInTheDocument();

    await utilisateur.click(screen.getByRole("tab", { name: "Session en cours" }));
    expect(await screen.findByText("Solde théorique disponible")).toBeInTheDocument();
  });

  it("garde le journal consultable quand aucune session n'est ouverte aujourd'hui", async () => {
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/referentiels/sessions-caisse/ouverte") throw erreurHttp(404);
      if (url === "/caisse/journal/caissiers") return { data: [] };
      return { data: { debut: "2026-10-09", fin: "2026-10-09", totaux: { nombreSessions: 0, sessionsNonCloturees: 0, nombreVentes: 0, nombreVentesAnnulees: 0, encaissementsEspeces: 0, reglementsClientsEspeces: 0, sorties: 0, especesComptees: 0, ecarts: 0 }, sessions: [] } };
    });
    const utilisateur = userEvent.setup();
    render(<CaisseMouvementsPage />);
    expect(await screen.findByText(/Aucune session de caisse ouverte aujourd’hui/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Enregistrer une sortie/ })).toBeDisabled();

    await utilisateur.click(screen.getByRole("tab", { name: "Journal de caisse" }));
    expect(await screen.findByText("Espèces entrées")).toBeInTheDocument();
  });
});
