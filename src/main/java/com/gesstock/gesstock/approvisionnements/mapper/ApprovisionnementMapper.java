package com.gesstock.gesstock.approvisionnements.mapper;

import com.gesstock.gesstock.approvisionnements.dto.*;
import com.gesstock.gesstock.entities.Approvisionnement;
import com.gesstock.gesstock.entities.Fournisseur;
import com.gesstock.gesstock.entities.LigneApprovisionnement;

import java.math.BigDecimal;

public final class ApprovisionnementMapper {

    private ApprovisionnementMapper() {}

    public static FournisseurResponse toResponse(Fournisseur fournisseur) {
        return new FournisseurResponse(
            fournisseur.getId(),
            fournisseur.getNom(),
            fournisseur.getTelephone(),
            fournisseur.getEmail(),
            fournisseur.getAdresse()
        );
    }

    public static ApprovisionnementResponse toResponse(Approvisionnement approvisionnement) {
        var lignesSource = approvisionnement.getLignes() == null
            ? java.util.List.<LigneApprovisionnement>of()
            : approvisionnement.getLignes();
        var lignes = lignesSource.stream().map(ligne -> {
            BigDecimal prix = ligne.getPrixUnitaire();
            return new LigneApprovisionnementResponse(
                ligne.getId(),
                ligne.getArticle() == null ? null : ligne.getArticle().getId(),
                ligne.getArticle() == null ? null : ligne.getArticle().getReference(),
                ligne.getArticle() == null ? null : ligne.getArticle().getDesignation(),
                ligne.getQuantite(),
                prix,
                prix.multiply(BigDecimal.valueOf(ligne.getQuantite()))
            );
        }).toList();
        BigDecimal total = lignes.stream()
            .map(LigneApprovisionnementResponse::montantLigne)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ApprovisionnementResponse(
            approvisionnement.getId(),
            approvisionnement.getNumero(),
            approvisionnement.getDateApprovisionnement(),
            approvisionnement.getStatut(),
            approvisionnement.getFournisseur() == null ? null : approvisionnement.getFournisseur().getId(),
            approvisionnement.getFournisseur() == null ? null : approvisionnement.getFournisseur().getNom(),
            total,
            lignes
        );
    }

    public static void mettreAJourFournisseur(Fournisseur fournisseur, FournisseurRequest request) {
        fournisseur.setNom(request.nom().trim());
        fournisseur.setTelephone(normaliser(request.telephone()));
        fournisseur.setEmail(normaliser(request.email()));
        fournisseur.setAdresse(normaliser(request.adresse()));
    }

    public static void mettreAJourApprovisionnement(
        Approvisionnement approvisionnement,
        ApprovisionnementRequest request,
        Fournisseur fournisseur
    ) {
        approvisionnement.setNumero(request.numero().trim());
        approvisionnement.setDateApprovisionnement(
            request.dateApprovisionnement() == null ? java.time.LocalDate.now() : request.dateApprovisionnement());
        approvisionnement.setFournisseur(fournisseur);
        if (approvisionnement.getLignes() == null) {
            approvisionnement.setLignes(new java.util.ArrayList<>());
        }
        approvisionnement.getLignes().clear();
        request.lignes().forEach(ligneRequest -> {
            LigneApprovisionnement ligne = new LigneApprovisionnement();
            ligne.setApprovisionnement(approvisionnement);
            ligne.setQuantite(ligneRequest.quantite());
            ligne.setPrixUnitaire(ligneRequest.prixUnitaire());
            approvisionnement.getLignes().add(ligne);
        });
    }

    private static String normaliser(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
