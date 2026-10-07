package com.gesstock.gesstock.journal.dto;

import java.time.LocalDateTime;

public record JournalActiviteResponse(
    Long id,
    String action,
    String entite,
    Long entiteId,
    String details,
    LocalDateTime dateActivite,
    Long utilisateurId,
    String utilisateur
) {}
