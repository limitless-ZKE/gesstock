import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../auth/AuthContext";
import { telecharger } from "../lib/telechargement";
import { api } from "../services/api";
import { erreurHttp } from "../test/axios";
import JournalCaisse from "./JournalCaisse";

vi.mock("../services/api", () => ({ api: { get: vi.fn() } }));
vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));
vi.mock("../lib/telechargement", () => ({ telecharger: vi.fn() }));

const ESPACES = new RegExp(`[${String.fromCharCode(160)}${String.fromCharCode(8239)}]`, "g");
const normaliser = (texte: string | null) => (texte ?? "").replace(ESPACES, " ");

function jour(date: string, extra: Record<string, unknown> = {}) {
  return {
    sessionId: Number(date.slice(-2)), code: `CAISSE-${date}`, journee: date, statut: "FERMEE",
    ventesEspeces: 245000, reglementsEspeces: 4000, sorties: 5000, especesARemettre: 244000,
    nombreVentes: 3, nombreVentesAnnulees: 1, ventesACredit: 13000, autresEncaissements: [{ mode: "CARTE", montant: 8000 }],
    ecritures: [
      { date: `${date}T10:05:00`, type: "VENTE", libelle: "Vente — client de passage", reference: "VTE-77", entree: 245000, sortie: null, cumul: 245000 },
      { date: `${date}T10:40:00`, type: "REGLEMENT", libelle: "Règlement de dette — Boutique Aminata", reference: "REC-1", entree: 4000, sortie: null, cumul: 249000 },
      { date: `${date}T11:30:00`, type: "SORTIE", libelle: "Sortie — Achat de sacs (Diop)", reference: null, entree: null, sortie: 5000, cumul: 244000 },
    ],
    ...extra,
  };
}

function journalDe(caissier: string, jours: ReturnType<typeof jour>[], debut = "2026-10-09", fin = "2026-10-09") {
  return {
    caissier, debut, fin,
    totaux: {
      nombreJours: jours.length, nombreVentes: 3 * jours.length, nombreVentesAnnulees: jours.length, ventesEspeces: 245000 * jours.length,
      reglementsEspeces: 4000 * jours.length, sorties: 5000 * jours.length, especesARemettre: 244000 * jours.length,
      autresEncaissements: 8000 * jours.length, ventesACredit: 13000 * jours.length,
    },
    jours,
  };
}

const journalComplet = {
  debut: "2026-10-09", fin: "2026-10-09",
  totaux: { nombreSessions: 0, sessionsNonCloturees: 0, nombreVentes: 0, nombreVentesAnnulees: 0, encaissementsEspeces: 0, reglementsClientsEspeces: 0, sorties: 0, especesComptees: 0, ecarts: 0 },
  sessions: [],
};
/** Le caissier demandé à l'API, quand l'écran en nomme un. */
const caissierDemande = (config?: { params?: unknown }) => (config?.params as { caissier?: string } | undefined)?.caissier;

const COMPTES = [{ username: "admin", actif: true }, { username: "marie", actif: true }, { username: "paul", actif: false }];

function seConnecterEn(...roles: string[]) {
  vi.mocked(useAuth).mockReturnValue({ user: { username: "marie", roles }, loading: false, login: vi.fn(), logout: vi.fn() });
}

