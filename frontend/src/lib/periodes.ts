import { decalerJour, jourCourt, versJourIso } from "./dates";

/** Les périodes proposées pour classer une liste par date (bons d'approvisionnement…). */
export type ChoixPeriode = "all" | "today" | "week" | "month" | "lastMonth" | "custom";

export const CHOIX_PERIODES: { valeur: ChoixPeriode; libelle: string }[] = [
  { valeur: "all", libelle: "Toutes les dates" },
  { valeur: "today", libelle: "Aujourd’hui" },
  { valeur: "week", libelle: "Cette semaine" },
  { valeur: "month", libelle: "Ce mois" },
  { valeur: "lastMonth", libelle: "Le mois dernier" },
  { valeur: "custom", libelle: "Dates précises…" },
];

/** Premier et dernier jour retenus (AAAA-MM-JJ, inclus) ; une borne absente est ouverte : « à partir du… » ou « jusqu'au… ». */
export type Bornes = { debut: string | null; fin: string | null };

function decouper(iso: string): [number, number, number] {
  const [annee, mois, jour] = iso.split("-").map(Number);
  return [annee, mois, jour];
}

/** 0 = lundi … 6 = dimanche : la semaine commence le lundi. */
function rangDansLaSemaine(iso: string): number {
  const [annee, mois, jour] = decouper(iso);
  return (new Date(annee, mois - 1, jour).getDay() + 6) % 7;
}

/**
 * Les bornes d'un choix de période, calculées avec le jour d'aujourd'hui. « Cette semaine » va du lundi au dimanche et « Ce mois » du
 * 1er au dernier jour du mois : un bon daté de demain, dans la même semaine ou le même mois, n'en sort pas.
 */
export function bornesDePeriode(choix: ChoixPeriode, aujourdhui: string, debutPerso = "", finPerso = ""): Bornes {
  const [annee, mois] = decouper(aujourdhui);
  switch (choix) {
    case "all":
      return { debut: null, fin: null };
    case "today":
      return { debut: aujourdhui, fin: aujourdhui };
    case "week": {
      const lundi = decalerJour(aujourdhui, -rangDansLaSemaine(aujourdhui));
      return { debut: lundi, fin: decalerJour(lundi, 6) };
    }
    case "month":
      return { debut: `${aujourdhui.slice(0, 7)}-01`, fin: versJourIso(new Date(annee, mois, 0)) };
    case "lastMonth": {
      const dernierJour = versJourIso(new Date(annee, mois - 1, 0));
      return { debut: `${dernierJour.slice(0, 7)}-01`, fin: dernierJour };
    }
    case "custom":
      return { debut: debutPerso || null, fin: finPerso || null };
  }
}

export function dansLesBornes(date: string, bornes: Bornes): boolean {
  return (!bornes.debut || date >= bornes.debut) && (!bornes.fin || date <= bornes.fin);
}

/** « Du 01/11/2026 au 30/11/2026 », « À partir du 01/11/2026 », « Jusqu'au 30/11/2026 » ; vide quand il n'y a aucune borne. */
export function libelleBornes(bornes: Bornes): string {
  if (bornes.debut && bornes.fin) return bornes.debut === bornes.fin ? `Le ${jourCourt(bornes.debut)}` : `Du ${jourCourt(bornes.debut)} au ${jourCourt(bornes.fin)}`;
  if (bornes.debut) return `À partir du ${jourCourt(bornes.debut)}`;
  if (bornes.fin) return `Jusqu’au ${jourCourt(bornes.fin)}`;
  return "";
}
