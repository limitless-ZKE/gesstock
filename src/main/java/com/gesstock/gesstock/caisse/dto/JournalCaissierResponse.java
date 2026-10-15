package com.gesstock.gesstock.caisse.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Journal de caisse d'UN caissier sur une période : seulement ce qu'il a fait lui-même (ses ventes, les règlements de dettes qu'il
 * a encaissés, ses sorties), journée par journée. Le fond de caisse, le solde théorique et l'écart de clôture sont ceux du tiroir de
 * la journée, partagé par tous : ils n'y figurent pas, remplacés par les « espèces à remettre » de ce caissier.
 *
 * @param jours seulement les journées où il a travaillé, de la plus ancienne à la plus récente
 */
public record JournalCaissierResponse(
    String caissier,
    LocalDate debut,
    LocalDate fin,
    TotauxJournalCaissier totaux,
    List<JournalCaissierJour> jours
) {}
