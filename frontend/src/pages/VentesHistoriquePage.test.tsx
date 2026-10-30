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

const VENTES_SOLDEES = {
  debut: "2026-10-09", fin: "2026-10-09",
  resume: { nombre: 2, montant: 258000, nombreComptant: 1, montantComptant: 245000, nombreCreditsSoldes: 1, montantCreditsSoldes: 13000 },
  ventes: [
    { id: 7, code: "VTE-7", dateVente: "2026-10-09T09:00:00", client: null, vendeur: "marie", aCredit: false, modeReglement: "ESPECES", montantTotal: 245000, soldeeLe: "2026-10-09T09:00:00" },
    { id: 8, code: "VTE-8", dateVente: "2026-10-09T10:00:00", client: "Boutique Aminata", vendeur: "marie", aCredit: true, modeReglement: null, montantTotal: 13000, soldeeLe: "2026-10-09T11:30:00" },
  ],
};

const REGLEMENTS = {
  debut: "2026-10-09", fin: "2026-10-09",
  resume: { nombre: 3, total: 16000, dettesSoldees: 1, parMode: [{ mode: "ESPECES", nombre: 2, montant: 7000 }, { mode: "MOBILE_MONEY", nombre: 1, montant: 9000 }] },
  reglements: [
    { id: 3, dateReglement: "2026-10-09T11:00:00", client: "Boutique Aminata", vente: "VTE-8", montant: 9000, modeReglement: "MOBILE_MONEY", reference: "MM-77", recuPar: "admin", resteApres: 0, detteAnnulee: false },
    { id: 2, dateReglement: "2026-10-09T10:30:00", client: "Boutique Aminata", vente: "VTE-8", montant: 4000, modeReglement: "ESPECES", reference: null, recuPar: "marie", resteApres: 9000, detteAnnulee: false },
    { id: 1, dateReglement: "2026-10-09T08:00:00", client: "Moussa Diop", vente: "VTE-5", montant: 3000, modeReglement: "ESPECES", reference: "R1", recuPar: "marie", resteApres: null, detteAnnulee: true },
  ],
};

