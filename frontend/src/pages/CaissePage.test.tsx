import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Link, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../auth/AuthContext";
import { PanierProvider } from "../panier/PanierContext";
import { api } from "../services/api";
import CaissePage from "./CaissePage";

vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));

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
  window.localStorage.clear();
  vi.mocked(useAuth).mockReturnValue({ user: { username: "marie", roles: ["CAISSIER"] }, loading: false, login: vi.fn(), logout: vi.fn() });
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
  render(<PanierProvider><CaissePage /></PanierProvider>);
  await utilisateur.click(await screen.findByRole("button", { name: /Riz 25 kg/ }));
  return utilisateur;
}

/** La caisse et un autre écran, sous le même panier, comme dans l'application : on passe de l'un à l'autre par le menu. */
function afficherAvecAutreEcran() {
  return render(
    <PanierProvider>
      <MemoryRouter initialEntries={["/caisse"]}>
        <Link to="/caisse">Aller à la caisse</Link>
        <Link to="/clients">Aller aux clients</Link>
        <Routes>
          <Route path="/caisse" element={<CaissePage />} />
          <Route path="/clients" element={<p>Écran des clients</p>} />
        </Routes>
      </MemoryRouter>
    </PanierProvider>,
  );
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

  it("accepte une virgule (3 décimales au plus) et ignore les lettres", async () => {
    const utilisateur = await remplirLePanier();

    await utilisateur.clear(champ());
    await utilisateur.type(champ(), "2,5abc");

    expect(champ()).toHaveValue("2,5");
    expect(boutonEncaisser()).toContain("2 500");

    await utilisateur.type(champ(), "1234");
    expect(champ()).toHaveValue("2,512");
  });

  it("envoie la quantité à virgule au serveur", async () => {
    const utilisateur = await remplirLePanier();
    await utilisateur.clear(champ());
    await utilisateur.type(champ(), "0.5");
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));

    await utilisateur.click(screen.getByRole("button", { name: "Valider et encaisser" }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/ventes", expect.objectContaining({
      montantRecu: 500, lignes: [expect.objectContaining({ articleId: 1, quantite: 0.5 })],
    })));
  });

  it("garde les boutons plus et moins", async () => {
    const utilisateur = await remplirLePanier();

    await utilisateur.click(screen.getByRole("button", { name: "Augmenter quantité" }));
    await utilisateur.click(screen.getByRole("button", { name: "Augmenter quantité" }));
    await utilisateur.click(screen.getByRole("button", { name: "Diminuer quantité" }));

    expect(champ()).toHaveValue("2");
  });
});

describe("prix négocié librement par le caissier", () => {
  const boutonEncaisser = () => normaliser(screen.getByRole("button", { name: /^Encaisser/ }).textContent);
  const quantite = () => screen.getByRole("textbox", { name: "Quantité de Riz 25 kg" });

  it("une forte remise par unité part sans demander de responsable", async () => {
    const utilisateur = await remplirLePanier();
    await utilisateur.type(screen.getByLabelText("Prix négocié pour Riz 25 kg"), "400");
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));

    await utilisateur.click(screen.getByRole("button", { name: "Valider et encaisser" }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/ventes/77/validation"));
    expect(api.post).toHaveBeenCalledWith("/ventes", expect.objectContaining({
      lignes: [expect.objectContaining({ prixUnitaire: 400 })],
    }));
    expect(screen.queryByRole("dialog", { name: /accord/i })).not.toBeInTheDocument();
  });

  it("la remise peut porter sur le total de la ligne : ce total est envoyé tel quel", async () => {
    const utilisateur = await remplirLePanier();
    await utilisateur.clear(quantite());
    await utilisateur.type(quantite(), "3");
    await utilisateur.type(screen.getByLabelText("Total négocié pour Riz 25 kg"), "2500");

    expect(boutonEncaisser()).toContain("2 500");
    expect(screen.getByText(/Remise sur la ligne/)).toBeInTheDocument();
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));
    await utilisateur.click(screen.getByRole("button", { name: "Valider et encaisser" }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/ventes", expect.objectContaining({
      montantRecu: 2500,
      lignes: [{ articleId: 1, quantite: 3, promotionId: null, prixUnitaire: null, montantNegocie: 2500 }],
    })));
  });

  it("un total négocié au-dessus du prix catalogue est signalé et rien n'est envoyé", async () => {
    const utilisateur = await remplirLePanier();
    await utilisateur.type(screen.getByLabelText("Total négocié pour Riz 25 kg"), "1500");

    expect(screen.getByRole("alert")).toHaveTextContent(/Ne peut pas dépasser le total au prix catalogue/);
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));
    await utilisateur.click(screen.getByRole("button", { name: "Valider et encaisser" }));
    expect(api.post).not.toHaveBeenCalled();
  });

  it("une vente que seule une promotion fait descendre sous le prix d'achat passe encore, et le caissier en est averti", async () => {
    vi.mocked(api.post).mockImplementation(async (url: string) => {
      if (url === "/ventes") return { data: { id: 77, code: "VTE-77", statut: "BROUILLON" } };
      if (url === "/ventes/77/validation") {
        return { data: { id: 77, code: "VTE-77", statut: "VALIDEE",
          avertissements: ["Vendu sous le prix d'achat — RIZ-25 « Riz 25 kg » : 1 vendu(s) 800, prix d'achat 900"] } };
      }
      throw new Error(`appel inattendu : ${url}`);
    });
    const utilisateur = await remplirLePanier();
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));
    await utilisateur.click(screen.getByRole("button", { name: "Valider et encaisser" }));

    const avertissement = await screen.findByText("Vente enregistrée sous le prix d’achat");
    expect(avertissement.closest("[role=alert]")).toHaveTextContent("RIZ-25 « Riz 25 kg » : 1 vendu(s) 800, prix d'achat 900");
  });
});

