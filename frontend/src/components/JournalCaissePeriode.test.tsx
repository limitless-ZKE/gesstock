import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { telecharger } from "../lib/telechargement";
import { api } from "../services/api";
import { erreurHttp } from "../test/axios";
import JournalCaissePeriode from "./JournalCaissePeriode";

vi.mock("../services/api", () => ({ api: { get: vi.fn() } }));
vi.mock("../lib/telechargement", () => ({ telecharger: vi.fn() }));

const ESPACES = new RegExp(`[${String.fromCharCode(160)}${String.fromCharCode(8239)}]`, "g");
const normaliser = (texte: string | null) => (texte ?? "").replace(ESPACES, " ");

function journee(jour: string, extra: Record<string, unknown> = {}) {
  return {
    sessionId: Number(jour.slice(-2)), code: `CAISSE-${jour}`, journee: jour, statut: "FERMEE", ouvertePar: "marie", fermeePar: "marie",
    ouverteLe: `${jour}T08:00:00`, fermeeLe: `${jour}T19:00:00`, fondDeCaisse: 1000, encaissementsEspeces: 245000, reglementsClientsEspeces: 4000,
    sorties: 5000, soldeTheorique: 245000, montantCloture: 244400, ecart: -600, nombreVentes: 3, nombreVentesAnnulees: 1, ventesACredit: 13000,
    autresEncaissements: [{ mode: "CARTE", montant: 8000 }], journeeDepassee: false,
    ecritures: [
      { date: `${jour}T08:00:00`, type: "OUVERTURE", libelle: "Fond de caisse à l'ouverture", reference: null, utilisateur: "marie", entree: 1000, sortie: null, solde: 1000 },
      { date: `${jour}T10:05:00`, type: "VENTE", libelle: "Vente — client de passage", reference: "VTE-77", utilisateur: "marie", entree: 245000, sortie: null, solde: 246000 },
      { date: `${jour}T11:30:00`, type: "SORTIE", libelle: "Sortie — Achat de sacs (Diop)", reference: null, utilisateur: "marie", entree: null, sortie: 5000, solde: 241000 },
    ],
    ...extra,
  };
}

function journal(sessions: ReturnType<typeof journee>[], totaux = {}) {
  return {
    debut: "2026-10-09", fin: "2026-10-09",
    totaux: {
      nombreSessions: sessions.length, sessionsNonCloturees: 0, nombreVentes: 3, nombreVentesAnnulees: 1, encaissementsEspeces: 245000,
      reglementsClientsEspeces: 4000, sorties: 5000, especesComptees: 244400, ecarts: -600, ...totaux,
    },
    sessions,
  };
}

function afficher() {
  render(<JournalCaissePeriode />);
  return userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
}

