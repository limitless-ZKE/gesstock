import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { useAuth } from "../auth/AuthContext";
import ProtectedRoute from "./ProtectedRoute";

vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));

function afficher(etat: { user: { username: string; roles: string[] } | null; loading: boolean }) {
  vi.mocked(useAuth).mockReturnValue({ ...etat, login: vi.fn(), logout: vi.fn() });
  render(
    <MemoryRouter initialEntries={["/caisse"]}>
      <Routes>
        <Route path="/login" element={<p>Page de connexion</p>} />
        <Route element={<ProtectedRoute />}>
          <Route path="/caisse" element={<p>Contenu protégé</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("ProtectedRoute", () => {
  it("attend la fin de la vérification de session avant de décider", () => {
    afficher({ user: null, loading: true });
    expect(screen.getByText(/Chargement de votre session/)).toBeInTheDocument();
    expect(screen.queryByText("Page de connexion")).not.toBeInTheDocument();
  });

  it("renvoie un visiteur anonyme vers la connexion", () => {
    afficher({ user: null, loading: false });
    expect(screen.getByText("Page de connexion")).toBeInTheDocument();
    expect(screen.queryByText("Contenu protégé")).not.toBeInTheDocument();
  });

  it("laisse passer un utilisateur connecté", () => {
    afficher({ user: { username: "marie", roles: ["CAISSIER"] }, loading: false });
    expect(screen.getByText("Contenu protégé")).toBeInTheDocument();
  });
});
