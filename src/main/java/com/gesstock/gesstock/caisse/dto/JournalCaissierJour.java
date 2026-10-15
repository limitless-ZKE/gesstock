package com.gesstock.gesstock.caisse.dto;

import com.gesstock.gesstock.entities.StatutSessionCaisse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Une journée de travail d'un caissier.
 *
 * @param especesARemettre    ses ventes en espèces + les règlements de dettes qu'il a encaissés en espèces − ses sorties
 * @param nombreVentes        ses ventes validées, tous modes confondus (espèces, Mobile Money, carte, crédit…)
 * @param autresEncaissements ce qu'il a encaissé hors espèces : à vérifier ailleurs, jamais dans le tiroir
 * @param ventesACredit       ses ventes à crédit : rien n'est entré en caisse tant que le client n'a pas payé
 */
public record JournalCaissierJour(
    Long sessionId,
    String code,
    LocalDate journee,
    StatutSessionCaisse statut,
    BigDecimal ventesEspeces,
    BigDecimal reglementsEspeces,
    BigDecimal sorties,
    BigDecimal especesARemettre,
    long nombreVentes,
    long nombreVentesAnnulees,
    BigDecimal ventesACredit,
    List<EncaissementHorsEspeces> autresEncaissements,
    List<EcritureCaissier> ecritures
) {}
