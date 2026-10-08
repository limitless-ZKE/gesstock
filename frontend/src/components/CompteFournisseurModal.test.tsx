import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { money } from "../lib/format";
import { api } from "../services/api";
import { erreurHttp } from "../test/axios";
import CompteFournisseurModal from "./CompteFournisseurModal";

vi.mock("../services/api", () => ({ api: { get: vi.fn(), post: vi.fn() } }));

/** Intl met des espaces insécables dans les montants : on compare sur des espaces ordinaires. */
const montant = (valeur: number) => money.format(valeur).replace(/[\u00a0\u202f]/g, " ");
const texteMontant = (valeur: number) => (contenu: string) => contenu.replace(/[\u00a0\u202f]/g, " ") === montant(valeur);

const COMPTE = {
  fournisseurId: 7,
  nom: "Grossiste Dakar",
  soldeDu: 30000,
  creditDisponible: 0,
  dettes: [{
    id: 11, dateCreation: "2026-10-01", approvisionnementId: 5, numeroBon: "BR-1",
    montantInitial: 50000, montantRegle: 20000, solde: 30000, statut: "PARTIELLEMENT_REGLEE",
    reglements: [{
      id: 1, detteId: 11, montant: 20000, source: "PAIEMENT", modeReglement: "VIREMENT", reference: "VIR-1",
      dateReglement: "2026-10-02T10:00:00", effectuePar: "admin", avoirId: null,
    }],
  }],
  avoirs: [],
};

function afficher(options: { canCreateCredit?: boolean } = {}) {
  const onChanged = vi.fn();
  const onClose = vi.fn();
  render(<CompteFournisseurModal supplier={{ id: 7, nom: "Grossiste Dakar" }} canCreateCredit={options.canCreateCredit ?? false} onClose={onClose} onChanged={onChanged} />);
  return { onChanged, onClose, utilisateur: userEvent.setup() };
}

