import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider, useAuth } from "./AuthContext";
import * as authService from "./authService";

vi.mock("./authService");

function Sonde() {
  const { user, loading, login, logout } = useAuth();
  return (
    <div>
      <p>{loading ? "chargement" : user ? `connecté : ${user.username} (${user.roles.join(",")})` : "anonyme"}</p>
      <button onClick={() => void login("admin", "secret")}>se connecter</button>
      {/* signOut peut échouer (réseau) : l'écran, lui, doit quand même oublier l'utilisateur */}
      <button onClick={() => logout().catch(() => undefined)}>se déconnecter</button>
    </div>
  );
}

const afficher = () => render(<AuthProvider><Sonde /></AuthProvider>);

describe("AuthProvider", () => {
  beforeEach(() => {
    vi.mocked(authService.getCurrentUser).mockReset();
    vi.mocked(authService.signIn).mockReset();
    vi.mocked(authService.signOut).mockReset();
  });

  it("reprend la session existante au chargement", async () => {
    vi.mocked(authService.getCurrentUser).mockResolvedValue({ username: "marie", roles: ["MANAGER"] });
    afficher();
    expect(screen.getByText("chargement")).toBeInTheDocument();
    expect(await screen.findByText("connecté : marie (MANAGER)")).toBeInTheDocument();
  });

  it("reste anonyme quand il n'y a pas de session", async () => {
    vi.mocked(authService.getCurrentUser).mockRejectedValue(new Error("401"));
    afficher();
    expect(await screen.findByText("anonyme")).toBeInTheDocument();
  });

  it("oublie l'utilisateur quand le serveur répond 401 plus tard (session expirée)", async () => {
    vi.mocked(authService.getCurrentUser).mockResolvedValue({ username: "marie", roles: ["CAISSIER"] });
    afficher();
    await screen.findByText("connecté : marie (CAISSIER)");

    act(() => { window.dispatchEvent(new CustomEvent("gesstock:unauthorized")); });

    expect(await screen.findByText("anonyme")).toBeInTheDocument();
  });

  it("connecte avec les identifiants saisis", async () => {
    vi.mocked(authService.getCurrentUser).mockRejectedValue(new Error("401"));
    vi.mocked(authService.signIn).mockResolvedValue({ username: "admin", roles: ["ADMIN"] });
    afficher();
    await screen.findByText("anonyme");

    await userEvent.setup().click(screen.getByText("se connecter"));

    expect(authService.signIn).toHaveBeenCalledWith("admin", "secret");
    expect(await screen.findByText("connecté : admin (ADMIN)")).toBeInTheDocument();
  });

  it("déconnecte côté écran même si le serveur ne répond pas", async () => {
    vi.mocked(authService.getCurrentUser).mockResolvedValue({ username: "marie", roles: ["ADMIN"] });
    vi.mocked(authService.signOut).mockRejectedValue(new Error("réseau"));
    afficher();
    await screen.findByText("connecté : marie (ADMIN)");

    await userEvent.setup().click(screen.getByText("se déconnecter"));

    await waitFor(() => expect(screen.getByText("anonyme")).toBeInTheDocument());
  });
});

describe("useAuth", () => {
  it("refuse de fonctionner hors du fournisseur", () => {
    const erreurs = vi.spyOn(console, "error").mockImplementation(() => undefined);
    expect(() => render(<Sonde />)).toThrow("AuthProvider");
    erreurs.mockRestore();
  });
});
