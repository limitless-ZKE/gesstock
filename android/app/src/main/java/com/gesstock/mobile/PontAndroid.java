package com.gesstock.mobile;

import android.util.Base64;
import android.webkit.JavascriptInterface;

/**
 * L'objet « window.GesStockAndroid » de la page (voir frontend/src/lib/android.ts). Ses appels arrivent sur un fil à part : ils
 * sont renvoyés sur le fil de l'écran, qui vérifie d'abord que la page affichée est bien celle du serveur GesStock.
 */
final class PontAndroid {

    private final MainActivity activite;

    PontAndroid(MainActivity activite) {
        this.activite = activite;
    }

    @JavascriptInterface
    public void enregistrerFichier(String nomDuFichier, String typeMime, String contenuBase64) {
        if (contenuBase64 == null) {
            return;
        }
        byte[] contenu;
        try {
            contenu = Base64.decode(contenuBase64, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            return;
        }
        activite.runOnUiThread(() -> activite.enregistrerFichier(nomDuFichier, typeMime, contenu));
    }

    @JavascriptInterface
    public void imprimer(String titre) {
        activite.runOnUiThread(() -> activite.imprimer(titre));
    }
}
