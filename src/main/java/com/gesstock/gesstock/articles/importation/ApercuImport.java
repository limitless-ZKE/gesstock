package com.gesstock.gesstock.articles.importation;

import java.util.List;

/**
 * Ce que l'import ferait, ligne par ligne, avant de rien enregistrer.
 *
 * @param categoriesACreer noms de catégories du fichier qui n'existent pas encore (elles seraient créées)
 * @param colonnesIgnorees titres du fichier qui ne correspondent à aucune colonne connue
 * @param importable       vrai s'il n'y a aucune erreur et au moins une ligne à créer ou à modifier
 */
public record ApercuImport(
    int nombreDeLignes,
    int nouveaux,
    int modifies,
    int identiques,
    int erreurs,
    int avertissements,
    List<String> categoriesACreer,
    List<String> colonnesIgnorees,
    boolean importable,
    List<LigneApercu> lignes
) {

    public enum Statut { NOUVEAU, MODIFIE, IDENTIQUE, ERREUR }

    /**
     * @param numero       la ligne du fichier, telle qu'on la voit dans Excel
     * @param changements  ce qui changerait pour un article existant (« prix d'achat 300 → 350 »)
     * @param avertissements ce qui passe, mais mérite un coup d'œil
     * @param erreurs      ce qui empêche l'import de la ligne (et donc de tout le fichier)
     */
    public record LigneApercu(
        int numero,
        String reference,
        String designation,
        Statut statut,
        List<String> changements,
        List<String> avertissements,
        List<String> erreurs
    ) {}
}
