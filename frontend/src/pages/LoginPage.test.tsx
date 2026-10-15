import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../auth/AuthContext";
import { erreurHttp, erreurReseau } from "../test/axios";
import LoginPage from "./LoginPage";

vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));

const login = vi.fn();

function afficher() {
  vi.mocked(useAuth).mockReturnValue({ user: null, loading: false, login, logout: vi.fn() });
  render(<MemoryRouter><LoginPage /></MemoryRouter>);
  return userEvent.setup();
}

async function seConnecter(utilisateur: ReturnType<typeof userEvent.setup>) {
  await utilisateur.type(screen.getByLabelText("Nom d’utilisateur"), "admin");
  await utilisateur.type(screen.getByLabelText("Mot de passe"), "secret-12345");
  await utilisateur.click(screen.getByRole("button", { name: /Se connecter/ }));
}

describe("page d'accueil (connexion)", () => {
  beforeEach(() => { login.mockReset(); });

  it("souhaite « Bon retour » et présente GesStock, sans ticket de caisse en illustration", () => {
    afficher();

    expect(screen.getByRole("heading", { name: "Bon retour" })).toBeInTheDocument();
    expect(screen.getByText("Connectez-vous à votre espace de gestion.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(/Votre stock,\s*en toute clarté\./);
    expect(screen.queryByText(/Ticket de vente/)).not.toBeInTheDocument();
    expect(screen.queryByText("BOUTIQUE DU MARCHÉ")).not.toBeInTheDocument();
  });

  it("envoie l'identifiant et le mot de passe saisis", async () => {
    login.mockResolvedValue(undefined);
    const utilisateur = afficher();

    await seConnecter(utilisateur);

    await waitFor(() => expect(login).toHaveBeenCalledWith("admin", "secret-12345"));
  });

  it("annonce un identifiant ou un mot de passe incorrect", async () => {
    login.mockRejectedValue(erreurHttp(401));
    const utilisateur = afficher();

    await seConnecter(utilisateur);

    expect(await screen.findByRole("alert")).toHaveTextContent("Identifiant ou mot de passe incorrect.");
  });

  it("annonce un serveur injoignable", async () => {
    login.mockRejectedValue(erreurReseau());
    const utilisateur = afficher();

    await seConnecter(utilisateur);

    expect(await screen.findByRole("alert")).toHaveTextContent("Le serveur GesStock est injoignable");
  });

  it("montre et masque le mot de passe", async () => {
    const utilisateur = afficher();
    const champ = screen.getByLabelText("Mot de passe");
    expect(champ).toHaveAttribute("type", "password");

    await utilisateur.click(screen.getByRole("button", { name: "Afficher le mot de passe" }));

    expect(champ).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Masquer le mot de passe" })).toBeInTheDocument();
  });
});
