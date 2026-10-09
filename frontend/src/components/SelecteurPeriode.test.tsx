import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import SelecteurPeriode, { type Periode } from "./SelecteurPeriode";

function Essai({ initiale, onChange }: { initiale: Periode; onChange: (periode: Periode) => void }) {
  const [periode, setPeriode] = useState(initiale);
  return <SelecteurPeriode periode={periode} onChange={(choisie) => { setPeriode(choisie); onChange(choisie); }} />;
}

describe("sélecteur de période", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 9, 10, 0));   // vendredi 9 octobre 2026
  });
  afterEach(() => vi.useRealTimers());

  const jour = { debut: "2026-10-09", fin: "2026-10-09" };

  it.each([
    ["aujourd’hui", { debut: "2026-10-09", fin: "2026-10-09" }, "Aujourd’hui"],
    ["hier", { debut: "2026-10-08", fin: "2026-10-08" }, "Hier"],
    ["le mois en cours", { debut: "2026-10-01", fin: "2026-10-09" }, "Ce mois"],
    ["des dates libres", { debut: "2026-09-12", fin: "2026-09-20" }, "Personnalisée"],
  ])("reconnaît %s comme raccourci de départ", (_nom, initiale, bouton) => {
    render(<Essai initiale={initiale} onChange={vi.fn()} />);
    const enfonces = screen.getAllByRole("button").filter((b) => b.getAttribute("aria-pressed") === "true");
    expect(enfonces.map((b) => b.textContent)).toEqual([bouton]);
    expect(screen.getByLabelText("Du")).toHaveValue(initiale.debut);
    expect(screen.getByLabelText("Au")).toHaveValue(initiale.fin);
  });

  it("applique tout de suite hier et le mois en cours, comme l'écran des statistiques", async () => {
    const onChange = vi.fn();
    const utilisateur = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<Essai initiale={jour} onChange={onChange} />);

    await utilisateur.click(screen.getByRole("button", { name: "Hier" }));
    expect(onChange).toHaveBeenLastCalledWith({ debut: "2026-10-08", fin: "2026-10-08" });
    expect(screen.getByLabelText("Au")).toHaveValue("2026-10-08");

    await utilisateur.click(screen.getByRole("button", { name: "Ce mois" }));
    expect(onChange).toHaveBeenLastCalledWith({ debut: "2026-10-01", fin: "2026-10-09" });

    await utilisateur.click(screen.getByRole("button", { name: "Aujourd’hui" }));
    expect(onChange).toHaveBeenLastCalledWith(jour);
    expect(onChange).toHaveBeenCalledTimes(3);
  });

  it("n'applique des dates précises qu'au clic sur le bouton, et passe en « Personnalisée »", async () => {
    const onChange = vi.fn();
    const utilisateur = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<Essai initiale={jour} onChange={onChange} />);

    await utilisateur.clear(screen.getByLabelText("Du"));
    await utilisateur.type(screen.getByLabelText("Du"), "2026-09-15");
    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Personnalisée" })).toHaveAttribute("aria-pressed", "true");

    await utilisateur.click(screen.getByRole("button", { name: "Afficher" }));
    expect(onChange).toHaveBeenCalledWith({ debut: "2026-09-15", fin: "2026-10-09" });
  });

  it("refuse une fin antérieure au début, avec une explication, sans rien demander au serveur", async () => {
    const onChange = vi.fn();
    const utilisateur = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<Essai initiale={{ debut: "2026-10-01", fin: "2026-10-09" }} onChange={onChange} />);

    const fin = screen.getByLabelText("Au");
    await utilisateur.clear(fin);
    await utilisateur.type(fin, "2026-09-20");
    await utilisateur.click(screen.getByRole("button", { name: "Afficher" }));

    expect(screen.getByRole("alert")).toHaveTextContent("La date de fin doit être égale ou postérieure à la date de début.");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("refuse un dernier jour dans le futur", async () => {
    const onChange = vi.fn();
    const utilisateur = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<Essai initiale={jour} onChange={onChange} />);

    await utilisateur.clear(screen.getByLabelText("Au"));
    await utilisateur.type(screen.getByLabelText("Au"), "2026-12-25");
    await utilisateur.click(screen.getByRole("button", { name: "Afficher" }));

    expect(screen.getByRole("alert")).toHaveTextContent("Le dernier jour ne peut pas être dans le futur.");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("ne propose aucune date future", () => {
    render(<Essai initiale={jour} onChange={vi.fn()} />);
    expect(screen.getByLabelText("Au")).toHaveAttribute("max", "2026-10-09");
    expect(screen.getByLabelText("Du")).toHaveAttribute("max", "2026-10-09");
  });
});
