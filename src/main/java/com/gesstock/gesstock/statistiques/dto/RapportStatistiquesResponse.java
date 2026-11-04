package com.gesstock.gesstock.statistiques.dto;

import com.gesstock.gesstock.journal.dto.JournalActiviteResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RapportStatistiquesResponse(
    LocalDate debut,
    LocalDate fin,
    BigDecimal chiffreAffaires,
    long nombreVentes,
    BigDecimal margeBrute,
    BilanDepensesResponse bilanDepenses,
    List<MargeCategorieResponse> margesParCategorie,
    List<MargeArticleResponse> margesParArticle,
    List<SessionCaisseRapportResponse> sessionsCaisse,
    List<JournalActiviteResponse> journalActivites
) {}
