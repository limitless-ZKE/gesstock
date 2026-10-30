package com.gesstock.mobile;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/**
 * Depuis Android 15, une application s'affiche sous la barre d'état et sous la barre de navigation : sans décalage, l'en-tête
 * de GesStock passerait sous l'heure et la batterie. La vue racine se décale donc de la hauteur de ces barres et du clavier.
 * On fait de même dès Android 11, pour un rendu identique ; avant, la fenêtre s'arrête d'elle-même aux barres et au clavier.
 */
final class Bords {

    private Bords() {
    }

    static void appliquer(Activity activite, View racine) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sousLesBarres(activite, racine);
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    @SuppressWarnings("deprecation")   // setDecorFitsSystemWindows : inutile mais sans effet à partir d'Android 15, qui l'impose
    private static void sousLesBarres(Activity activite, View racine) {
        activite.getWindow().setDecorFitsSystemWindows(false);
        racine.setOnApplyWindowInsetsListener((vue, encarts) -> {
            Insets bords = encarts.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
            vue.setPadding(bords.left, bords.top, bords.right, bords.bottom);
            return WindowInsets.CONSUMED;
        });
    }
}
