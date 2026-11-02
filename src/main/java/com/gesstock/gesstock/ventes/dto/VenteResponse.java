package com.gesstock.gesstock.ventes.dto;

import com.gesstock.gesstock.entities.StatutVente;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VenteResponse(
    Long id,
    String code,
    LocalDateTime dateVente,
    StatutVente statut,
    Long clientId,
    String client,
    Long sessionCaisseId,
    Long vendeurId,
    String vendeur,
    Long proformaId,
    boolean aCredit,
    BigDecimal montantTotal,
    BigDecimal montantHT,
    BigDecimal montantTva,
    BigDecimal tauxTva,
    com.gesstock.gesstock.entities.ModeReglement modeReglement,
    BigDecimal montantRecu,
    List<LigneDocumentResponse> lignes,
    /** Avertissements pour le vendeur à la validation (vente sous le prix d'achat) ; vide sinon. */
    List<String> avertissements
) {}
