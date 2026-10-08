package com.gesstock.gesstock.entities;

/** EN_SAISIE : on compte. SOUMIS : comptage terminé, en attente de validation. VALIDE : écarts appliqués au stock. ANNULE : abandonné. */
public enum StatutInventaire {
    EN_SAISIE,
    SOUMIS,
    VALIDE,
    ANNULE;

    /** Un inventaire est « en cours » tant qu'il n'est ni validé ni annulé : il n'y en a qu'un à la fois. */
    public boolean enCours() {
        return this == EN_SAISIE || this == SOUMIS;
    }
}