describe("panier gardé quand on quitte la caisse", () => {
  it("retrouve les articles, la quantité et le client en revenant sur la caisse", async () => {
    const utilisateur = userEvent.setup();
    afficherAvecAutreEcran();
    await utilisateur.click(await screen.findByRole("button", { name: /Riz 25 kg/ }));
    await utilisateur.click(screen.getByRole("button", { name: "Augmenter quantité" }));
    await choisirLeClient(utilisateur, "fatou", /Fatou Ndiaye/);

    await utilisateur.click(screen.getByRole("link", { name: "Aller aux clients" }));
    expect(screen.getByText("Écran des clients")).toBeInTheDocument();
    await utilisateur.click(screen.getByRole("link", { name: "Aller à la caisse" }));

    expect(await screen.findByLabelText("Quantité de Riz 25 kg")).toHaveValue("2");
    expect(screen.getByRole("combobox", { name: /Client/ })).toHaveValue("Fatou Ndiaye");
    expect(screen.queryByText("Votre panier est vide")).not.toBeInTheDocument();
  });

  it("garde aussi le prix négocié saisi", async () => {
    const utilisateur = userEvent.setup();
    afficherAvecAutreEcran();
    await utilisateur.click(await screen.findByRole("button", { name: /Riz 25 kg/ }));
    await utilisateur.type(screen.getByLabelText("Prix négocié pour Riz 25 kg"), "900");

    await utilisateur.click(screen.getByRole("link", { name: "Aller aux clients" }));
    await utilisateur.click(screen.getByRole("link", { name: "Aller à la caisse" }));

    expect(await screen.findByLabelText("Prix négocié pour Riz 25 kg")).toHaveValue(900);
  });

  it("revient vide une fois la vente encaissée : on ne revend pas deux fois le même panier", async () => {
    const utilisateur = userEvent.setup();
    afficherAvecAutreEcran();
    await utilisateur.click(await screen.findByRole("button", { name: /Riz 25 kg/ }));
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));
    await utilisateur.click(await screen.findByRole("button", { name: "Valider et encaisser" }));
    await screen.findByRole("dialog", { name: "Reçu" });

    await utilisateur.click(screen.getByRole("button", { name: "Fermer le reçu" }));
    await utilisateur.click(screen.getByRole("link", { name: "Aller aux clients" }));
    await utilisateur.click(screen.getByRole("link", { name: "Aller à la caisse" }));

    expect(await screen.findByText("Votre panier est vide")).toBeInTheDocument();
  });

  it("retire d'un panier gardé l'article tombé en rupture et prévient le caissier", async () => {
    window.localStorage.setItem("gesstock:panier:marie", JSON.stringify({
      enregistreLe: Date.now(),
      clientId: "",
      lignes: [{ article: riz, quantite: 2, promotionId: null, prixNegocie: "", totalNegocie: "" }],
    }));
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/articles") return { data: [{ ...riz, quantiteStock: 0 }] };
      if (url === "/caisse/parametres") return { data: { tauxTva: 0, prixTtc: false } };
      if (url === "/articles/categories" || url === "/promotions") return { data: [] };
      if (url === "/clients") return { data: CLIENTS };
      if (url === "/referentiels/sessions-caisse/ouverte") return { data: session };
      throw new Error(`appel inattendu : ${url}`);
    });

    afficherAvecAutreEcran();

    expect(await screen.findByText(/n’est plus disponible : retiré du panier/)).toBeInTheDocument();
    expect(screen.getByText("Votre panier est vide")).toBeInTheDocument();
  });
});