describe("journal de caisse : chacun voit ce qui le concerne", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 9, 12, 0));   // vendredi 9 octobre 2026
    vi.mocked(api.get).mockReset();
    vi.mocked(telecharger).mockReset();
    vi.mocked(api.get).mockImplementation(async (url: string, config?: { params?: unknown }) => {
      if (url === "/caisse/journal/caissier") return { data: journalDe(caissierDemande(config) ?? "marie", [jour("2026-10-09")]) };
      if (url === "/caisse/journal/caissiers") return { data: COMPTES };
      if (url === "/caisse/journal") return { data: journalComplet };
      if (url === "/caisse/journal/caissier/pdf") return { data: new Blob(["%PDF-"]) };
      throw new Error(`appel inattendu : ${url}`);
    });
  });
  afterEach(() => vi.useRealTimers());

  function afficher() {
    render(<JournalCaisse />);
    return userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  }

  describe("un caissier", () => {
    beforeEach(() => seConnecterEn("CAISSIER"));

    it("lit son propre journal, sans choisir de caissier, et jamais le journal complet", async () => {
      afficher();

      expect(await screen.findByRole("heading", { name: "Mon journal de caisse" })).toBeInTheDocument();
      expect(api.get).toHaveBeenCalledWith("/caisse/journal/caissier", { params: { debut: "2026-10-09", fin: "2026-10-09" } });
      expect(api.get).not.toHaveBeenCalledWith("/caisse/journal", expect.anything());
      expect(api.get).not.toHaveBeenCalledWith("/caisse/journal/caissiers");
      expect(screen.queryByLabelText("Caissier")).not.toBeInTheDocument();
    });

    it("voit ses espèces à remettre et le détail de ses lignes, sans le fond de caisse ni le solde du tiroir", async () => {
      afficher();
      await screen.findByText("Vente — client de passage");

      expect(normaliser(screen.getByText("Espèces à remettre", { selector: "p, span, div" }).parentElement?.textContent ?? "")).toContain("244 000");
      const detail = screen.getByText("Vente — client de passage").closest("table") as HTMLElement;
      expect(within(detail).getByText("VTE-77")).toBeInTheDocument();
      expect(within(detail).getByText("Règlement de dette — Boutique Aminata")).toBeInTheDocument();
      expect(within(detail).getByText("Sortie — Achat de sacs (Diop)")).toBeInTheDocument();
      expect(normaliser(detail.textContent)).toContain("244 000");   // cumul après la sortie
      expect(screen.getByText(/Encaissé hors espèces/)).toHaveTextContent("Carte");
      expect(screen.getByText(/Ventes à crédit/)).toBeInTheDocument();
      const ecran = normaliser(document.body.textContent);
      expect(ecran).not.toMatch(/Fond de caisse|Solde théorique|Écart de clôture|Espèces comptées/);
    });

    it("imprime son journal en PDF ou Excel, avec son nom dans le fichier", async () => {
      const utilisateur = afficher();
      await screen.findByText("Vente — client de passage");

      await utilisateur.click(screen.getByRole("button", { name: "PDF (imprimer)" }));

      await waitFor(() => expect(telecharger).toHaveBeenCalled());
      expect(api.get).toHaveBeenCalledWith("/caisse/journal/caissier/pdf", { params: { debut: "2026-10-09", fin: "2026-10-09" }, responseType: "blob" });
      expect(vi.mocked(telecharger).mock.calls[0][1]).toBe("gesstock-journal-caissier-marie-2026-10-09-2026-10-09.pdf");
    });

    it("dit qu'il n'a rien fait plutôt que d'afficher un tableau vide", async () => {
      vi.mocked(api.get).mockResolvedValue({ data: journalDe("marie", []) });
      afficher();

      expect(await screen.findByText("Vous n’avez effectué aucune opération sur cette période.")).toBeInTheDocument();
    });

    it("annonce l'échec du chargement", async () => {
      vi.mocked(api.get).mockRejectedValue(erreurHttp(500, { detail: "Erreur interne." }));
      afficher();

      expect(await screen.findByRole("alert")).toBeInTheDocument();
    });
  });

  describe.each([["ADMIN"], ["MANAGER"], ["GESTIONNAIRE"]])("un responsable (%s)", (role) => {
    beforeEach(() => seConnecterEn(role));

    it("choisit le caissier dans une liste et garde le journal complet par défaut", async () => {
      afficher();

      const liste = await screen.findByLabelText("Caissier");
      expect(api.get).toHaveBeenCalledWith("/caisse/journal", { params: { debut: "2026-10-09", fin: "2026-10-09" } });
      await waitFor(() => expect(within(liste).getAllByRole("option")).toHaveLength(4));
      expect(within(liste).getAllByRole("option").map((option) => option.textContent)).toEqual([
        "Tous : journal complet de la journée", "admin", "marie", "paul (désactivé)",
      ]);
    });
  });

  describe("un administrateur", () => {
    beforeEach(() => seConnecterEn("ADMIN"));

    it("imprime le journal d'un caissier précis, en gardant la période choisie", async () => {
      const utilisateur = afficher();
      const liste = await screen.findByLabelText("Caissier");
      await waitFor(() => expect(within(liste).getAllByRole("option")).toHaveLength(4));
      await utilisateur.click(screen.getByRole("button", { name: "Ce mois" }));
      await waitFor(() => expect(api.get).toHaveBeenCalledWith("/caisse/journal", { params: { debut: "2026-10-01", fin: "2026-10-09" } }));

      await utilisateur.selectOptions(liste, "marie");

      expect(await screen.findByRole("heading", { name: "Journal de caisse de marie" })).toBeInTheDocument();
      expect(api.get).toHaveBeenLastCalledWith("/caisse/journal/caissier", { params: { debut: "2026-10-01", fin: "2026-10-09", caissier: "marie" } });
      await screen.findByText("Vente — client de passage");

      await utilisateur.click(screen.getByRole("button", { name: "PDF (imprimer)" }));

      await waitFor(() => expect(telecharger).toHaveBeenCalled());
      expect(api.get).toHaveBeenCalledWith("/caisse/journal/caissier/pdf",
        { params: { debut: "2026-10-01", fin: "2026-10-09", caissier: "marie" }, responseType: "blob" });
      expect(vi.mocked(telecharger).mock.calls[0][1]).toBe("gesstock-journal-caissier-marie-2026-10-01-2026-10-09.pdf");
    });

    it("revient au journal complet avec « Tous »", async () => {
      const utilisateur = afficher();
      const liste = await screen.findByLabelText("Caissier");
      await waitFor(() => expect(within(liste).getAllByRole("option")).toHaveLength(4));
      await utilisateur.selectOptions(liste, "marie");
      await screen.findByRole("heading", { name: "Journal de caisse de marie" });

      await utilisateur.selectOptions(liste, "");

      expect(await screen.findByRole("heading", { name: "Journal de caisse" })).toBeInTheDocument();
      expect(api.get).toHaveBeenLastCalledWith("/caisse/journal", { params: { debut: "2026-10-09", fin: "2026-10-09" } });
    });

    it("nomme le caissier quand il n'a rien fait sur la période", async () => {
      vi.mocked(api.get).mockImplementation(async (url: string, config?: { params?: unknown }) => {
        if (url === "/caisse/journal/caissiers") return { data: COMPTES };
        if (url === "/caisse/journal/caissier") return { data: journalDe(caissierDemande(config) ?? "", []) };
        return { data: journalComplet };
      });
      const utilisateur = afficher();
      const liste = await screen.findByLabelText("Caissier");
      await waitFor(() => expect(within(liste).getAllByRole("option")).toHaveLength(4));

      await utilisateur.selectOptions(liste, "paul");

      expect(await screen.findByText("paul n’a effectué aucune opération sur cette période.")).toBeInTheDocument();
    });
  });
});
