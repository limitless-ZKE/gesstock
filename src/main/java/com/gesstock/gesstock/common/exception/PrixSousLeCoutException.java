package com.gesstock.gesstock.common.exception;

/**
 * Un prix négocié place une ligne de vente sous son prix d'achat : la validation est refusée (409). Code stable
 * {@code PRIX_SOUS_LE_COUT} pour que la caisse sache qu'il faut relever le prix dans le panier, et non réessayer à l'identique.
 */
public class PrixSousLeCoutException extends ConflitRessourceException {
    public PrixSousLeCoutException(String message) {
        super(message);
    }
}
