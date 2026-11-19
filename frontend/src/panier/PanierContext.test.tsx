import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../auth/AuthContext";
import {
  DUREE_DE_VIE_MS,
  PanierProvider,
  lirePanier,
  rapprocherDuCatalogue,
  usePanier,
  type Article,
  type CartLine,
} from "./PanierContext";

vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));

const riz: Article = {
  id: 1, reference: "RIZ-25", designation: "Riz 25 kg", prixVente: 1000, quantiteStock: 40, seuilAlerteStock: 5,
  categorieId: null, categorie: null, uniteMesure: "sac",
};
const huile: Article = { ...riz, id: 2, reference: "HUI-5", designation: "Huile 5 l", prixVente: 3500, quantiteStock: 10 };
const ligne = (article: Article, quantite = 1): CartLine => ({ article, quantite, promotionId: null, prixNegocie: "", totalNegocie: "" });

const connecter = (username: string | null) =>
  vi.mocked(useAuth).mockReturnValue({
    user: username ? { username, roles: ["CAISSIER"] } : null, loading: false, login: vi.fn(), logout: vi.fn(),
  });

function Sonde() {
  const { cart, setCart, clientId, setClientId, nombreArticles } = usePanier();
  return (
    <div>
      <p>lignes : {cart.map((entry) => `${entry.article.designation} x${entry.quantite}`).join(", ") || "aucune"}</p>
      <p>client : {clientId || "passage"}</p>
      <p>articles : {nombreArticles}</p>
      <button onClick={() => setCart((current) => [...current, ligne(riz, 2)])}>ajouter du riz</button>
      <button onClick={() => setClientId("4")}>choisir le client 4</button>
      <button onClick={() => setCart([])}>vider</button>
    </div>
  );
}

const monter = () => render(<PanierProvider><Sonde /></PanierProvider>);

describe("PanierProvider", () => {
  beforeEach(() => {
    window.localStorage.clear();
    connecter("marie");
  });

  it("garde le panier et le client d'un montage à l'autre (rechargement de la page)", async () => {
    const utilisateur = userEvent.setup();
    const premier = monter();
    await utilisateur.click(screen.getByRole("button", { name: "ajouter du riz" }));
    await utilisateur.click(screen.getByRole("button", { name: "choisir le client 4" }));
    premier.unmount();

    monter();
    expect(screen.getByText("lignes : Riz 25 kg x2")).toBeInTheDocument();
    expect(screen.getByText("client : 4")).toBeInTheDocument();
    expect(screen.getByText("articles : 2")).toBeInTheDocument();
  });

  it("n'enregistre plus rien quand le panier est vidé", async () => {
    const utilisateur = userEvent.setup();
    monter();
    await utilisateur.click(screen.getByRole("button", { name: "ajouter du riz" }));
    expect(window.localStorage.getItem("gesstock:panier:marie")).not.toBeNull();

    await utilisateur.click(screen.getByRole("button", { name: "vider" }));
    expect(window.localStorage.getItem("gesstock:panier:marie")).toBeNull();
  });

  it("donne à chaque personne connectée son propre panier", async () => {
    const utilisateur = userEvent.setup();
    const vue = monter();
    await utilisateur.click(screen.getByRole("button", { name: "ajouter du riz" }));

    connecter("paul");
    vue.rerender(<PanierProvider><Sonde /></PanierProvider>);
    expect(screen.getByText("lignes : aucune")).toBeInTheDocument();

    connecter("marie");
    vue.rerender(<PanierProvider><Sonde /></PanierProvider>);
    expect(screen.getByText("lignes : Riz 25 kg x2")).toBeInTheDocument();
  });

  it("ne garde rien d'une personne non connectée", async () => {
    connecter(null);
    const utilisateur = userEvent.setup();
    monter();
    await utilisateur.click(screen.getByRole("button", { name: "ajouter du riz" }));

    expect(screen.getByText("lignes : Riz 25 kg x2")).toBeInTheDocument();
    expect(window.localStorage.length).toBe(0);
  });

  it("continue de fonctionner, en mémoire, si le navigateur refuse d'écrire", async () => {
    const ecriture = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("quota"); });
    const utilisateur = userEvent.setup();
    monter();
    await act(async () => { await utilisateur.click(screen.getByRole("button", { name: "ajouter du riz" })); });

    expect(screen.getByText("lignes : Riz 25 kg x2")).toBeInTheDocument();
    ecriture.mockRestore();
  });
});

describe("lirePanier", () => {
  beforeEach(() => window.localStorage.clear());
  const ecrire = (valeur: unknown) => window.localStorage.setItem("gesstock:panier:marie", typeof valeur === "string" ? valeur : JSON.stringify(valeur));

  it("ignore un panier plus vieux que la durée de vie", () => {
    ecrire({ enregistreLe: 1_000, clientId: "", lignes: [ligne(riz)] });
    expect(lirePanier("marie", 1_000 + DUREE_DE_VIE_MS + 1).lignes).toEqual([]);
    expect(lirePanier("marie", 1_000 + DUREE_DE_VIE_MS).lignes).toHaveLength(1);
  });

  it("repart d'un panier vide si le contenu est abîmé", () => {
    ecrire("{pas du json");
    expect(lirePanier("marie")).toEqual({ lignes: [], clientId: "" });
  });

  it("écarte les lignes mal formées sans perdre les bonnes", () => {
    ecrire({ enregistreLe: Date.now(), clientId: "7", lignes: [ligne(riz, 3), { article: null }, { ...ligne(huile), quantite: -1 }, "x"] });
    const lu = lirePanier("marie");
    expect(lu.lignes).toHaveLength(1);
    expect(lu.lignes[0].quantite).toBe(3);
    expect(lu.clientId).toBe("7");
  });
});

describe("rapprocherDuCatalogue", () => {
  it("laisse intact un panier toujours valide", () => {
    const { lignes, messages } = rapprocherDuCatalogue([ligne(riz, 2)], [riz, huile]);
    expect(messages).toEqual([]);
    expect(lignes).toEqual([ligne(riz, 2)]);
  });

  it("retire l'article supprimé du catalogue ou en rupture", () => {
    const { lignes, messages } = rapprocherDuCatalogue([ligne(riz), ligne(huile)], [{ ...huile, quantiteStock: 0 }]);
    expect(lignes).toEqual([]);
    expect(messages).toHaveLength(2);
    expect(messages[0]).toContain("Riz 25 kg");
    expect(messages[1]).toContain("Huile 5 l");
  });

  it("ramène la quantité au stock disponible", () => {
    const { lignes, messages } = rapprocherDuCatalogue([ligne(huile, 8)], [{ ...huile, quantiteStock: 3 }]);
    expect(lignes[0].quantite).toBe(3);
    expect(messages[0]).toContain("ramenée à 3");
  });

  it("prend le prix et le stock du moment, en gardant la négociation saisie", () => {
    const negocie: CartLine = { ...ligne(riz, 2), prixNegocie: "900" };
    const { lignes, messages } = rapprocherDuCatalogue([negocie], [{ ...riz, prixVente: 1200 }]);
    expect(lignes[0].article.prixVente).toBe(1200);
    expect(lignes[0].prixNegocie).toBe("900");
    expect(messages[0]).toContain("a changé");
  });
});
