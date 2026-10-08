package com.gesstock.gesstock.entreprise.service;

import com.gesstock.gesstock.common.exception.RequeteInvalideException;

import java.util.Locale;

/**
 * Les formats sous lesquels le logo est servi. Chaque endroit où il s'affiche demande le sien : le ticket thermique 80 mm n'a
 * pas besoin des mêmes pixels qu'un en-tête A4, et l'écran d'administration se contente d'une miniature. Les bornes sont des
 * MAXIMUMS : on réduit en conservant les proportions, on n'agrandit jamais (un petit logo reste net).
 */
public enum TailleLogo {

    /** Aperçus et listes à l'écran. */
    MINIATURE(160, 80),
    /** Ticket thermique 80 mm (zone imprimable de 72 mm, environ 576 points à 203 dpi : le logo en occupe à peu près la moitié). */
    TICKET(320, 160),
    /** En-tête de document A4 (60 mm de large à 300 dpi, environ 700 px). */
    ENTETE(720, 300),
    /** Logo tel que stocké, borné à {@link TraitementLogo#COTE_MAX_STOCKE} px de côté. */
    ORIGINAL(TraitementLogo.COTE_MAX_STOCKE, TraitementLogo.COTE_MAX_STOCKE);

    private final int largeurMax;
    private final int hauteurMax;

    TailleLogo(int largeurMax, int hauteurMax) {
        this.largeurMax = largeurMax;
        this.hauteurMax = hauteurMax;
    }

    public int largeurMax() {
        return largeurMax;
    }

    public int hauteurMax() {
        return hauteurMax;
    }

    public static TailleLogo depuisParametre(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return ENTETE;
        }
        try {
            return valueOf(valeur.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new RequeteInvalideException("Taille de logo inconnue : « " + valeur + " » (miniature, ticket, entete ou original)");
        }
    }
}
