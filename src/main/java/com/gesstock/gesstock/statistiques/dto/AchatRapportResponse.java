package com.gesstock.gesstock.statistiques.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un bon d'approvisionnement réceptionné pendant la période : un achat. */
public record AchatRapportResponse(Long id, String numero, LocalDateTime recuLe, String fournisseur, BigDecimal montant) {}
