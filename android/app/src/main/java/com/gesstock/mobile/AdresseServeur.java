package com.gesstock.mobile;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * L'adresse du serveur GesStock telle que le personnel la tape : « 192.168.1.75 », « 192.168.1.75:8080 »,
 * « http://192.168.1.75:8080/caisse »… Elle est ramenée à une adresse de base : « http://192.168.1.75:8080/ ».
 * Sans numéro de port, c'est celui de GesStock (8080) et non celui d'un serveur web ordinaire (80).
 */
final class AdresseServeur {

    static final int PORT_GESSTOCK = 8080;

    private AdresseServeur() {
    }

    /** L'adresse de base, barre finale comprise, ou {@code null} si la saisie n'est pas l'adresse d'un serveur. */
    static String normaliser(String saisie) {
        if (saisie == null) {
            return null;
        }
        // Sur certains claviers, la touche du point tape une virgule ; les espaces viennent d'un copier-coller.
        String texte = saisie.trim().replace(',', '.').replace(" ", "");
        if (texte.isEmpty()) {
            return null;
        }
        if (!texte.contains("://")) {
            texte = "http://" + texte;
        }
        URI adresse;
        try {
            adresse = new URI(texte);
        } catch (URISyntaxException e) {
            return null;
        }
        String schema = adresse.getScheme() == null ? "" : adresse.getScheme().toLowerCase(Locale.ROOT);
        String hote = adresse.getHost();
        if ((!schema.equals("http") && !schema.equals("https")) || hote == null || hote.isEmpty()) {
            return null;
        }
        int port = adresse.getPort();
        if (port == -1 && schema.equals("http")) {
            port = PORT_GESSTOCK;
        }
        if (port == 0 || port > 65535) {
            return null;
        }
        return schema + "://" + hote.toLowerCase(Locale.ROOT) + (port == -1 ? "" : ":" + port) + "/";
    }

    /** Vrai si {@code adresse} est sur le même serveur (schéma, hôte et port) que l'adresse de base {@code base}. */
    static boolean memeServeur(String adresse, String base) {
        try {
            URI page = new URI(adresse);
            URI serveur = new URI(base);
            return page.getScheme() != null && page.getScheme().equalsIgnoreCase(serveur.getScheme())
                && page.getHost() != null && page.getHost().equalsIgnoreCase(serveur.getHost())
                && port(page) == port(serveur);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** L'adresse sans « http:// » ni barre finale, comme on la tape : « 192.168.1.75:8080 ». */
    static String pourSaisie(String base) {
        String texte = base.startsWith("http://") ? base.substring("http://".length()) : base;
        return texte.endsWith("/") ? texte.substring(0, texte.length() - 1) : texte;
    }

    private static int port(URI adresse) {
        if (adresse.getPort() != -1) {
            return adresse.getPort();
        }
        return "https".equalsIgnoreCase(adresse.getScheme()) ? 443 : 80;
    }
}
