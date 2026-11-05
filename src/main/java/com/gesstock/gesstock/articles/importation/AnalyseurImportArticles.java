package com.gesstock.gesstock.articles.importation;

import com.gesstock.gesstock.articles.importation.ApercuImport.LigneApercu;
import com.gesstock.gesstock.articles.importation.ApercuImport.Statut;
import com.gesstock.gesstock.articles.importation.LecteurFichierArticles.LigneBrute;
import com.gesstock.gesstock.articles.importation.LecteurFichierArticles.TableauLu;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.CategorieArticle;
import com.gesstock.gesstock.entities.UniteMesure;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Décide, ligne par ligne, ce que ferait l'import : créer un article, modifier un article existant (reconnu à sa référence), ne rien
 * changer, ou refuser la ligne. Ne touche à rien : l'aperçu et l'import réel passent par la même analyse.
 *
 * <ul>
 *   <li>Une cellule vide laisse l'article existant tel qu'il est ; pour un nouvel article, elle vaut 0 (ou « aucune catégorie »).</li>
 *   <li>Le stock d'un article existant ne change jamais par import (il ne bouge que par un mouvement tracé) : la colonne est ignorée,
 *       avec un avertissement si elle diffère.</li>
 *   <li>Un prix d'achat modifié suit la même règle que dans la fiche de l'article : il devient le coût du stock en rayon.</li>
 * </ul>
 */
final class AnalyseurImportArticles {

    /** Ce qu'il faut faire pour une ligne acceptée, une fois les catégories créées. */
    record Operation(
        int numero,
        Statut statut,
        Article existant,
        String reference,
        String designation,
        BigDecimal prixAchat,
        BigDecimal prixVente,
        int seuil,
        BigDecimal stock,
        Long categorieId,
        String categorieACreer,
        Long uniteId
    ) {}

    record Plan(List<Operation> operations, ApercuImport apercu) {}

    private AnalyseurImportArticles() {}

