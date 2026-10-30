package com.gesstock.gesstock.journal.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Le journal des opérations d'une période (pour tous les utilisateurs ou pour un seul), les plus récentes d'abord.
 *
 * @param nombre         opérations de la période
 * @param parUtilisateur combien chaque compte en a fait (les plus actifs d'abord ; « Sans compte » pour un identifiant inconnu)
 * @param parOperation   combien de chaque type (les plus fréquentes d'abord)
 */
public record JournalOperationsResponse(
    LocalDate debut,
    LocalDate fin,
    long nombre,
    List<NombreParLibelle> parUtilisateur,
    List<NombreParLibelle> parOperation,
    List<OperationJournal> operations
) {}
