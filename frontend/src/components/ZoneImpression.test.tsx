import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import ZoneImpression from "./ZoneImpression";

describe("fenêtre d'impression d'un ticket", () => {
  it("place le ticket directement dans la page, hors de l'application, pour que le reste disparaisse à l'impression", () => {
    const { container } = render(<ZoneImpression cible="ticket" largeurMm={72}><p id="ticket">Ticket</p></ZoneImpression>);
    const zone = screen.getByText("Ticket").closest(".zone-impression")!;
    expect(zone.parentElement).toBe(document.body);
    expect(container.contains(zone)).toBe(false);
  });

  it("n'imprime que le ticket : le reste de la page est retiré (display: none), pas seulement rendu invisible", () => {
    render(<ZoneImpression cible="ticket" largeurMm={72}><p id="ticket">Ticket</p></ZoneImpression>);
    const regles = document.querySelector(".zone-impression style")!.textContent!;
    expect(regles).toContain("body > *:not(.zone-impression) { display: none !important; }");
    expect(regles).not.toContain("visibility");
    expect(regles).not.toContain("position: fixed");
    expect(regles).toContain("#ticket { width: 72mm;");
  });

  it("marque la page pendant que le ticket est affiché, et la libère à la fermeture", () => {
    const { unmount } = render(<ZoneImpression cible="ticket" largeurMm={80}><p id="ticket">Ticket</p></ZoneImpression>);
    expect(document.documentElement).toHaveClass("impression-ticket");
    unmount();
    expect(document.documentElement).not.toHaveClass("impression-ticket");
    expect(document.querySelector(".zone-impression")).toBeNull();
  });
});
