package com.gesstock.gesstock.ventes.dto;

import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.StatutVente;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Une vente de l'historique, sans le détail de ses articles.
 *
 * @param nombreArticles somme des quantités vendues
 * @param remises        remises de la vente (négociée + promotions), zéro s'il n'y en a pas
 */
public record LigneHistoriqueVente(
    Long id,
    String code,
    LocalDateTime dateVente,
    StatutVente statut,
    String client,
    String vendeur,
    boolean aCredit,
    ModeReglement modeReglement,
    int nombreArticles,
    BigDecimal remises,
    BigDecimal montantTotal
) {}
