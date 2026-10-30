package com.gesstock.mobile;

import android.annotation.TargetApi;
import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Enregistre un fichier reçu de la page dans « Téléchargements/GesStock », là où l'application Fichiers le retrouve. */
final class Fichiers {

    static final String DOSSIER = "GesStock";

    private Fichiers() {
    }

    /** L'adresse du fichier enregistré, que les autres applications (lecteur de PDF, tableur) peuvent ouvrir. */
    static Uri enregistrer(Context contexte, String nom, String type, byte[] contenu) throws IOException {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ? dansLesTelechargements(contexte, nom, type, contenu)
            : dansLesTelechargementsAndroid9(contexte, nom, type, contenu);
    }

    /** Android 10 et plus : par le stockage partagé, sans permission ; un nom déjà pris reçoit « (1) ». */
    @TargetApi(Build.VERSION_CODES.Q)
    private static Uri dansLesTelechargements(Context contexte, String nom, String type, byte[] contenu) throws IOException {
        ContentResolver resolveur = contexte.getContentResolver();
        ContentValues valeurs = new ContentValues();
        valeurs.put(MediaStore.MediaColumns.DISPLAY_NAME, nom);
        valeurs.put(MediaStore.MediaColumns.MIME_TYPE, type);
        valeurs.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + DOSSIER);
        valeurs.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri fichier = resolveur.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valeurs);
        if (fichier == null) {
            throw new IOException("dossier Téléchargements inaccessible");
        }
        try (OutputStream sortie = resolveur.openOutputStream(fichier)) {
            if (sortie == null) {
                throw new IOException("écriture impossible");
            }
            sortie.write(contenu);
        } catch (IOException | RuntimeException e) {
            resolveur.delete(fichier, null, null);
            throw e;
        }
        valeurs.clear();
        valeurs.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolveur.update(fichier, valeurs, null, null);
        return fichier;
    }

    /** Android 8 et 9 : écriture directe dans le dossier (permission demandée avant), puis déclaration aux téléchargements. */
    @SuppressWarnings("deprecation")
    private static Uri dansLesTelechargementsAndroid9(Context contexte, String nom, String type, byte[] contenu) throws IOException {
        File dossier = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DOSSIER);
        if (!dossier.isDirectory() && !dossier.mkdirs()) {
            throw new IOException("dossier " + dossier + " impossible à créer");
        }
        File fichier = new File(dossier, NomsDeFichiers.nomLibre(nom, candidat -> new File(dossier, candidat).exists()));
        try (FileOutputStream sortie = new FileOutputStream(fichier)) {
            sortie.write(contenu);
        }
        DownloadManager telechargements = (DownloadManager) contexte.getSystemService(Context.DOWNLOAD_SERVICE);
        long numero = telechargements.addCompletedDownload(fichier.getName(), contexte.getString(R.string.app_name), true, type,
            fichier.getAbsolutePath(), fichier.length(), true);
        Uri adresse = telechargements.getUriForDownloadedFile(numero);
        if (adresse == null) {
            throw new IOException("fichier enregistré mais introuvable : " + fichier);
        }
        return adresse;
    }
}
