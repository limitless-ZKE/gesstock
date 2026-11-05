/** Ce que doit un client : le total de ses dettes non réglées, et depuis quand (la plus ancienne). */
export type DetteClientResume = { du: number; plusAncienne: string; jours: number; nombre: number };

type DetteLue = { clientId: number; dateCreation: string; solde: number; statut: string };

const MS_PAR_JOUR = 24 * 60 * 60 * 1000;

/** Nombre de jours entre deux dates « AAAA-MM-JJ » (lues en UTC : pas de décalage d'heure d'été ou de fuseau). */
export function joursEntre(debut: string, fin: string): number {
  return Math.round((Date.parse(fin) - Date.parse(debut)) / MS_PAR_JOUR);
}

/** Pour chaque client qui doit de l'argent : son total dû, sa plus ancienne dette et son ancienneté en jours. Les dettes soldées ou annulées ne comptent pas. */
export function dettesParClient(dettes: DetteLue[], aujourdhui: string): Map<number, DetteClientResume> {
  const resumes = new Map<number, DetteClientResume>();
  for (const dette of dettes) {
    if (dette.solde <= 0 || dette.statut === "ANNULEE") continue;
    const courant = resumes.get(dette.clientId);
    const plusAncienne = courant && courant.plusAncienne <= dette.dateCreation ? courant.plusAncienne : dette.dateCreation;
    resumes.set(dette.clientId, {
      du: (courant?.du ?? 0) + dette.solde,
      plusAncienne,
      jours: joursEntre(plusAncienne, aujourdhui),
      nombre: (courant?.nombre ?? 0) + 1,
    });
  }
  return resumes;
}

export type FiltreClient = "all" | "withDebt" | "noDebt" | "old30" | "old60" | "old90";

/** Les filtres proposés, avec ce qu'il faut demander au serveur pour imprimer la même liste (voir ClientExportService). */
export const FILTRES_CLIENTS: Record<FiltreClient, { libelle: string; filtre: string; jours?: number; nomDuFichier: string }> = {
  all: { libelle: "Tous les clients", filtre: "tous", nomDuFichier: "gesstock-clients-tous" },
  withDebt: { libelle: "Avec une dette en cours", filtre: "avec-dette", nomDuFichier: "gesstock-clients-avec-dette" },
  noDebt: { libelle: "Sans dette", filtre: "sans-dette", nomDuFichier: "gesstock-clients-sans-dette" },
  old30: { libelle: "Dette de plus de 30 jours", filtre: "dette-ancienne", jours: 30, nomDuFichier: "gesstock-clients-dette-ancienne-30j" },
  old60: { libelle: "Dette de plus de 60 jours", filtre: "dette-ancienne", jours: 60, nomDuFichier: "gesstock-clients-dette-ancienne-60j" },
  old90: { libelle: "Dette de plus de 90 jours", filtre: "dette-ancienne", jours: 90, nomDuFichier: "gesstock-clients-dette-ancienne-90j" },
};

/** Le client est-il retenu par ce filtre ? Pour « dette de plus de N jours » : sa dette la plus ancienne a au moins N jours. */
export function retenirClient(filtre: FiltreClient, resume: DetteClientResume | undefined): boolean {
  const jours = FILTRES_CLIENTS[filtre].jours;
  if (filtre === "all") return true;
  if (filtre === "withDebt") return resume !== undefined;
  if (filtre === "noDebt") return resume === undefined;
  return resume !== undefined && jours !== undefined && resume.jours >= jours;
}

/** Les filtres de dette classent du plus gros débiteur au plus petit, comme la liste imprimée. */
export function trieParDette(filtre: FiltreClient): boolean {
  return filtre !== "all" && filtre !== "noDebt";
}