/** Erreur de l'API telle que la voit le front (axios) : statut, code stable et message du serveur. */
const erreurApi = (status: number, code: string, detail: string) =>
  Object.assign(new Error(detail), { isAxiosError: true, response: { status, data: { code, detail }, headers: {} } });

describe("prix négocié sous le prix d'achat : le serveur refuse la validation", () => {
  const REFUS = "Prix négocié sous le prix d'achat : la vente n'est pas validée. Relevez le prix négocié de RIZ-25 « Riz 25 kg » : 400 proposé pour 1, minimum 900";

  /** Chaque encaissement crée un brouillon (77, 78…) ; la validation du 77 est refusée, celle du suivant passe. */
  function serveurQuiRefuseLePremierBrouillon() {
    let dernier = 76;
    vi.mocked(api.post).mockImplementation(async (url: string) => {
      if (url === "/ventes") { dernier += 1; return { data: { id: dernier, code: `VTE-${dernier}`, statut: "BROUILLON" } }; }
      if (url === "/ventes/77/validation") throw erreurApi(409, "PRIX_SOUS_LE_COUT", REFUS);
      if (url === "/ventes/78/validation") return { data: { id: 78, code: "VTE-78", statut: "VALIDEE" } };
      throw new Error(`appel inattendu : ${url}`);
    });
  }
  const brouillonsCrees = () => vi.mocked(api.post).mock.calls.filter(([url]) => url === "/ventes");

  it("referme l'encaissement, garde le panier et dit quel article relever", async () => {
    serveurQuiRefuseLePremierBrouillon();
    const utilisateur = await remplirLePanier();
    await utilisateur.type(screen.getByLabelText("Prix négocié pour Riz 25 kg"), "400");
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));

    await utilisateur.click(screen.getByRole("button", { name: "Valider et encaisser" }));

    expect(await screen.findByText(/minimum 900/)).toHaveTextContent("Le panier est conservé");
    expect(screen.queryByRole("button", { name: "Valider et encaisser" })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Prix négocié pour Riz 25 kg")).toHaveValue(400);
    expect(screen.queryByRole("dialog", { name: "Reçu" })).not.toBeInTheDocument();
  });

  it("après avoir relevé le prix, la vente repart sur un nouveau brouillon et se valide", async () => {
    serveurQuiRefuseLePremierBrouillon();
    const utilisateur = await remplirLePanier();
    const prix = screen.getByLabelText("Prix négocié pour Riz 25 kg");
    await utilisateur.type(prix, "400");
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));
    await utilisateur.click(screen.getByRole("button", { name: "Valider et encaisser" }));
    await screen.findByText(/minimum 900/);

    await utilisateur.clear(prix);
    await utilisateur.type(prix, "900");
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));
    await utilisateur.click(screen.getByRole("button", { name: "Valider et encaisser" }));

    expect(await screen.findByRole("dialog", { name: "Reçu" })).toHaveTextContent("VTE-78");
    expect(brouillonsCrees()).toHaveLength(2);
    expect(brouillonsCrees()[1][1]).toEqual(expect.objectContaining({ lignes: [expect.objectContaining({ prixUnitaire: 900 })] }));
  });
});

describe("brouillon gardé après un échec de validation", () => {
  function validationEnPanneUneFois() {
    let dernier = 76;
    vi.mocked(api.post).mockImplementation(async (url: string) => {
      if (url === "/ventes") { dernier += 1; return { data: { id: dernier, code: `VTE-${dernier}`, statut: "BROUILLON" } }; }
      if (url === "/ventes/77/validation") throw erreurApi(409, "CONFLIT", "Stock insuffisant pour l'article RIZ-25");
      return { data: { id: dernier, code: `VTE-${dernier}`, statut: "VALIDEE" } };
    });
  }
  const brouillonsCrees = () => vi.mocked(api.post).mock.calls.filter(([url]) => url === "/ventes");

  it("est réutilisé tel quel quand rien n'a changé dans le panier", async () => {
    validationEnPanneUneFois();
    const utilisateur = await remplirLePanier();
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));
    await utilisateur.click(screen.getByRole("button", { name: "Valider et encaisser" }));
    await screen.findByText(/Le brouillon reste enregistré/);

    await utilisateur.click(screen.getByRole("button", { name: "Valider et encaisser" }));

    expect(brouillonsCrees()).toHaveLength(1);
    expect(api.post).toHaveBeenCalledWith("/ventes/77/validation");
  });

  it("est remplacé par un nouveau quand le panier a changé : le serveur garde les anciennes lignes du brouillon", async () => {
    validationEnPanneUneFois();
    const utilisateur = await remplirLePanier();
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));
    await utilisateur.click(screen.getByRole("button", { name: "Valider et encaisser" }));
    await screen.findByText(/Le brouillon reste enregistré/);
    await utilisateur.click(screen.getByRole("button", { name: "Annuler" }));

    await utilisateur.click(screen.getByRole("button", { name: "Augmenter quantité" }));
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));
    await utilisateur.click(screen.getByRole("button", { name: "Valider et encaisser" }));

    expect(await screen.findByRole("dialog", { name: "Reçu" })).toHaveTextContent("VTE-78");
    expect(brouillonsCrees()).toHaveLength(2);
    expect(brouillonsCrees()[1][1]).toEqual(expect.objectContaining({ lignes: [expect.objectContaining({ quantite: 2 })] }));
  });
});

