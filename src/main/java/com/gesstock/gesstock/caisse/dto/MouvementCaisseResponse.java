package com.gesstock.gesstock.caisse.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @param type   SORTIE (l'argent quitte la caisse) ou ENTREE (apport de fonds)
 * @param origine MANUEL, REGLEMENT_FOURNISSEUR (paiement d'un bon en espèces) ou REMBOURSEMENT_CLIENT (vente annulée)
 */
public record MouvementCaisseResponse(
    Long id,
    Long sessionId,
    String type,
    String origine,
    String motif,
    String beneficiaire,
    BigDecimal montant,
    LocalDateTime dateMouvement,
    String effectuePar
) {}
