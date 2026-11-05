package com.gesstock.gesstock.articles.importation;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Les colonnes d'un fichier d'import d'articles. Chaque colonne se reconnaît à son titre, sans tenir compte des majuscules, des
 * accents, des espaces ni des apostrophes : « Prix d'achat », « prix achat » et « PRIX_ACHAT » sont la même colonne.
 */
public enum ColonneImport {
    REFERENCE("Référence", true, "reference", "ref", "code", "codearticle", "codeproduit", "refarticle"),
    DESIGNATION("Désignation", true, "designation", "nom", "nomarticle", "nomdelarticle", "article", "produit", "nomproduit", "libelle"),
    CATEGORIE("Catégorie", false, "categorie", "famille", "categoriearticle"),
    UNITE("Unité", false, "unite", "unitedemesure", "unitemesure"),
    PRIX_ACHAT("Prix d'achat", false, "prixachat", "prixdachat", "achat", "coutachat", "coutdachat", "pa"),
    PRIX_VENTE("Prix de vente", false, "prixvente", "prixdevente", "vente", "pv", "prix", "prixunitaire"),
    STOCK("Stock", false, "stock", "quantite", "qte", "stockinitial", "stockactuel"),
    SEUIL("Seuil d'alerte", false, "seuil", "seuilalerte", "seuildalerte", "seuilalertestock", "stockminimum", "stockmini");

    private final String intitule;
    private final boolean obligatoire;
    private final List<String> synonymes;

    ColonneImport(String intitule, boolean obligatoire, String... synonymes) {
        this.intitule = intitule;
        this.obligatoire = obligatoire;
        this.synonymes = List.of(synonymes);
    }

    /** Le titre écrit dans le modèle à télécharger. */
    public String intitule() {
        return intitule;
    }

    public boolean obligatoire() {
        return obligatoire;
    }

    /** « Prix d'achat » → « prixdachat » : minuscules, sans accents, sans rien d'autre que des lettres et des chiffres. */
    static String simplifier(String texte) {
        String decompose = Normalizer.normalize(texte == null ? "" : texte, Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
        StringBuilder propre = new StringBuilder();
        for (int i = 0; i < decompose.length(); i++) {
            char c = decompose.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) propre.append(c);
        }
        return propre.toString();
    }

    /** La colonne que désigne ce titre, ou rien si le titre n'est pas reconnu. */
    static Optional<ColonneImport> reconnaitre(String titre) {
        String simple = simplifier(titre);
        if (simple.isEmpty()) return Optional.empty();
        for (ColonneImport colonne : values()) {
            if (colonne.synonymes.contains(simple)) return Optional.of(colonne);
        }
        return Optional.empty();
    }
}
