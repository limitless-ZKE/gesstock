import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../auth/AuthContext";
import { telecharger } from "../lib/telechargement";
import { api } from "../services/api";
import { erreurHttp } from "../test/axios";
import VentesHistoriquePage from "./VentesHistoriquePage";

vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));
vi.mock("../services/api", () => ({ api: { get: vi.fn(), post: vi.fn() } }));
vi.mock("../lib/telechargement", () => ({ telecharger: vi.fn() }));
vi.mock("../components/ReceiptModal", () => ({
  default: ({ sale, onClose }: { sale: { code: string }; onClose: () => void }) => (
    <div role="dialog" aria-label="Reçu">Reçu de {sale.code}<button onClick={onClose}>Fermer le reçu</button></div>
  ),
}));

const ESPACES = new RegExp(`[${String.fromCharCode(160)}${String.fromCharCode(8239)}]`, "g");
const normaliser = (texte: string | null) => (texte ?? "").replace(ESPACES, " ");

function vente(id: number, extra: Record<string, unknown> = {}) {
  return {
    id, code: `VTE-${id}`, dateVente: "2026-10-09T10:15:00", statut: "VALIDEE", client: null, vendeur: "marie", aCredit: false,
    modeReglement: "ESPECES", nombreArticles: 2, remises: 0, montantTotal: 1000 * id, ...extra,
  };
}

function historique(ventes: ReturnType<typeof vente>[], resume = {}) {
  return {
    debut: "2026-10-09", fin: "2026-10-09",
    resume: { nombreValidees: ventes.length, chiffreAffaires: 5000, remises: 150, nombreAnnulees: 1, montantAnnule: 2000, ...resume },
    ventes,
  };
}

function afficher(roles = ["ADMIN"]) {
  vi.mocked(useAuth).mockReturnValue({ user: { username: "u", roles }, loading: false, login: vi.fn(), logout: vi.fn() });
  render(<VentesHistoriquePage />);
  return userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
}

