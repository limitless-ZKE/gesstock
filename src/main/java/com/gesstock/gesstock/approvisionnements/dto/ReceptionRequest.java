package com.gesstock.gesstock.approvisionnements.dto;

/**
 * Ce qu'on décide en réceptionnant un bon.
 *
 * @param payerEnEspeces vrai : le total du bon sort aussitôt de la caisse du jour (sortie automatique, bon payé, plus de dette) ;
 *                       faux : la dette envers le fournisseur reste à régler plus tard, comme avant
 */
public record ReceptionRequest(boolean payerEnEspeces) {}