describe("CompteFournisseurModal", () => {
  beforeEach(() => {
    vi.mocked(api.get).mockReset().mockResolvedValue({ data: COMPTE });
    vi.mocked(api.post).mockReset().mockResolvedValue({ data: {} });
  });

  it("montre ce que l'on doit et la dette de chaque bon", async () => {
    afficher();
    expect(await screen.findByRole("dialog", { name: "Compte de Grossiste Dakar" })).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith("/fournisseurs/7/compte");

    expect(await screen.findAllByText(texteMontant(30000))).not.toHaveLength(0);
    expect(screen.getByText("Nous lui devons")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /BR-1/ })).toBeInTheDocument();
    expect(screen.getByText("Partielle")).toBeInTheDocument();
  });

  it("déplie les règlements d'un bon", async () => {
    const { utilisateur } = afficher();
    await utilisateur.click(await screen.findByRole("button", { name: /BR-1/ }));
    expect(screen.getByText("par admin")).toBeInTheDocument();
    expect(screen.getByText("réf. VIR-1")).toBeInTheDocument();
    expect(screen.getByText(texteMontant(20000), { selector: "span" })).toBeInTheDocument();
  });

  it("paie le solde d'une dette : envoie montant, mode et référence puis rafraîchit", async () => {
    const { utilisateur, onChanged } = afficher();
    await utilisateur.click(await screen.findByRole("button", { name: "Payer" }));

    const champMontant = screen.getByLabelText("Montant (XOF)");
    expect(champMontant).toHaveValue(30000);   // proposé : le solde entier

    await utilisateur.selectOptions(screen.getByLabelText("Mode de règlement"), "ESPECES");
    expect(screen.getByText(/L’argent sort de la caisse/)).toBeInTheDocument();

    await utilisateur.click(screen.getByRole("button", { name: /Enregistrer le paiement/ }));

    expect(api.post).toHaveBeenCalledWith("/fournisseurs/dettes/11/reglements", { montant: 30000, modeReglement: "ESPECES", reference: null });
    expect(await screen.findByRole("status")).toHaveTextContent("Paiement de");
    expect(onChanged).toHaveBeenCalledTimes(1);
    expect(api.get).toHaveBeenCalledTimes(2);   // chargement initial + rafraîchissement
  });

  it("permet un paiement partiel avec une référence", async () => {
    const { utilisateur } = afficher();
    await utilisateur.click(await screen.findByRole("button", { name: "Payer" }));
    const champMontant = screen.getByLabelText("Montant (XOF)");
    await utilisateur.clear(champMontant);
    await utilisateur.type(champMontant, "12000");
    await utilisateur.type(screen.getByLabelText("Référence (facultatif)"), "CHQ-77");
    await utilisateur.click(screen.getByRole("button", { name: /Enregistrer le paiement/ }));

    expect(api.post).toHaveBeenCalledWith("/fournisseurs/dettes/11/reglements", { montant: 12000, modeReglement: "VIREMENT", reference: "CHQ-77" });
  });

  it("affiche le refus du serveur sans rafraîchir ni prévenir la liste", async () => {
    vi.mocked(api.post).mockRejectedValue(erreurHttp(409, { detail: "Une session de caisse ouverte est requise pour enregistrer une sortie" }));
    const { utilisateur, onChanged } = afficher();
    await utilisateur.click(await screen.findByRole("button", { name: "Payer" }));
    await utilisateur.selectOptions(screen.getByLabelText("Mode de règlement"), "ESPECES");
    await utilisateur.click(screen.getByRole("button", { name: /Enregistrer le paiement/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent("session de caisse ouverte est requise");
    expect(onChanged).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Montant (XOF)")).toBeInTheDocument();   // le formulaire reste ouvert pour corriger
  });

  it("ne propose la saisie d'un avoir qu'aux responsables", async () => {
    afficher({ canCreateCredit: false });
    await screen.findByRole("button", { name: "Payer" });
    expect(screen.queryByRole("button", { name: /Saisir un avoir/ })).not.toBeInTheDocument();
  });

  it("enregistre un avoir saisi par un responsable", async () => {
    const { utilisateur, onChanged } = afficher({ canCreateCredit: true });
    await utilisateur.click(await screen.findByRole("button", { name: /Saisir un avoir/ }));
    await utilisateur.type(screen.getByLabelText("Montant (XOF)"), "1500");
    await utilisateur.type(screen.getByLabelText("Motif"), "Ristourne de fin d’année");
    await utilisateur.click(screen.getByRole("button", { name: /Enregistrer l’avoir/ }));

    expect(api.post).toHaveBeenCalledWith("/fournisseurs/7/avoirs", { montant: 1500, motif: "Ristourne de fin d’année" });
    await waitFor(() => expect(onChanged).toHaveBeenCalledTimes(1));
  });

  it("affiche le crédit disponible et son effet sur les prochains bons", async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: {
        ...COMPTE, soldeDu: 0, creditDisponible: 500, dettes: [],
        avoirs: [{ id: 3, dateCreation: "2026-10-03T09:00:00", montant: 4000, montantUtilise: 3500, restant: 500, motif: "Retour RF-1", creePar: "manager" }],
      },
    });
    afficher();
    expect(await screen.findByText(/déduit automatiquement de ses prochains bons/)).toBeInTheDocument();
    const ligne = screen.getByText("Retour RF-1").closest("tr")!;
    expect(within(ligne).getByText(texteMontant(500))).toBeInTheDocument();
    expect(screen.getByText(/Aucune dette/)).toBeInTheDocument();
  });

  it("signale un compte impossible à charger", async () => {
    vi.mocked(api.get).mockRejectedValue(erreurHttp(500, { detail: "Erreur interne" }));
    afficher();
    expect(await screen.findByRole("alert")).toHaveTextContent("Erreur interne");
  });
});
