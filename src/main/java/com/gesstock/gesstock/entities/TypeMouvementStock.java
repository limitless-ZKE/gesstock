package com.gesstock.gesstock.entities;

public enum TypeMouvementStock {
    /** Stock de départ saisi à la création d'un article : sans lui, la somme des mouvements n'explique pas le stock. */
    STOCK_INITIAL,
    ENTREE_APPROVISIONNEMENT,
    SORTIE_VENTE,
    AJUSTEMENT_POSITIF,
    AJUSTEMENT_NEGATIF,
    RETOUR_CLIENT,
    RETOUR_FOURNISSEUR
}
