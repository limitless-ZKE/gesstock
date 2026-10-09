package com.gesstock.gesstock.caisse.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Journal de caisse d'une période : une session par journée (il n'y en a qu'une par jour), chacune avec ses écritures dans
 * l'ordre du temps et le solde d'espèces après chacune. Sert à l'écran comme aux documents imprimés.
 */
public record JournalCaisseResponse(
    LocalDate debut,
    LocalDate fin,
    TotauxJournalCaisse totaux,
    List<JournalSessionCaisse> sessions
) {}
