package com.gesstock.gesstock.ventes.mapper;

import com.gesstock.gesstock.entities.*;
import com.gesstock.gesstock.ventes.dto.*;

import java.math.BigDecimal;

public final class VentesMapper {

    private VentesMapper() {}

    public static ProformaResponse toResponse(Proforma proforma) {
        var lignes = proforma.getLignes().stream()
            .map(ligne -> new LigneDocumentResponse(
                ligne.getArticle().getId(),
                ligne.getArticle().getReference(),
                ligne.getArticle().getDesignation(),
                ligne.getUniteVente() == null ? null : ligne.getUniteVente().getId(),
                ligne.getQuantite(),
                ligne.getPrixUnitaire(),
                ligne.getMontantLigne(),
                ligne.getPromotion() == null ? null : ligne.getPromotion().getId(),
                ligne.getRemiseUnitaire(),
                ligne.getPrixCatalogue() == null ? ligne.getPrixUnitaire() : ligne.getPrixCatalogue()
            )).toList();
        return new ProformaResponse(
            proforma.getId(),
            proforma.getNumero(),
            proforma.getDateEmission(),
            proforma.getDateExpiration(),
            proforma.getStatut(),
            proforma.getClient() == null ? null : proforma.getClient().getId(),
            proforma.getClient() == null ? null : proforma.getClient().getNom(),
            proforma.getMontantTotal(),
            lignes,
            proforma.getVente() == null ? null : proforma.getVente().getId()
        );
    }

    public static VenteResponse toResponse(Vente vente) {
        var lignes = vente.getLignesVente().stream()
            .map(ligne -> {
                BigDecimal prix = ligne.getPrixUnitaire();
                return new LigneDocumentResponse(
                    ligne.getArticle().getId(),
                    ligne.getArticle().getReference(),
                    ligne.getArticle().getDesignation(),
                    ligne.getUniteVente() == null ? null : ligne.getUniteVente().getId(),
                    ligne.getQuantite(),
                    prix,
                    prix.subtract(ligne.getRemiseUnitaire() == null
                            ? BigDecimal.ZERO : ligne.getRemiseUnitaire())
                        .multiply(BigDecimal.valueOf(ligne.getQuantite())),
                    ligne.getPromotion() == null ? null : ligne.getPromotion().getId(),
                    ligne.getRemiseUnitaire(),
                    ligne.getPrixCatalogue() == null ? prix : ligne.getPrixCatalogue()
                );
            }).toList();
        return new VenteResponse(
            vente.getId(),
            vente.getCode(),
            vente.getDateVente(),
            vente.getStatut(),
            vente.getClient() == null ? null : vente.getClient().getId(),
            vente.getClient() == null ? null : vente.getClient().getNom(),
            vente.getSessionCaisse() == null ? null : vente.getSessionCaisse().getId(),
            vente.getVendeur() == null ? null : vente.getVendeur().getId(),
            vente.getVendeur() == null ? null : vente.getVendeur().getUsername(),
            vente.getProforma() == null ? null : vente.getProforma().getId(),
            vente.isVenteACredit(),
            vente.getMontantTotal(),
            vente.getMontantHT() == null ? vente.getMontantTotal() : vente.getMontantHT(),
            vente.getMontantTva() == null ? BigDecimal.ZERO : vente.getMontantTva(),
            vente.getTauxTva() == null ? BigDecimal.ZERO : vente.getTauxTva(),
            vente.getModeReglement(),
            vente.getMontantRecu(),
            lignes
        );
    }
}
