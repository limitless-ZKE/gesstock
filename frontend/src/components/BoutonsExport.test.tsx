import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { telecharger } from "../lib/telechargement";
import { api } from "../services/api";
import { erreurHttp } from "../test/axios";
import BoutonsExport from "./BoutonsExport";

vi.mock("../services/api", () => ({ api: { get: vi.fn() } }));
vi.mock("../lib/telechargement", () => ({ telecharger: vi.fn() }));

const periode = { debut: "2026-10-01", fin: "2026-10-09" };

describe("boutons Excel et PDF d'une période", () => {
  beforeEach(() => {
    vi.mocked(api.get).mockResolvedValue({ data: new Blob(["contenu"]) });
  });

  it("demande le fichier de la période au serveur et l'enregistre sous un nom parlant", async () => {
    const utilisateur = userEvent.setup();
    render(<BoutonsExport chemin="/ventes/historique" periode={periode} nomDuFichier="gesstock-historique-ventes" onErreur={vi.fn()} />);

    await utilisateur.click(screen.getByRole("button", { name: /PDF/ }));
    expect(api.get).toHaveBeenCalledWith("/ventes/historique/pdf", { params: { debut: "2026-10-01", fin: "2026-10-09" }, responseType: "blob" });
    await waitFor(() => expect(telecharger).toHaveBeenCalledWith(expect.any(Blob), "gesstock-historique-ventes-2026-10-01-2026-10-09.pdf"));

    await utilisateur.click(screen.getByRole("button", { name: "Excel" }));
    expect(api.get).toHaveBeenLastCalledWith("/ventes/historique/excel", expect.anything());
    await waitFor(() => expect(telecharger).toHaveBeenLastCalledWith(expect.any(Blob), "gesstock-historique-ventes-2026-10-01-2026-10-09.xlsx"));
  });

  it("bloque les deux boutons pendant la préparation d'un fichier", async () => {
    let terminer: (valeur: { data: Blob }) => void = () => undefined;
    vi.mocked(api.get).mockReturnValue(new Promise((resolve) => { terminer = resolve; }));
    const utilisateur = userEvent.setup();
    render(<BoutonsExport chemin="/caisse/journal" periode={periode} nomDuFichier="x" onErreur={vi.fn()} />);

    await utilisateur.click(screen.getByRole("button", { name: "Excel" }));
    expect(screen.getByRole("button", { name: "Préparation…" })).toBeDisabled();
    expect(screen.getByRole("button", { name: /PDF/ })).toBeDisabled();

    terminer({ data: new Blob(["x"]) });
    await waitFor(() => expect(screen.getByRole("button", { name: "Excel" })).toBeEnabled());
  });

  it("rend le message du serveur, même quand il arrive dans un fichier (téléchargement en erreur)", async () => {
    const erreur = erreurHttp(400, new Blob([JSON.stringify({ detail: "Cette période compte 60000 ventes : c'est trop pour l'imprimer." })], { type: "application/json" }));
    vi.mocked(api.get).mockRejectedValue(erreur);
    const onErreur = vi.fn();
    const utilisateur = userEvent.setup();
    render(<BoutonsExport chemin="/ventes/historique" periode={periode} nomDuFichier="x" onErreur={onErreur} />);

    await utilisateur.click(screen.getByRole("button", { name: /PDF/ }));

    await waitFor(() => expect(onErreur).toHaveBeenLastCalledWith("Cette période compte 60000 ventes : c'est trop pour l'imprimer."));
    expect(onErreur).toHaveBeenNthCalledWith(1, null);   // un nouvel essai efface l'ancien message
    expect(telecharger).not.toHaveBeenCalled();
  });

  it("garde un texte clair quand le serveur ne répond pas du tout", async () => {
    vi.mocked(api.get).mockRejectedValue(new Error("boom"));
    const onErreur = vi.fn();
    const utilisateur = userEvent.setup();
    render(<BoutonsExport chemin="/ventes/historique" periode={periode} nomDuFichier="x" onErreur={onErreur} />);

    await utilisateur.click(screen.getByRole("button", { name: "Excel" }));

    await waitFor(() => expect(onErreur).toHaveBeenLastCalledWith("L’export Excel a échoué."));
  });

  it("reste inactif tant que l'écran n'a pas ses chiffres", () => {
    render(<BoutonsExport chemin="/ventes/historique" periode={periode} nomDuFichier="x" desactive onErreur={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Excel" })).toBeDisabled();
    expect(screen.getByRole("button", { name: /PDF/ })).toBeDisabled();
  });
});
