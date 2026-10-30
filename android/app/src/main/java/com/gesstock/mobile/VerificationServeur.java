package com.gesstock.mobile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Avant de retenir une adresse, l'application vérifie qu'elle mène bien à GesStock : la page d'accueil de son interface porte
 * son nom. Cela distingue un PC éteint ou injoignable (aucune réponse) d'un mauvais numéro de port (un autre serveur répond,
 * par exemple la page de WampServer sur le port 80).
 */
final class VerificationServeur {

    enum Resultat { GESSTOCK, AUTRE_SERVEUR, INJOIGNABLE }

    /** Le nom figure dans l'en-tête de la page : inutile de lire davantage. */
    private static final int TAILLE_LUE = 64 * 1024;

    private VerificationServeur() {
    }

    static Resultat verifier(String adresse, int delaiMillisecondes) {
        HttpURLConnection connexion = null;
        try {
            connexion = (HttpURLConnection) URI.create(adresse).toURL().openConnection();
            connexion.setConnectTimeout(delaiMillisecondes);
            connexion.setReadTimeout(delaiMillisecondes);
            if (connexion.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return Resultat.AUTRE_SERVEUR;
            }
            try (InputStream entree = connexion.getInputStream()) {
                return debut(entree).contains("GesStock") ? Resultat.GESSTOCK : Resultat.AUTRE_SERVEUR;
            }
        } catch (IOException | IllegalArgumentException e) {
            return Resultat.INJOIGNABLE;
        } finally {
            if (connexion != null) {
                connexion.disconnect();
            }
        }
    }

    private static String debut(InputStream entree) throws IOException {
        ByteArrayOutputStream lu = new ByteArrayOutputStream();
        byte[] tampon = new byte[8192];
        int n;
        while (lu.size() < TAILLE_LUE && (n = entree.read(tampon)) != -1) {
            lu.write(tampon, 0, n);
        }
        return new String(lu.toByteArray(), StandardCharsets.UTF_8);
    }
}
