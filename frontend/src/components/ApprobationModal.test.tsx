import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import ApprobationModal from "./ApprobationModal";

function afficher(surcharges: Partial<React.ComponentProps<typeof ApprobationModal>> = {}) {
  const onSoumettre = vi.fn();
  const onAnnuler = vi.fn();
  render(
    <ApprobationModal
      message="Remise de 12 % : au-delà du plafond de 5 % du caissier."
      erreur=""
      occupe={false}
      onSoumettre={onSoumettre}
      onAnnuler={onAnnuler}
      {...surcharges}
    />,
  );
  return { onSoumettre, onAnnuler, utilisateur: userEvent.setup() };
}

describe("ApprobationModal", () => {
  it("explique pourquoi un responsable est demandé", () => {
    afficher();
    expect(screen.getByRole("dialog", { name: "Accord d'un responsable" })).toBeInTheDocument();
    expect(screen.getByText(/au-delà du plafond de 5 %/)).toBeInTheDocument();
  });

  it("n'autorise la validation qu'avec un identifiant ET un mot de passe", async () => {
    const { utilisateur } = afficher({ libelleAction: "Valider la vente" });
    const valider = screen.getByRole("button", { name: "Valider la vente" });
    expect(valider).toBeDisabled();

    await utilisateur.type(screen.getByLabelText("Identifiant du responsable"), "manager");
    expect(valider).toBeDisabled();

    await utilisateur.type(screen.getByLabelText("Mot de passe"), "secret");
    expect(valider).toBeEnabled();
  });

  it("transmet l'identifiant nettoyé et le mot de passe tel quel", async () => {
    const { utilisateur, onSoumettre } = afficher();
    await utilisateur.type(screen.getByLabelText("Identifiant du responsable"), "  manager  ");
    await utilisateur.type(screen.getByLabelText("Mot de passe"), " Mot de passe avec espaces ");
    await utilisateur.click(screen.getByRole("button", { name: "Valider" }));

    expect(onSoumettre).toHaveBeenCalledWith({ username: "manager", motDePasse: " Mot de passe avec espaces " });
  });

  it("montre le refus de la tentative précédente", () => {
    afficher({ erreur: "Identifiants refusés." });
    expect(screen.getByRole("alert")).toHaveTextContent("Identifiants refusés.");
  });

  it("permet d'annuler, et se verrouille pendant la vérification", async () => {
    const { utilisateur, onAnnuler } = afficher();
    await utilisateur.click(screen.getByRole("button", { name: "Annuler" }));
    expect(onAnnuler).toHaveBeenCalledTimes(1);
  });

  it("désactive les boutons pendant la vérification du serveur", () => {
    afficher({ occupe: true });
    expect(screen.getByRole("button", { name: "Annuler" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Vérification…" })).toBeDisabled();
  });
});
