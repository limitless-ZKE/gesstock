import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../services/api";
import CaissePage from "./CaissePage";

vi.mock("../services/api", () => ({ api: { get: vi.fn(), post: vi.fn() } }));
vi.mock("../components/ReceiptModal", () => ({
  default: ({ sale, onClose }: { sale: { code: string }; onClose: () => void }) => (
    <div role="dialog" aria-label="Reçu">Reçu de {sale.code}<button onClick={onClose}>Fermer le reçu</button></div>
  ),
}));

const ESPACES = new RegExp(`[${String.fromCharCode(160)}${String.fromCharCode(8239)}]`, "g");
const normaliser = (texte: string | null) => (texte ?? "").replace(ESPACES, " ");

const riz = {
  id: 1, reference: "RIZ-25", designation: "Riz 25 kg", prixVente: 1000, quantiteStock: 40, seuilAlerteStock: 5,
  categorieId: null, categorie: null, uniteMesure: "sac",
};
const CLIENTS = [
  { id: 4, nom: "Fatou Ndiaye", telephone: "780001122" },
  { id: 5, nom: "Boutique Aminata", telephone: "770000000" },
  { id: 6, nom: "Moussa Diallo", telephone: null },
];
const session = {
  id: 1, code: "CAI-1", journee: "2026-10-15", fondDeCaisse: 50000, ouverteLe: "2026-10-15T08:00:00",
  soldeTheorique: 50000, journeeDepassee: false, ecartCloture: null,
};

beforeEach(() => {
  vi.mocked(api.get).mockReset();
  vi.mocked(api.post).mockReset();
  vi.mocked(api.get).mockImplementation(async (url: string) => {
    if (url === "/caisse/parametres") return { data: { tauxTva: 0, prixTtc: false } };
    if (url === "/articles") return { data: [riz] };
    if (url === "/articles/categories") return { data: [] };
    if (url === "/clients") return { data: CLIENTS };
    if (url === "/promotions") return { data: [] };
    if (url === "/referentiels/sessions-caisse/ouverte") return { data: session };
    throw new Error(`appel inattendu : ${url}`);
  });
  vi.mocked(api.post).mockImplementation(async (url: string) => {
    if (url === "/ventes") return { data: { id: 77, code: "VTE-77", statut: "BROUILLON" } };
    if (url === "/ventes/77/validation") return { data: { id: 77, code: "VTE-77", statut: "VALIDEE" } };
    throw new Error(`appel inattendu : ${url}`);
  });
});

/** Comme le caissier : il tape une partie du nom, puis choisit dans la liste réduite. */
async function choisirLeClient(utilisateur: ReturnType<typeof userEvent.setup>, recherche: string, nom: RegExp) {
  await utilisateur.type(screen.getByRole("combobox", { name: /Client/ }), recherche);
  await utilisateur.click(screen.getByRole("option", { name: nom }));
}

async function remplirLePanier() {
  const utilisateur = userEvent.setup();
  render(<CaissePage />);
  await utilisateur.click(await screen.findByRole("button", { name: /Riz 25 kg/ }));
  return utilisateur;
}