    static Plan analyser(
        TableauLu tableau,
        Map<String, Article> articles,          // par référence en minuscules
        Map<String, CategorieArticle> categories, // par nom en minuscules
        List<UniteMesure> unites
    ) {
        Map<String, UniteMesure> unitesParNom = new HashMap<>();
        for (UniteMesure unite : unites) {
            unitesParNom.put(unite.getCode().toLowerCase(Locale.ROOT), unite);
            unitesParNom.put(unite.getLibelle().toLowerCase(Locale.ROOT), unite);
        }
        Map<String, String> categoriesACreer = new LinkedHashMap<>();   // minuscules → écriture du fichier
        Map<String, Integer> referencesVues = new HashMap<>();
        List<LigneApercu> lignes = new ArrayList<>();
        List<Operation> operations = new ArrayList<>();
        int nouveaux = 0;
        int modifies = 0;
        int identiques = 0;
        int enErreur = 0;
        int avecAvertissement = 0;

        for (LigneBrute brute : tableau.lignes()) {
            List<String> erreurs = new ArrayList<>();
            List<String> avertissements = new ArrayList<>();
            List<String> changements = new ArrayList<>();
            String reference = brute.texte(ColonneImport.REFERENCE);
            String designation = brute.texte(ColonneImport.DESIGNATION);

            if (reference.isEmpty()) erreurs.add("La référence est vide.");
            else if (reference.length() > 60) erreurs.add("La référence dépasse 60 caractères.");
            else {
                Integer deja = referencesVues.putIfAbsent(reference.toLowerCase(Locale.ROOT), brute.numero());
                if (deja != null) erreurs.add("La référence « " + reference + " » apparaît déjà à la ligne " + deja + " du fichier.");
            }
            if (designation.length() > 150) erreurs.add("La désignation dépasse 150 caractères.");

            Article existant = reference.isEmpty() ? null : articles.get(reference.toLowerCase(Locale.ROOT));
            if (existant == null && designation.isEmpty()) erreurs.add("La désignation est vide.");

            BigDecimal prixAchat = nombre(brute, ColonneImport.PRIX_ACHAT, 4, erreurs);
            BigDecimal prixVente = nombre(brute, ColonneImport.PRIX_VENTE, 4, erreurs);
            BigDecimal stock = nombre(brute, ColonneImport.STOCK, 3, erreurs);
            BigDecimal seuilLu = nombre(brute, ColonneImport.SEUIL, 0, erreurs);

            // catégorie et unité
            Long categorieId = existant != null && existant.getCategorie() != null ? existant.getCategorie().getId() : null;
            String categorieACreer = null;
            String nomCategorie = brute.texte(ColonneImport.CATEGORIE);
            if (!nomCategorie.isEmpty()) {
                if (nomCategorie.length() > 100) {
                    erreurs.add("Le nom de la catégorie dépasse 100 caractères.");
                } else {
                    CategorieArticle trouvee = categories.get(nomCategorie.toLowerCase(Locale.ROOT));
                    if (trouvee != null) {
                        categorieId = trouvee.getId();
                    } else {
                        categorieId = null;
                        categorieACreer = categoriesACreer.computeIfAbsent(nomCategorie.toLowerCase(Locale.ROOT), cle -> nomCategorie);
                        avertissements.add("La catégorie « " + nomCategorie + " » n'existe pas : elle sera créée.");
                    }
                }
            }
            Long uniteId = existant != null && existant.getUniteMesure() != null ? existant.getUniteMesure().getId() : null;
            String nomUnite = brute.texte(ColonneImport.UNITE);
            if (!nomUnite.isEmpty()) {
                UniteMesure unite = unitesParNom.get(nomUnite.toLowerCase(Locale.ROOT));
                if (unite == null) {
                    erreurs.add("L'unité « " + nomUnite + " » n'existe pas. Unités connues : " + nomsDesUnites(unites)
                        + ". Créez-la dans « Catégories & unités » ou corrigez le nom.");
                } else {
                    uniteId = unite.getId();
                }
            }

            Statut statut;
            BigDecimal achatFinal;
            BigDecimal venteFinale;
            int seuilFinal;
            BigDecimal stockFinal;
            String designationFinale = designation;
            if (existant == null) {
                achatFinal = prixAchat == null ? BigDecimal.ZERO : prixAchat;
                venteFinale = prixVente == null ? BigDecimal.ZERO : prixVente;
                stockFinal = stock == null ? BigDecimal.ZERO : stock;
                seuilFinal = seuilLu == null ? 0 : seuilLu.intValue();
                statut = Statut.NOUVEAU;
                if (achatFinal.signum() == 0) avertissements.add("Prix d'achat absent ou à 0 : l'article apparaîtra « Coût à vérifier » et son bénéfice sera faux tant qu'il n'est pas saisi.");
                if (venteFinale.signum() == 0) avertissements.add("Prix de vente absent ou à 0.");
                if (stockFinal.signum() > 0) changements.add("stock de départ " + texte(stockFinal));
            } else {
                designationFinale = designation.isEmpty() ? existant.getDesignation() : designation;
                achatFinal = prixAchat == null ? existant.getPrixAchat() : prixAchat;
                venteFinale = prixVente == null ? existant.getPrix() : prixVente;
                seuilFinal = seuilLu == null ? existant.getSeuilAlerte() : seuilLu.intValue();
                stockFinal = existant.getQuantiteStock();
                noter(changements, "désignation", existant.getDesignation(), designationFinale);
                noter(changements, "prix d'achat", existant.getPrixAchat(), achatFinal);
                noter(changements, "prix de vente", existant.getPrix(), venteFinale);
                if (seuilFinal != existant.getSeuilAlerte()) changements.add("seuil d'alerte " + existant.getSeuilAlerte() + " → " + seuilFinal);
                if (!nomCategorie.isEmpty()) {
                    String avant = existant.getCategorie() == null ? "aucune" : existant.getCategorie().getNom();
                    if (!avant.equalsIgnoreCase(nomCategorie)) changements.add("catégorie « " + avant + " » → « " + nomCategorie + " »");
                }
                if (!nomUnite.isEmpty() && uniteId != null) {
                    Long avant = existant.getUniteMesure() == null ? null : existant.getUniteMesure().getId();
                    if (!uniteId.equals(avant)) {
                        changements.add("unité « " + (existant.getUniteMesure() == null ? "aucune" : existant.getUniteMesure().getLibelle()) + " » → « " + nomUnite + " »");
                    }
                }
                if (stock != null && stock.compareTo(existant.getQuantiteStock()) != 0) {
                    avertissements.add("Stock du fichier (" + texte(stock) + ") ignoré : le stock actuel (" + texte(existant.getQuantiteStock())
                        + ") ne change pas par un import, utilisez « Ajuster le stock ».");
                }
                statut = changements.isEmpty() ? Statut.IDENTIQUE : Statut.MODIFIE;
            }
            if (achatFinal.signum() > 0 && venteFinale.signum() > 0 && venteFinale.compareTo(achatFinal) < 0) {
                avertissements.add("Prix de vente inférieur au prix d'achat : l'article se vendrait à perte.");
            }

            if (!erreurs.isEmpty()) {
                statut = Statut.ERREUR;
                enErreur++;
            } else {
                switch (statut) {
                    case NOUVEAU -> nouveaux++;
                    case MODIFIE -> modifies++;
                    default -> identiques++;
                }
                operations.add(new Operation(brute.numero(), statut, existant, reference, designationFinale, achatFinal, venteFinale,
                    seuilFinal, stockFinal, categorieId, categorieACreer, uniteId));
            }
            if (!avertissements.isEmpty() && erreurs.isEmpty()) avecAvertissement++;
            // une ligne refusée n'a pas à porter en plus des avertissements : l'erreur à corriger est la seule chose à lire
            lignes.add(new LigneApercu(brute.numero(), reference, designationFinale, statut, erreurs.isEmpty() ? changements : List.of(),
                erreurs.isEmpty() ? avertissements : List.of(), erreurs));
        }

        ApercuImport apercu = new ApercuImport(lignes.size(), nouveaux, modifies, identiques, enErreur, avecAvertissement,
            List.copyOf(categoriesACreer.values()), tableau.colonnesIgnorees(), enErreur == 0 && nouveaux + modifies > 0, lignes);
        return new Plan(operations, apercu);
    }