describe("historiques par vendeur : toutes les ventes, ventes soldées, règlements de dettes", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 9, 12, 0));
    vi.mocked(telecharger).mockReset();
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/caisse/journal/caissiers") return { data: [{ username: "admin", actif: true }, { username: "marie", actif: true }, { username: "ancien", actif: false }] };
      if (url === "/ventes/historique") return { data: historique([vente(1)]) };
      if (url === "/ventes/historique/soldees") return { data: VENTES_SOLDEES };
      if (url === "/ventes/historique/reglements") return { data: REGLEMENTS };
      if (url.endsWith("/pdf") || url.endsWith("/excel")) return { data: new Blob(["fichier"]) };
      throw new Error(`appel inattendu : ${url}`);
    });
  });
  afterEach(() => vi.useRealTimers());

  it("un responsable choisit le vendeur : ses chiffres à l'écran, et son nom sur le document imprimé", async () => {
    const utilisateur = afficher(["MANAGER"]);
    await screen.findByText("VTE-1");
    expect(screen.getByRole("option", { name: "ancien (désactivé)" })).toBeInTheDocument();

    await utilisateur.selectOptions(screen.getByLabelText("Vendeur"), "marie");

    await waitFor(() => expect(api.get).toHaveBeenLastCalledWith("/ventes/historique", { params: { debut: "2026-10-09", fin: "2026-10-09", vendeur: "marie" } }));
    expect(screen.getByText("Vendeur : marie")).toBeInTheDocument();
    await screen.findByText("VTE-1");
    await utilisateur.click(screen.getByRole("button", { name: /PDF/ }));
    await waitFor(() => expect(telecharger).toHaveBeenCalledWith(expect.any(Blob), "gesstock-historique-ventes-marie-2026-10-09-2026-10-09.pdf"));
    expect(api.get).toHaveBeenCalledWith("/ventes/historique/pdf", { params: { debut: "2026-10-09", fin: "2026-10-09", vendeur: "marie" }, responseType: "blob" });
  });

  it("un caissier ne choisit pas : il voit les siens (le serveur les lui impose) et son nom est sur ce qu'il imprime", async () => {
    const utilisateur = afficher(["CAISSIER"]);
    await screen.findByText("VTE-1");

    expect(screen.queryByLabelText("Vendeur")).not.toBeInTheDocument();
    expect(api.get).not.toHaveBeenCalledWith("/caisse/journal/caissiers");
    expect(screen.getByText("Vos ventes et les règlements que vous avez encaissés")).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith("/ventes/historique", { params: { debut: "2026-10-09", fin: "2026-10-09" } });
    await utilisateur.click(screen.getByRole("button", { name: /PDF/ }));
    await waitFor(() => expect(telecharger).toHaveBeenCalledWith(expect.any(Blob), "gesstock-historique-ventes-u-2026-10-09-2026-10-09.pdf"));
  });

  it("l'onglet « Ventes soldées » : au comptant, ou crédit soldé avec la date du dernier règlement", async () => {
    const utilisateur = afficher();
    await screen.findByText("VTE-1");

    await utilisateur.click(screen.getByRole("tab", { name: "Ventes soldées" }));

    expect(await screen.findByText("VTE-8")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Ventes soldées" })).toHaveAttribute("aria-selected", "true");
    expect(api.get).toHaveBeenCalledWith("/ventes/historique/soldees", { params: { debut: "2026-10-09", fin: "2026-10-09" } });
    expect(within(screen.getByText("VTE-8").closest("tr") as HTMLElement).getByText("Crédit soldé le 09/10/2026")).toBeInTheDocument();
    expect(within(screen.getByText("VTE-7").closest("tr") as HTMLElement).getByText("Espèces")).toBeInTheDocument();
    expect(normaliser(screen.getByText("Au comptant").parentElement?.textContent ?? "")).toContain("245 000");
    expect(screen.getByText("Au comptant").parentElement).toHaveTextContent("1 vente");

    await utilisateur.click(screen.getByRole("button", { name: /Excel/ }));
    await waitFor(() => expect(telecharger).toHaveBeenCalledWith(expect.any(Blob), "gesstock-ventes-soldees-2026-10-09-2026-10-09.xlsx"));
  });

  it("l'onglet « Règlements de dettes » : qui a encaissé, par quel moyen, et ce qui restait dû", async () => {
    const utilisateur = afficher();
    await screen.findByText("VTE-1");

    await utilisateur.click(screen.getByRole("tab", { name: "Règlements de dettes" }));

    expect(await screen.findByText("MM-77")).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith("/ventes/historique/reglements", { params: { debut: "2026-10-09", fin: "2026-10-09" } });
    expect(within(screen.getByText("MM-77").closest("tr") as HTMLElement).getByText("Soldée")).toBeInTheDocument();
    expect(within(screen.getByText("MM-77").closest("tr") as HTMLElement).getByText("Mobile Money")).toBeInTheDocument();
    expect(within(screen.getByText("R1").closest("tr") as HTMLElement).getByText("Dette annulée")).toBeInTheDocument();
    const quatreMille = screen.getAllByRole("row").find((ligne) => ligne.textContent?.includes("Espèces") && !ligne.textContent?.includes("R1"));
    expect(normaliser(quatreMille?.textContent ?? "")).toContain("9 000");
    const parMode = normaliser(screen.getByText("Par moyen de paiement :").parentElement?.textContent ?? "");
    expect(parMode).toContain("Espèces 7 000");
    expect(parMode).toContain("Mobile Money 9 000");

    await utilisateur.type(screen.getByLabelText("Rechercher un règlement"), "diop");
    expect(screen.getByText("R1")).toBeInTheDocument();
    expect(screen.queryByText("MM-77")).not.toBeInTheDocument();
  });

  it("garde la période et le vendeur d'un onglet à l'autre", async () => {
    const utilisateur = afficher(["ADMIN"]);
    await screen.findByText("VTE-1");
    await utilisateur.click(screen.getByRole("button", { name: "Ce mois" }));
    await utilisateur.selectOptions(screen.getByLabelText("Vendeur"), "marie");

    await utilisateur.click(screen.getByRole("tab", { name: "Règlements de dettes" }));

    await waitFor(() => expect(api.get).toHaveBeenLastCalledWith("/ventes/historique/reglements",
      { params: { debut: "2026-10-01", fin: "2026-10-09", vendeur: "marie" } }));
  });
});
