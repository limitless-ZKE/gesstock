package com.gesstock.gesstock.articles.importation;

import java.math.BigDecimal;

/**
 * Lit un nombre tel qu'on l'écrit dans un tableur français : « 1500 », « 1 500 », « 1500,50 », « 1500.5 », « 1 500 F CFA ». Les
 * espaces (y compris insécables) séparent les milliers ; la virgule ou le point sont la virgule décimale.
 */
final class NombreSaisi {

    private static final BigDecimal MAXIMUM = new BigDecimal("999999999999");
    /** Les monnaies qu'on écrit après un montant, de la plus longue à la plus courte. */
    private static final String[] MONNAIES = {"francs", "franc", "fcfa", "xof", "cfa", "f"};

    private NombreSaisi() {}

    /** @return le nombre, ou {@code null} pour une cellule vide ; @throws NumberFormatException si ce n'est pas un nombre lisible */
    static BigDecimal lire(String brut) {
        if (brut == null) return null;
        StringBuilder texte = new StringBuilder();
        for (char c : brut.toCharArray()) {
            if (c == ' ' || c == ' ' || c == ' ' || c == '\t') continue;
            texte.append(c);
        }
        if (texte.length() == 0) return null;
        // une monnaie écrite après le nombre : « 1500F », « 1500FCFA », « 1500XOF » ; toute autre lettre rend la cellule illisible
        String minuscules = texte.toString().toLowerCase(java.util.Locale.ROOT);
        for (String monnaie : MONNAIES) {
            if (minuscules.endsWith(monnaie)) {
                texte.setLength(texte.length() - monnaie.length());
                break;
            }
        }
        if (texte.length() == 0) throw new NumberFormatException(brut);
        String propre = texte.toString();
        int virgule = propre.lastIndexOf(',');
        int point = propre.lastIndexOf('.');
        if (virgule >= 0 && point >= 0) {
            // les deux : le dernier est la virgule décimale, l'autre sépare les milliers (« 1.500,50 » ou « 1,500.50 »)
            propre = virgule > point ? propre.replace(".", "").replace(',', '.') : propre.replace(",", "");
        } else if (virgule >= 0) {
            if (propre.indexOf(',') != virgule) throw new NumberFormatException(brut);
            propre = propre.replace(',', '.');
        } else if (point >= 0 && propre.indexOf('.') != point) {
            propre = propre.replace(".", "");   // plusieurs points : des séparateurs de milliers
        }
        int chiffres = 0;
        int points = 0;
        for (int i = 0; i < propre.length(); i++) {
            char c = propre.charAt(i);
            if (c >= '0' && c <= '9') chiffres++;
            else if (c == '.') points++;
            else throw new NumberFormatException(brut);
        }
        if (chiffres == 0 || points > 1) throw new NumberFormatException(brut);
        BigDecimal nombre = new BigDecimal(propre);
        if (nombre.compareTo(MAXIMUM) > 0) throw new NumberFormatException(brut);
        return nombre;
    }
}
