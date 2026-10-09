/**
 * Dates au format AAAA-MM-JJ (celui de l'API et de <input type="date">), calculées avec l'horloge du poste : la journée d'un
 * commerce est celle que l'on vit, pas celle du méridien de Greenwich (toISOString() donnerait « hier » juste après minuit
 * dès que le fuseau est en avance sur UTC).
 */
export function versJourIso(date: Date): string {
  const mois = String(date.getMonth() + 1).padStart(2, "0");
  const jour = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${mois}-${jour}`;
}

function depuisJourIso(iso: string): Date {
  const [annee, mois, jour] = iso.split("-").map(Number);
  return new Date(annee, mois - 1, jour);
}

export const aujourdhui = () => versJourIso(new Date());

export const hier = () => decalerJour(aujourdhui(), -1);

export const debutDuMois = () => `${aujourdhui().slice(0, 7)}-01`;

export function decalerJour(iso: string, jours: number): string {
  const date = depuisJourIso(iso);
  date.setDate(date.getDate() + jours);
  return versJourIso(date);
}

/** « vendredi 9 octobre 2026 » (la première lettre se met en capitale par la classe CSS first-letter:uppercase). */
export function jourLong(iso: string): string {
  return new Intl.DateTimeFormat("fr-FR", { dateStyle: "full" }).format(depuisJourIso(iso));
}

/** « 09/10/2026 ». */
export function jourCourt(iso: string): string {
  return new Intl.DateTimeFormat("fr-FR").format(depuisJourIso(iso));
}

/** Une journée : « vendredi 9 octobre 2026 » ; plusieurs : « Du 01/10/2026 au 09/10/2026 ». */
export function libellePeriode(debut: string, fin: string): string {
  return debut === fin ? jourLong(debut) : `Du ${jourCourt(debut)} au ${jourCourt(fin)}`;
}
