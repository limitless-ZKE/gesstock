package com.gesstock.gesstock.caisse.dto;

import com.gesstock.gesstock.entities.StatutSessionCaisse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Une journée de caisse.
 *
 * @param montantCloture      espèces comptées à la clôture ; null tant que la session est ouverte
 * @param ecart               montantCloture − soldeTheorique ; null tant que la session est ouverte
 * @param autresEncaissements ce qui est entré hors espèces (Mobile Money, carte…) : à vérifier ailleurs, jamais dans le tiroir
 * @param ventesACredit       ventes à crédit : rien n'est entré en caisse tant que le client n'a pas payé
 */
public record JournalSessionCaisse(
    Long sessionId,
    String code,
    LocalDate journee,
    StatutSessionCaisse statut,
    String ouvertePar,
    String fermeePar,
    LocalDateTime ouverteLe,
    LocalDateTime fermeeLe,
    BigDecimal fondDeCaisse,
    BigDecimal encaissementsEspeces,
    BigDecimal reglementsClientsEspeces,
    BigDecimal entreesDeFonds,
    BigDecimal sorties,
    BigDecimal soldeTheorique,
    BigDecimal montantCloture,
    BigDecimal ecart,
    long nombreVentes,
    long nombreVentesAnnulees,
    BigDecimal ventesACredit,
    List<EncaissementHorsEspeces> autresEncaissements,
    boolean journeeDepassee,
    List<EcritureCaisse> ecritures
) {}
