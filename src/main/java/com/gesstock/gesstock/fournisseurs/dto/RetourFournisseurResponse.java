package com.gesstock.gesstock.fournisseurs.dto;

import com.gesstock.gesstock.entities.StatutRetourFournisseur;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** {@code avoirId} n'est renseigné qu'une fois le retour validé (et si son montant n'est pas nul). */
public record RetourFournisseurResponse(
    Long id,
    String numero,
    StatutRetourFournisseur statut,
    Long fournisseurId,
    String fournisseur,
    Long approvisionnementId,
    String numeroBon,
    String motif,
    LocalDateTime dateCreation,
    String creePar,
    LocalDateTime dateValidation,
    String validePar,
    BigDecimal montantTotal,
    Long avoirId,
    List<LigneRetourFournisseurResponse> lignes
) {}
