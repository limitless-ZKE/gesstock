package com.gesstock.gesstock.articles.dto;

/** Pourquoi le stock réel diffère du stock enregistré. Le motif est obligatoire pour tout ajustement. */
public enum MotifAjustementStock {
    CASSE("Casse"),
    VOL("Vol"),
    PEREMPTION("Péremption"),
    ERREUR_DE_SAISIE("Erreur de saisie"),
    ECART_DE_COMPTAGE("Écart de comptage"),
    AUTRE("Autre");

    private final String libelle;

    MotifAjustementStock(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
