import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ECHELLE_TICKET, MARGE_INTERIEURE_MM } from "../lib/ticket";
import ZoneImpression from "./ZoneImpression";

const regles = () => document.querySelector(".zone-impression style")!.textContent!;

describe("fenêtre d'impression d'un ticket", () => {
  it("place le ticket directement dans la page, hors de l'application, pour que le reste disparaisse à l'impression", () => {
    const { container } = render(<ZoneImpression cible="ticket"><p id="ticket">Ticket</p></ZoneImpression>);
    const zone = screen.getByText("Ticket").closest(".zone-impression")!;
    expect(zone.parentElement).toBe(document.body);
    expect(container.contains(zone)).toBe(false);
  });

  it("n'imprime que le ticket : le reste de la page est retiré (display: none), pas seulement rendu invisible", () => {
    render(<ZoneImpression cible="ticket"><p id="ticket">Ticket</p></ZoneImpression>);

    expect(regles()).toContain("body > *:not(.zone-impression) { display: none !important; }");
    expect(regles()).not.toContain("visibility");
    expect(regles()).not.toContain("position: fixed");
  });

  it("occupe toute la largeur du papier de 80 mm : page sans marge, ticket à 100 % de la largeur, jamais une largeur fixe qui pourrait déborder", () => {
    render(<ZoneImpression cible="ticket"><p id="ticket">Ticket</p></ZoneImpression>);

    expect(regles()).toContain("@page { size: 80mm auto; margin: 0; }");
    expect(regles()).toMatch(/#ticket \{[^}]*width: 100%; max-width: none; margin: 0;/);
    expect(regles()).not.toMatch(/width: calc\(\d+mm/);
    expect(regles()).toContain(`padding: ${MARGE_INTERIEURE_MM}mm;`);
  });

  it("agrandit les écritures de 25 % au papier, et à l'écran pour le ticket de vente", () => {
    render(<ZoneImpression cible="ticket"><p id="ticket">Ticket</p></ZoneImpression>);

    expect(ECHELLE_TICKET).toBe(1.25);
    expect(regles()).toContain("#ticket { zoom: 1.25; }");                                          // à l'écran
    expect(regles()).toMatch(/@media print \{[\s\S]*#ticket \{ zoom: 1\.25; width: 100%/);          // au papier
  });

  it("n'agrandit pas à l'écran la fenêtre entière du reçu de règlement, seulement au papier", () => {
    render(<ZoneImpression cible="reglement" agrandirALEcran={false}><p id="reglement">Reçu</p></ZoneImpression>);

    expect(regles()).not.toMatch(/^\.zone-impression #reglement/);
    expect(regles().split("@media print")[0]).not.toContain("zoom");
    expect(regles()).toMatch(/@media print \{[\s\S]*#reglement \{ zoom: 1\.25;/);
  });

  it("marque la page pendant que le ticket est affiché, et la libère à la fermeture", () => {
    const { unmount } = render(<ZoneImpression cible="ticket"><p id="ticket">Ticket</p></ZoneImpression>);
    expect(document.documentElement).toHaveClass("impression-ticket");
    unmount();
    expect(document.documentElement).not.toHaveClass("impression-ticket");
    expect(document.querySelector(".zone-impression")).toBeNull();
  });
});
