package com.gesstock.mobile;

import android.content.Context;

/** Ce que l'application retient d'un lancement à l'autre : l'adresse du serveur, rien d'autre. */
final class Reglages {

    private static final String FICHIER = "reglages";
    private static final String CLE_ADRESSE = "adresse_serveur";

    private Reglages() {
    }

    /** L'adresse de base du serveur (« http://192.168.1.75:8080/ »), ou {@code null} tant qu'elle n'a pas été réglée. */
    static String adresse(Context contexte) {
        return contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).getString(CLE_ADRESSE, null);
    }

    static void enregistrerAdresse(Context contexte, String adresse) {
        contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().putString(CLE_ADRESSE, adresse).apply();
    }
}
