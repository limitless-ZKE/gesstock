package com.gesstock.gesstock.ventes.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * L'historique des ventes d'une période : de quoi remplir l'écran comme le document imprimé. Les lignes d'articles ne sont pas
 * incluses (le ticket complet se relit avec {@code GET /api/ventes/{id}}) ; seules comptent ici les ventes validées et annulées.
 */
public record HistoriqueVentesResponse(
    LocalDate debut,
    LocalDate fin,
    ResumeHistoriqueVentes resume,
    List<LigneHistoriqueVente> ventes
) {}