describe("vente à crédit à la caisse", () => {
  it("n'est pas proposée pour un client de passage : une dette doit avoir un nom", async () => {
    const utilisateur = await remplirLePanier();

    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));

    expect(screen.getByRole("button", { name: "À crédit" })).toBeDisabled();
    expect(screen.getByText(/Choisissez d’abord un client/)).toBeInTheDocument();
  });

  it("cherche le client en tapant une partie de son nom, sans lire toute la liste", async () => {
    const utilisateur = await remplirLePanier();

    await utilisateur.type(screen.getByRole("combobox", { name: /Client/ }), "moussa");

    const noms = within(screen.getByRole("listbox", { name: "Clients" })).getAllByRole("option").map((option) => option.textContent ?? "");
    expect(noms).toHaveLength(1);
    expect(noms[0]).toContain("Moussa Diallo");
    await utilisateur.click(screen.getByRole("option", { name: /Moussa Diallo/ }));
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));
    expect(screen.getByRole("button", { name: "À crédit" })).toBeEnabled();
  });

  it("enregistre une dette au nom du client choisi, sans mode de règlement ni montant reçu", async () => {
    const utilisateur = await remplirLePanier();
    await choisirLeClient(utilisateur, "amin", /Boutique Aminata/);
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));

    await utilisateur.click(screen.getByRole("button", { name: "À crédit" }));

    expect(screen.queryByLabelText(/Montant reçu|Montant encaissé/)).not.toBeInTheDocument();
    expect(normaliser(screen.getByRole("dialog").textContent)).toContain("Boutique Aminata");
    await utilisateur.click(screen.getByRole("button", { name: "Valider à crédit" }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/ventes/77/validation"));
    expect(api.post).toHaveBeenCalledWith("/ventes", expect.objectContaining({
      aCredit: true, clientId: 5, modeReglement: null, montantRecu: null,
      lignes: [expect.objectContaining({ articleId: 1, quantite: 1 })],
    }));
    expect(await screen.findByText("Reçu de VTE-77")).toBeInTheDocument();
  });

  it("garde le règlement au comptant par défaut : le choix du crédit ne change rien aux autres ventes", async () => {
    const utilisateur = await remplirLePanier();
    await choisirLeClient(utilisateur, "amin", /Boutique Aminata/);
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));

    await utilisateur.click(screen.getByRole("button", { name: "Valider et encaisser" }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/ventes/77/validation"));
    expect(api.post).toHaveBeenCalledWith("/ventes", expect.objectContaining({
      aCredit: false, clientId: 5, modeReglement: "ESPECES", montantRecu: 1000,
    }));
  });
});

describe("quantité saisie directement dans le panier", () => {
  const champ = () => screen.getByRole("textbox", { name: "Quantité de Riz 25 kg" });
  const boutonEncaisser = () => normaliser(screen.getByRole("button", { name: /^Encaisser/ }).textContent);

  it("se remplace en tapant le nombre voulu, et le total suit", async () => {
    const utilisateur = await remplirLePanier();
    expect(champ()).toHaveValue("1");

    await utilisateur.clear(champ());
    await utilisateur.type(champ(), "12");

    expect(champ()).toHaveValue("12");
    expect(boutonEncaisser()).toContain("12 000");
  });

  it("ne dépasse jamais le stock disponible", async () => {
    const utilisateur = await remplirLePanier();

    await utilisateur.clear(champ());
    await utilisateur.type(champ(), "500");

    expect(champ()).toHaveValue("40");
    expect(boutonEncaisser()).toContain("40 000");
  });

  it("revient à la quantité précédente si on laisse le champ vide ou à zéro", async () => {
    const utilisateur = await remplirLePanier();
    await utilisateur.clear(champ());
    await utilisateur.type(champ(), "7");

    await utilisateur.clear(champ());
    await utilisateur.tab();
    expect(champ()).toHaveValue("7");

    await utilisateur.clear(champ());
    await utilisateur.type(champ(), "0");
    await utilisateur.tab();
    expect(champ()).toHaveValue("7");
    expect(boutonEncaisser()).toContain("7 000");
  });

  it("ignore tout ce qui n'est pas un chiffre", async () => {
    const utilisateur = await remplirLePanier();

    await utilisateur.clear(champ());
    await utilisateur.type(champ(), "2,5abc");

    expect(champ()).toHaveValue("25");
  });

  it("garde les boutons plus et moins", async () => {
    const utilisateur = await remplirLePanier();

    await utilisateur.click(screen.getByRole("button", { name: "Augmenter quantité" }));
    await utilisateur.click(screen.getByRole("button", { name: "Augmenter quantité" }));
    await utilisateur.click(screen.getByRole("button", { name: "Diminuer quantité" }));

    expect(champ()).toHaveValue("2");
  });
});