    /** Une cellule de nombre : vide → {@code null} ; illisible, négative ou trop précise → une erreur. */
    private static BigDecimal nombre(LigneBrute ligne, ColonneImport colonne, int decimalesMax, List<String> erreurs) {
        String brut = ligne.texte(colonne);
        if (brut.isEmpty()) return null;
        try {
            BigDecimal valeur = NombreSaisi.lire(brut);
            if (valeur == null) return null;
            if (valeur.stripTrailingZeros().scale() > decimalesMax) {
                erreurs.add("« " + colonne.intitule() + " » : " + brut + " a trop de décimales ("
                    + (decimalesMax == 0 ? "un nombre entier est attendu" : decimalesMax + " au plus") + ").");
                return null;
            }
            return valeur;
        } catch (NumberFormatException exception) {
            erreurs.add("« " + colonne.intitule() + " » : « " + brut + " » n'est pas un nombre"
                + (brut.startsWith("-") ? " positif." : "."));
            return null;
        }
    }

    private static void noter(List<String> changements, String champ, String avant, String apres) {
        if (!avant.equals(apres)) changements.add(champ + " « " + avant + " » → « " + apres + " »");
    }

    private static void noter(List<String> changements, String champ, BigDecimal avant, BigDecimal apres) {
        if (avant.compareTo(apres) != 0) changements.add(champ + " " + texte(avant) + " → " + texte(apres));
    }

    private static String texte(BigDecimal valeur) {
        return valeur.stripTrailingZeros().toPlainString();
    }

    private static String nomsDesUnites(List<UniteMesure> unites) {
        if (unites.isEmpty()) return "aucune";
        List<String> noms = unites.stream().map(UniteMesure::getLibelle).sorted(String.CASE_INSENSITIVE_ORDER).limit(10).collect(Collectors.toList());
        return String.join(", ", noms) + (unites.size() > 10 ? "…" : "");
    }
}