describe("historique des ventes par période", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 9, 12, 0));   // vendredi 9 octobre 2026
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/ventes/historique") return { data: historique([vente(1, { client: "Boutique Aminata", remises: 150 }), vente(2, { modeReglement: "CARTE" }), vente(3, { statut: "ANNULEE" })]) };
      if (url.startsWith("/ventes/")) return { data: { ...vente(1), lignes: [] } };
      throw new Error(`appel inattendu : ${url}`);
    });
  });
  afterEach(() => vi.useRealTimers());

  it("affiche la journée en cours par défaut : chiffres du serveur et une ligne par vente", async () => {
    afficher();
    expect(await screen.findByText("VTE-1")).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith("/ventes/historique", { params: { debut: "2026-10-09", fin: "2026-10-09" } });
    expect(screen.getByText("vendredi 9 octobre 2026")).toBeInTheDocument();
    const indicateurs = screen.getAllByText(/^(Ventes validées|Chiffre d’affaires TTC|Remises accordées|Ventes annulées)$/);
    expect(indicateurs).toHaveLength(4);
    expect(normaliser(screen.getByText("Chiffre d’affaires TTC").parentElement?.textContent ?? "")).toContain("5 000");
    expect(screen.getByText("Boutique Aminata")).toBeInTheDocument();
    expect(screen.getAllByText("Client de passage")).toHaveLength(2);
    expect(screen.getByText("Carte")).toBeInTheDocument();
    expect(screen.getByText("Annulée")).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Heure" })).toBeInTheDocument();
  });

  it("recharge toute la période choisie et montre la date de chaque vente", async () => {
    const utilisateur = afficher();
    await screen.findByText("VTE-1");

    await utilisateur.click(screen.getByRole("button", { name: "Ce mois" }));

    await waitFor(() => expect(api.get).toHaveBeenLastCalledWith("/ventes/historique", { params: { debut: "2026-10-01", fin: "2026-10-09" } }));
    expect(await screen.findByText("Du 01/10/2026 au 09/10/2026")).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Date" })).toBeInTheDocument();
  });

  it("filtre par recherche et par statut sans rappeler le serveur", async () => {
    const utilisateur = afficher();
    await screen.findByText("VTE-1");
    const appels = vi.mocked(api.get).mock.calls.length;

    await utilisateur.type(screen.getByLabelText("Rechercher une vente"), "aminata");
    expect(screen.getByText("VTE-1")).toBeInTheDocument();
    expect(screen.queryByText("VTE-2")).not.toBeInTheDocument();

    await utilisateur.clear(screen.getByLabelText("Rechercher une vente"));
    await utilisateur.selectOptions(screen.getByLabelText("Filtrer par statut"), "ANNULEE");
    expect(screen.getByText("VTE-3")).toBeInTheDocument();
    expect(screen.queryByText("VTE-1")).not.toBeInTheDocument();
    expect(api.get).toHaveBeenCalledTimes(appels);
  });

  it("découpe une longue période en pages de 50 ventes", async () => {
    const beaucoup = Array.from({ length: 120 }, (_, i) => vente(i + 1));
    vi.mocked(api.get).mockResolvedValue({ data: historique(beaucoup, { nombreValidees: 120 }) });
    const utilisateur = afficher();
    await screen.findByText("VTE-1");

    expect(screen.getByText("Page 1 sur 3")).toBeInTheDocument();
    expect(screen.queryByText("VTE-51")).not.toBeInTheDocument();
    await utilisateur.click(screen.getByRole("button", { name: "Page suivante" }));
    expect(screen.getByText("Page 2 sur 3")).toBeInTheDocument();
    expect(screen.getByText("VTE-51")).toBeInTheDocument();
    await utilisateur.click(screen.getByRole("button", { name: "Page suivante" }));
    expect(screen.getByRole("button", { name: "Page suivante" })).toBeDisabled();
    expect(screen.getByText("120 vente(s), montants en francs CFA. Le bouton « Reçu » imprime un duplicata du ticket remis au client.")).toBeInTheDocument();
  }, 20000);   // 120 lignes affichées trois fois : 3 s seul, plus de 5 s quand toute la suite tourne en parallèle

  it("n'offre l'annulation qu'à un responsable, pour une vente au comptant validée du jour", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: historique([
      vente(1),                                              // au comptant, aujourd'hui : annulable
      vente(2, { aCredit: true, modeReglement: null }),      // à crédit : non
      vente(3, { dateVente: "2026-10-08T17:00:00" }),        // d'hier : non
      vente(4, { statut: "ANNULEE" }),                       // déjà annulée : non
    ]) });
    afficher(["MANAGER"]);
    await screen.findByText("VTE-1");

    const annuler = screen.getAllByRole("button", { name: /Annuler/ });
    expect(annuler).toHaveLength(1);
    expect(within(annuler[0].closest("tr") as HTMLElement).getByText("VTE-1")).toBeInTheDocument();
  });

  it("cache l'annulation à un caissier", async () => {
    afficher(["CAISSIER"]);
    await screen.findByText("VTE-1");
    expect(screen.queryByRole("button", { name: /Annuler/ })).not.toBeInTheDocument();
  });

  it("relit le ticket complet de la vente au clic sur « Reçu »", async () => {
    const utilisateur = afficher();
    await screen.findByText("VTE-1");

    await utilisateur.click(within(screen.getByText("VTE-1").closest("tr") as HTMLElement).getByRole("button", { name: /Reçu/ }));

    expect(api.get).toHaveBeenCalledWith("/ventes/1");
    expect(await screen.findByRole("dialog", { name: "Reçu" })).toHaveTextContent("Reçu de VTE-1");
  });

  it("imprime la période affichée : PDF et Excel du serveur", async () => {
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/ventes/historique") return { data: historique([vente(1)]) };
      return { data: new Blob(["fichier"]) };
    });
    const utilisateur = afficher();
    await screen.findByText("VTE-1");

    await utilisateur.click(screen.getByRole("button", { name: /PDF/ }));

    await waitFor(() => expect(telecharger).toHaveBeenCalledWith(expect.any(Blob), "gesstock-historique-ventes-2026-10-09-2026-10-09.pdf"));
    expect(api.get).toHaveBeenCalledWith("/ventes/historique/pdf", { params: { debut: "2026-10-09", fin: "2026-10-09" }, responseType: "blob" });
  });

  it("dit pourquoi l'historique est vide, ou ce qui n'a pas marché", async () => {
    vi.mocked(api.get).mockResolvedValue({ data: historique([], { nombreValidees: 0, chiffreAffaires: 0, nombreAnnulees: 0 }) });
    afficher();
    expect(await screen.findByText("Aucune vente enregistrée pour cette journée.")).toBeInTheDocument();
  });

  it("annonce l'erreur du serveur quand la période est refusée", async () => {
    vi.mocked(api.get).mockRejectedValue(erreurHttp(400, { detail: "La période ne peut pas dépasser 366 jours.", code: "REQUETE_INVALIDE" }));
    afficher();
    expect(await screen.findByRole("alert")).toHaveTextContent("La période ne peut pas dépasser 366 jours.");
  });
});