describe("journal de caisse par période", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 9, 12, 0));   // vendredi 9 octobre 2026
    vi.mocked(api.get).mockResolvedValue({ data: journal([journee("2026-10-09")]) });
  });
  afterEach(() => vi.useRealTimers());

  it("charge la journée en cours et résume les espèces entrées, les sorties et l'écart", async () => {
    afficher();
    expect(await screen.findByText("Espèces entrées")).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith("/caisse/journal", { params: { debut: "2026-10-09", fin: "2026-10-09" } });
    expect(normaliser(screen.getByText("Espèces entrées").parentElement?.textContent ?? "")).toContain("249 000");   // 245 000 + 4 000
    expect(normaliser(screen.getByText("Sorties de caisse").parentElement?.textContent ?? "")).toContain("5 000");
    const ecart = screen.getByText("Écart de clôture").parentElement as HTMLElement;
    expect(normaliser(ecart.textContent)).toContain("600");
    expect(ecart).toHaveTextContent("espèces manquantes");
  });

  it("montre directement le détail d'une journée unique : chaque écriture avec son solde", async () => {
    afficher();
    await screen.findByText("Vente — client de passage");

    const detail = screen.getByText("Vente — client de passage").closest("table") as HTMLElement;
    expect(within(detail).getByText("Fond de caisse à l'ouverture")).toBeInTheDocument();
    expect(within(detail).getByText("VTE-77")).toBeInTheDocument();
    expect(within(detail).getByText("Sortie — Achat de sacs (Diop)")).toBeInTheDocument();
    expect(normaliser(detail.textContent)).toContain("241 000");   // solde après la sortie
    expect(screen.getByText(/Encaissé hors espèces/)).toHaveTextContent("Carte");
    expect(screen.getByText(/Ventes à crédit/)).toBeInTheDocument();
    expect(screen.getByText("1 vente(s) annulée(s), non comptée(s).")).toBeInTheDocument();
    expect(screen.getByText(/Session CAISSE-2026-10-09 · ouverte par marie à 08:00 · clôturée par marie à 19:00/)).toBeInTheDocument();
  });

  it("sur plusieurs journées, chaque détail se déplie à la demande", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: journal([journee("2026-10-08", { ecart: 0, montantCloture: 245000 }), journee("2026-10-09")], { nombreSessions: 2 }) });
    const utilisateur = afficher();
    await screen.findByText("08/10/2026");
    expect(screen.queryByText("Vente — client de passage")).not.toBeInTheDocument();

    await utilisateur.click(screen.getByRole("button", { name: "Voir le détail du 08/10/2026" }));

    expect(screen.getByText("Vente — client de passage")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Masquer le détail du 08/10/2026" })).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("aucun")).toBeInTheDocument();   // écart nul de la veille
    await utilisateur.click(screen.getByRole("button", { name: "Masquer le détail du 08/10/2026" }));
    expect(screen.queryByText("Vente — client de passage")).not.toBeInTheDocument();
  });

  it("signale une journée non clôturée au lieu d'inventer des espèces comptées", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: journal([journee("2026-10-08", { statut: "OUVERTE", journeeDepassee: true, montantCloture: null, ecart: null, fermeeLe: null, fermeePar: null })],
      { sessionsNonCloturees: 1, especesComptees: 0, ecarts: 0 }) });
    afficher();
    expect(await screen.findByText("Non clôturée")).toBeInTheDocument();
    expect(screen.getByText("dont 1 non clôturée(s)")).toBeInTheDocument();
    const ligne = screen.getByText("08/10/2026").closest("tr") as HTMLElement;
    expect(within(ligne).getAllByText("—").length).toBeGreaterThanOrEqual(2);   // compté et écart
  });

  it("recharge la période choisie", async () => {
    const utilisateur = afficher();
    await screen.findByText("Espèces entrées");

    await utilisateur.click(screen.getByRole("button", { name: "Hier" }));

    await waitFor(() => expect(api.get).toHaveBeenLastCalledWith("/caisse/journal", { params: { debut: "2026-10-08", fin: "2026-10-08" } }));
  });

  it("dit qu'aucune session n'a été ouverte plutôt que d'afficher un tableau vide", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: journal([], { nombreSessions: 0, encaissementsEspeces: 0, reglementsClientsEspeces: 0, sorties: 0, ecarts: 0 }) });
    afficher();
    expect(await screen.findByText("Aucune session de caisse n’a été ouverte sur cette période.")).toBeInTheDocument();
  });

  it("imprime le journal de la période : PDF et Excel du serveur", async () => {
    vi.mocked(api.get).mockImplementation(async (url: string) => (url === "/caisse/journal" ? { data: journal([journee("2026-10-09")]) } : { data: new Blob(["x"]) }));
    const utilisateur = afficher();
    await screen.findByText("Espèces entrées");

    await utilisateur.click(screen.getByRole("button", { name: "Excel" }));

    await waitFor(() => expect(telecharger).toHaveBeenCalledWith(expect.any(Blob), "gesstock-journal-caisse-2026-10-09-2026-10-09.xlsx"));
    expect(api.get).toHaveBeenCalledWith("/caisse/journal/excel", { params: { debut: "2026-10-09", fin: "2026-10-09" }, responseType: "blob" });
  });

  it("annonce l'erreur du serveur", async () => {
    vi.mocked(api.get).mockRejectedValue(erreurHttp(400, { detail: "La période ne peut pas dépasser 366 jours.", code: "REQUETE_INVALIDE" }));
    afficher();
    expect(await screen.findByRole("alert")).toHaveTextContent("La période ne peut pas dépasser 366 jours.");
  });
});
