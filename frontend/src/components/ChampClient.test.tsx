import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import ChampClient from "./ChampClient";

const CLIENTS = [
  { id: 1, nom: "Boutique Aminata", telephone: "77 123 45 67" },
  { id: 2, nom: "Élise Diop", telephone: null },
  { id: 3, nom: "Fatou Ndiaye", telephone: "78 000 11 22" },
  { id: 4, nom: "Amadou Fall", telephone: "70 555 66 77" },
];

/** Le champ est piloté par la page (l'identifiant choisi vit dans la caisse) : on le monte comme elle. */
function Monter({ surChangement }: { surChangement?: (id: string) => void }) {
  const [valeur, setValeur] = useState("");
  return <ChampClient clients={CLIENTS} valeur={valeur} onChange={(id) => { setValeur(id); surChangement?.(id); }} />;
}

const champ = () => screen.getByRole("combobox", { name: /Client/ });
const options = () => screen.queryAllByRole("option").map((option) => option.textContent ?? "");

describe("champ de recherche d'un client", () => {
  it("propose le client de passage tant qu'on n'a rien choisi, sans liste ouverte", () => {
    render(<Monter />);

    expect(champ()).toHaveValue("");
    expect(champ()).toHaveAttribute("placeholder", expect.stringContaining("Client de passage"));
    expect(champ()).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("réduit la liste à mesure qu'on tape une partie du nom", async () => {
    const utilisateur = userEvent.setup();
    render(<Monter />);

    await utilisateur.click(champ());
    expect(options()).toHaveLength(5);   // « Client de passage » + les quatre clients

    await utilisateur.type(champ(), "am");
    expect(options().join("|")).toContain("Boutique Aminata");
    expect(options().join("|")).toContain("Amadou Fall");
    expect(options().join("|")).not.toContain("Fatou");
  });

  it("ignore les majuscules et les accents", async () => {
    const utilisateur = userEvent.setup();
    render(<Monter />);

    await utilisateur.type(champ(), "ELISE");
    expect(options().some((texte) => texte.includes("Élise Diop"))).toBe(true);

    await utilisateur.clear(champ());
    await utilisateur.type(champ(), "élise");
    expect(options().some((texte) => texte.includes("Élise Diop"))).toBe(true);
  });

  it("retrouve aussi un client par son numéro de téléphone", async () => {
    const utilisateur = userEvent.setup();
    render(<Monter />);

    await utilisateur.type(champ(), "78 000");

    expect(options().filter((texte) => !texte.includes("Client de passage"))).toEqual([expect.stringContaining("Fatou Ndiaye")]);
  });

  it("dit quand personne ne correspond", async () => {
    const utilisateur = userEvent.setup();
    render(<Monter />);

    await utilisateur.type(champ(), "xyz");

    expect(screen.getByText(/Aucun client ne correspond à « xyz »/)).toBeInTheDocument();
  });

  it("choisit un client d'un clic : son nom reste dans le champ et la liste se ferme", async () => {
    const surChangement = vi.fn();
    const utilisateur = userEvent.setup();
    render(<Monter surChangement={surChangement} />);

    await utilisateur.type(champ(), "fat");
    await utilisateur.click(screen.getByRole("option", { name: /Fatou Ndiaye/ }));

    expect(surChangement).toHaveBeenLastCalledWith("3");
    expect(champ()).toHaveValue("Fatou Ndiaye");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("se pilote au clavier : flèche bas, Entrée, puis Échap referme", async () => {
    const surChangement = vi.fn();
    const utilisateur = userEvent.setup();
    render(<Monter surChangement={surChangement} />);

    await utilisateur.type(champ(), "am");
    await utilisateur.keyboard("{ArrowDown}{Enter}");
    expect(surChangement).toHaveBeenLastCalledWith(expect.stringMatching(/^(1|4)$/));
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();

    await utilisateur.click(champ());
    expect(screen.getByRole("listbox")).toBeInTheDocument();
    await utilisateur.keyboard("{Escape}");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("revient au client de passage avec la croix ou avec l'entrée « Client de passage »", async () => {
    const surChangement = vi.fn();
    const utilisateur = userEvent.setup();
    render(<Monter surChangement={surChangement} />);
    await utilisateur.type(champ(), "fat");
    await utilisateur.click(screen.getByRole("option", { name: /Fatou Ndiaye/ }));

    await utilisateur.click(screen.getByRole("button", { name: "Retirer le client" }));
    expect(surChangement).toHaveBeenLastCalledWith("");
    expect(champ()).toHaveValue("");
    expect(screen.queryByRole("button", { name: "Retirer le client" })).not.toBeInTheDocument();

    await utilisateur.type(champ(), "amad");
    await utilisateur.click(screen.getByRole("option", { name: /Amadou Fall/ }));
    await utilisateur.click(champ());
    await utilisateur.click(within(screen.getByRole("listbox")).getByRole("option", { name: /Client de passage/ }));
    expect(surChangement).toHaveBeenLastCalledWith("");
  });

  it("n'efface pas le client choisi si l'on quitte le champ sans rien taper", async () => {
    const utilisateur = userEvent.setup();
    render(<Monter />);
    await utilisateur.type(champ(), "fat");
    await utilisateur.click(screen.getByRole("option", { name: /Fatou Ndiaye/ }));

    await utilisateur.click(champ());
    await utilisateur.tab();

    expect(champ()).toHaveValue("Fatou Ndiaye");
  });

  it("limite la liste affichée et invite à préciser la recherche", () => {
    const beaucoup = Array.from({ length: 80 }, (_, i) => ({ id: i + 1, nom: `Client ${String(i + 1).padStart(2, "0")}`, telephone: null }));
    render(<ChampClient clients={beaucoup} valeur="" onChange={() => undefined} />);

    userEvent.setup().click(champ());

    return screen.findByText(/Précisez la recherche/).then(() => {
      expect(options().length).toBeLessThanOrEqual(31);   // « Client de passage » + 30 clients au plus
    });
  });
});
