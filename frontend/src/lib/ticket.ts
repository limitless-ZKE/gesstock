/**
 * Taille des tickets (reçu de vente, reçu de règlement) sur le papier de caisse de 80 mm.
 *
 * Le ticket occupe TOUTE la largeur du papier (marge de page à 0, largeur 100 %) : le texte va de 2,5 mm à 77,5 mm. Les écritures sont
 * agrandies de 25 % par rapport à la première version (zoom CSS) : montants, désignations, lignes de détail et logo.
 * L'échelle ne change pas la largeur du ticket : elle ne fait que passer plus tôt à la ligne les désignations longues.
 *
 * Si l'imprimante rogne les bords du papier (zone non imprimable de l'imprimante), augmenter `MARGE_INTERIEURE_MM` d'un millimètre à la
 * fois ; si les écritures sont encore trop petites ou trop grandes, ne modifier que `ECHELLE_TICKET`.
 */
export const ECHELLE_TICKET = 1.25;

/** Marge entre le bord du papier et le texte, en millimètres AVANT agrandissement (2 mm deviennent 2,5 mm sur le papier). */
export const MARGE_INTERIEURE_MM = 2;
