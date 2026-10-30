/**
 * Pont avec l'application Android GesStock : elle affiche cette interface dans une vue web et y ajoute l'objet
 * `window.GesStockAndroid`. Une vue web Android n'imprime pas (`window.print()` n'y fait rien) et ne sait pas enregistrer un
 * fichier préparé dans la page : ces deux gestes passent donc par l'application. Dans un navigateur, l'objet n'existe pas.
 */
export interface PontAndroid {
  /** Enregistre le fichier dans « Téléchargements/GesStock » du téléphone, puis l'ouvre. */
  enregistrerFichier(nomDuFichier: string, typeMime: string, contenuBase64: string): void;
  /** Ouvre la fenêtre d'impression d'Android sur la page affichée (styles d'impression compris). */
  imprimer(titre: string): void;
}

export function pontAndroid(): PontAndroid | null {
  return (window as Window & { GesStockAndroid?: PontAndroid }).GesStockAndroid ?? null;
}
