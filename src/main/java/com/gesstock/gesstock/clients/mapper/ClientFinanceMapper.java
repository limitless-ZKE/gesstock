package com.gesstock.gesstock.clients.mapper;

import com.gesstock.gesstock.clients.dto.*;
import com.gesstock.gesstock.entities.Client;
import com.gesstock.gesstock.entities.DetteClient;
import com.gesstock.gesstock.entities.ReglementClient;
import com.gesstock.gesstock.entities.StatutDette;

import java.math.BigDecimal;

public final class ClientFinanceMapper {

    private ClientFinanceMapper() {}

    public static ClientResponse toResponse(Client client) {
        return new ClientResponse(
            client.getId(),
            client.getNom(),
            client.getEmail(),
            client.getTelephone(),
            client.getAdresse()
        );
    }

    public static DetteResponse toResponse(DetteClient dette) {
        BigDecimal solde = dette.getMontantInitial().subtract(dette.getMontantRegle());
        return new DetteResponse(
            dette.getId(),
            dette.getDateCreation(),
            dette.getVente() == null ? null : dette.getVente().getId(),
            dette.getVente() == null ? null : dette.getVente().getCode(),
            dette.getMontantInitial(),
            dette.getMontantRegle(),
            dette.getStatut() == StatutDette.ANNULEE ? BigDecimal.ZERO : solde.max(BigDecimal.ZERO),
            dette.getStatut()
        );
    }

    public static DetteAvecClientResponse toResponseAvecClient(DetteClient dette) {
        DetteResponse base = toResponse(dette);
        return new DetteAvecClientResponse(
            base.id(),
            dette.getClient().getId(),
            dette.getClient().getNom(),
            base.dateCreation(),
            base.venteId(),
            base.numeroVente(),
            base.montantInitial(),
            base.montantRegle(),
            base.solde(),
            base.statut()
        );
    }

    public static ReglementResponse toResponse(ReglementClient reglement) {
        return new ReglementResponse(
            reglement.getId(),
            reglement.getDette().getId(),
            reglement.getClient().getId(),
            reglement.getMontant(),
            reglement.getModeReglement(),
            reglement.getReference(),
            reglement.getDateReglement(),
            reglement.getRecuPar() == null ? null : reglement.getRecuPar().getId(),
            reglement.getRecuPar() == null ? null : reglement.getRecuPar().getUsername()
        );
    }
}