describe("alerte dès la saisie d'un prix négocié sous le prix d'achat", () => {
  /** Le catalogue de la caisse, avec le coût moyen que l'API lui envoie déjà pour chaque article. */
  function catalogueAvecCout(coutMoyen: number | null) {
    vi.mocked(api.get).mockImplementation(async (url: string) => {
      if (url === "/caisse/parametres") return { data: { tauxTva: 0, prixTtc: false } };
      if (url === "/articles") return { data: [{ ...riz, coutMoyen }] };
      if (url === "/articles/categories" || url === "/promotions") return { data: [] };
      if (url === "/clients") return { data: CLIENTS };
      if (url === "/referentiels/sessions-caisse/ouverte") return { data: session };
      throw new Error(`appel inattendu : ${url}`);
    });
  }

  it("prévient pendant la frappe et donne le minimum par unité", async () => {
    catalogueAvecCout(700);
    const utilisateur = await remplirLePanier();
    const prix = screen.getByLabelText("Prix négocié pour Riz 25 kg");

    await utilisateur.type(prix, "600");

    expect(await screen.findByRole("alert")).toHaveTextContent(/Prix négocié sous le prix d.achat : minimum 700.* par unité/);
    expect(prix).toBeInvalid();
  });

  it("laisse passer un prix égal au coût, et signale le centime en dessous", async () => {
    catalogueAvecCout(700);
    const utilisateur = await remplirLePanier();
    const prix = screen.getByLabelText("Prix négocié pour Riz 25 kg");

    await utilisateur.type(prix, "699.99");
    expect(await screen.findByRole("alert")).toBeInTheDocument();

    await utilisateur.clear(prix);
    await utilisateur.type(prix, "700");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(prix).toBeValid();
  });

  it("contrôle aussi le total négocié de la ligne, au coût de toutes les unités", async () => {
    catalogueAvecCout(700);
    const utilisateur = await remplirLePanier();
    await utilisateur.click(screen.getByRole("button", { name: "Augmenter quantité" })); // 2 sacs : coût 1 400
    const total = screen.getByLabelText("Total négocié pour Riz 25 kg");

    await utilisateur.type(total, "1300");
    expect(await screen.findByRole("alert")).toHaveTextContent(/Total négocié sous le prix d.achat : minimum 1 400/);

    await utilisateur.clear(total);
    await utilisateur.type(total, "1400");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("n'alerte pas quand le coût est inconnu ou nul, ni quand le prix reste celui du catalogue", async () => {
    catalogueAvecCout(null);
    const utilisateur = await remplirLePanier();
    await utilisateur.type(screen.getByLabelText("Prix négocié pour Riz 25 kg"), "1");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("n'alerte jamais une ligne au prix catalogue, même si le catalogue est sous le coût", async () => {
    catalogueAvecCout(1500);
    const utilisateur = await remplirLePanier();
    await utilisateur.type(screen.getByLabelText("Prix négocié pour Riz 25 kg"), "1000");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("refuse d'ouvrir l'encaissement tant que la ligne est sous le prix d'achat, sans rien envoyer au serveur", async () => {
    catalogueAvecCout(700);
    const utilisateur = await remplirLePanier();
    const prix = screen.getByLabelText("Prix négocié pour Riz 25 kg");
    await utilisateur.type(prix, "600");

    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));

    expect(await screen.findByText(/Corrigez le prix dans le panier avant d.encaisser/)).toHaveTextContent("Riz 25 kg");
    expect(screen.queryByRole("button", { name: "Valider et encaisser" })).not.toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();

    await utilisateur.clear(prix);
    await utilisateur.type(prix, "700");
    await utilisateur.click(screen.getByRole("button", { name: /^Encaisser/ }));
    expect(await screen.findByRole("button", { name: "Valider et encaisser" })).toBeInTheDocument();
  });
});
