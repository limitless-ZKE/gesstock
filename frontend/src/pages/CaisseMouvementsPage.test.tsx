import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../auth/AuthContext";
import { api } from "../services/api";
import { erreurHttp } from "../test/axios";
import CaisseMouvementsPage from "./CaisseMouvementsPage";

vi.mock("../services/api", () => ({ api: { get: vi.fn(), post: vi.fn() } }));
vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));

const SESSION = {
  id: 4, code: "CAISSE-1", statut: "OUVERTE", fondDeCaisse: 1000, soldeTheorique: 246000, encaissementsEspeces: 245000, reglementsClientsEspeces: 0, sortiesCaisse: 0,
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
