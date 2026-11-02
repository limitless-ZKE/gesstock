package com.gesstock.gesstock.journal.service;

import java.util.Locale;
import java.util.Map;

/**
 * Ce que veut dire, en clair, chaque opération enregistrée dans le journal (une action sur un élément : VALIDATION de VENTE…), et
 * la famille où la ranger pour l'écran et les documents. Une opération inconnue garde ses mots d'origine, dans « Autres ».
 */
public final class OperationsConnues {

    /** Les familles du journal, dans l'ordre où l'écran les propose. */
    public enum Famille {
        VENTES("Ventes"),
        CAISSE("Caisse"),
        CLIENTS("Clients et dettes"),
        STOCK("Stock"),
        FOURNISSEURS("Fournisseurs"),
        CATALOGUE("Catalogue"),
        COMPTES("Connexions et comptes"),
        ENTREPRISE("Entreprise"),
        AUTRES("Autres");

        private final String libelle;

        Famille(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    private record Description(String libelle, Famille famille) {}

    private static final Map<String, Description> CONNUES = Map.ofEntries(
        connue("VALIDATION", "VENTE", "Vente validée", Famille.VENTES),
        connue("ANNULATION", "VENTE", "Vente annulée", Famille.VENTES),
        connue("APPROBATION_REMISE", "VENTE", "Remise approuvée sur une vente", Famille.VENTES),
        connue("VENTE_SOUS_COUT", "VENTE", "Vente sous le prix d'achat", Famille.VENTES),
        connue("CREATION", "PROFORMA", "Proforma créé", Famille.VENTES),
        connue("APPROBATION_REMISE", "PROFORMA", "Remise approuvée sur un proforma", Famille.VENTES),
        connue("CHANGEMENT_STATUT", "PROFORMA", "Statut d'un proforma modifié", Famille.VENTES),

        connue("OUVERTURE", "SESSION_CAISSE", "Ouverture de la caisse", Famille.CAISSE),
        connue("CLOTURE", "SESSION_CAISSE", "Clôture de la caisse", Famille.CAISSE),
        connue("SORTIE_CAISSE", "SESSION_CAISSE", "Sortie de caisse", Famille.CAISSE),

        connue("REGLEMENT", "DETTE_CLIENT", "Règlement de dette client", Famille.CLIENTS),
        connue("CREATION", "CLIENT", "Client créé", Famille.CLIENTS),
        connue("MODIFICATION", "CLIENT", "Client modifié", Famille.CLIENTS),
        connue("SUPPRESSION", "CLIENT", "Client supprimé", Famille.CLIENTS),

        connue("VALIDATION", "APPROVISIONNEMENT", "Approvisionnement réceptionné", Famille.STOCK),
        connue("MODIFICATION", "APPROVISIONNEMENT", "Approvisionnement corrigé après réception", Famille.STOCK),
        connue("AJUSTEMENT_STOCK", "ARTICLE", "Ajustement de stock", Famille.STOCK),
        connue("OUVERTURE", "INVENTAIRE", "Inventaire ouvert", Famille.STOCK),
        connue("SOUMISSION", "INVENTAIRE", "Inventaire soumis à validation", Famille.STOCK),
        connue("REPRISE", "INVENTAIRE", "Inventaire repris en saisie", Famille.STOCK),
        connue("ANNULATION", "INVENTAIRE", "Inventaire annulé", Famille.STOCK),
        connue("VALIDATION", "INVENTAIRE", "Inventaire validé", Famille.STOCK),

        connue("CREATION", "DETTE_FOURNISSEUR", "Dette fournisseur enregistrée", Famille.FOURNISSEURS),
        connue("REGLEMENT", "DETTE_FOURNISSEUR", "Paiement d'un fournisseur", Famille.FOURNISSEURS),
        connue("MODIFICATION", "DETTE_FOURNISSEUR", "Dette fournisseur corrigée (bon modifié)", Famille.FOURNISSEURS),
        connue("CREATION", "AVOIR_FOURNISSEUR", "Avoir fournisseur", Famille.FOURNISSEURS),
        connue("CREATION", "RETOUR_FOURNISSEUR", "Retour fournisseur préparé", Famille.FOURNISSEURS),
        connue("VALIDATION", "RETOUR_FOURNISSEUR", "Retour fournisseur validé", Famille.FOURNISSEURS),
        connue("ANNULATION", "RETOUR_FOURNISSEUR", "Retour fournisseur annulé", Famille.FOURNISSEURS),

        connue("CREATION", "ARTICLE", "Article créé", Famille.CATALOGUE),
        connue("MODIFICATION", "ARTICLE", "Article modifié", Famille.CATALOGUE),
        connue("SUPPRESSION", "ARTICLE", "Article supprimé", Famille.CATALOGUE),
        connue("CREATION", "CATEGORIE", "Catégorie créée", Famille.CATALOGUE),
        connue("MODIFICATION", "CATEGORIE", "Catégorie modifiée", Famille.CATALOGUE),
        connue("SUPPRESSION", "CATEGORIE", "Catégorie supprimée", Famille.CATALOGUE),
        connue("CREATION", "UNITE_VENTE", "Unité de vente ajoutée", Famille.CATALOGUE),
        connue("MODIFICATION", "UNITE_VENTE", "Unité de vente modifiée", Famille.CATALOGUE),
        connue("SUPPRESSION", "UNITE_VENTE", "Unité de vente retirée", Famille.CATALOGUE),
        connue("CREATION", "PROMOTION", "Promotion créée", Famille.CATALOGUE),
        connue("MODIFICATION", "PROMOTION", "Promotion modifiée", Famille.CATALOGUE),
        connue("SUPPRESSION", "PROMOTION", "Promotion supprimée", Famille.CATALOGUE),

        connue("CONNEXION", "COMPTE", "Connexion", Famille.COMPTES),
        connue("CONNEXION_REFUSEE", "COMPTE", "Connexion refusée", Famille.COMPTES),
        connue("CREATION", "UTILISATEUR", "Compte créé", Famille.COMPTES),
        connue("MODIFICATION", "UTILISATEUR", "Compte modifié", Famille.COMPTES),
        connue("SUPPRESSION", "UTILISATEUR", "Compte supprimé", Famille.COMPTES),

        connue("MODIFICATION", "ENTREPRISE", "Entreprise modifiée", Famille.ENTREPRISE),
        connue("SUPPRESSION", "ENTREPRISE", "Logo de l'entreprise retiré", Famille.ENTREPRISE)
    );

    private OperationsConnues() {
    }

    private static Map.Entry<String, Description> connue(String action, String entite, String libelle, Famille famille) {
        return Map.entry(cle(action, entite), new Description(libelle, famille));
    }

    private static String cle(String action, String entite) {
        return action + "|" + entite;
    }

    /** « Vente annulée », « Connexion refusée »… ; une opération inconnue : « Action · élément » avec ses mots d'origine. */
    public static String libelle(String action, String entite) {
        Description connue = CONNUES.get(cle(action, entite));
        if (connue != null) {
            return connue.libelle();
        }
        String texte = (action + " · " + entite).replace('_', ' ').toLowerCase(Locale.ROOT);
        return texte.isEmpty() ? texte : Character.toUpperCase(texte.charAt(0)) + texte.substring(1);
    }

    public static Famille famille(String action, String entite) {
        Description connue = CONNUES.get(cle(action, entite));
        return connue == null ? Famille.AUTRES : connue.famille();
    }
}
