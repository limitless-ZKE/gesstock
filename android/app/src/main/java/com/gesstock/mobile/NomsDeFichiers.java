package com.gesstock.mobile;

import java.util.function.Predicate;

/** Noms des fichiers enregistrés sur le téléphone (les PDF et Excel préparés par le serveur). */
final class NomsDeFichiers {

    private static final int LONGUEUR_MAX = 120;

    private NomsDeFichiers() {
    }

    /** Un nom sans dossier ni caractère interdit, jamais vide : le nom proposé par la page ne doit rien écrire ailleurs. */
    static String nomSur(String nom) {
        String propre = nom == null ? "" : nom.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        while (propre.startsWith(".")) {
            propre = propre.substring(1);
        }
        if (propre.isEmpty()) {
            return "gesstock";
        }
        // Trop long : on garde la fin, qui porte les dates et l'extension.
        return propre.length() > LONGUEUR_MAX ? propre.substring(propre.length() - LONGUEUR_MAX) : propre;
    }

    /** « journal.pdf » s'il est libre, sinon « journal (1).pdf », « journal (2).pdf »… comme le fait Android 10 et plus. */
    static String nomLibre(String nom, Predicate<String> pris) {
        if (!pris.test(nom)) {
            return nom;
        }
        int point = nom.lastIndexOf('.');
        String base = point > 0 ? nom.substring(0, point) : nom;
        String extension = point > 0 ? nom.substring(point) : "";
        for (int numero = 1; numero < 1000; numero++) {
            String candidat = base + " (" + numero + ")" + extension;
            if (!pris.test(candidat)) {
                return candidat;
            }
        }
        return base + " (" + System.currentTimeMillis() + ")" + extension;
    }
}
