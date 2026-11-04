package com.gesstock.gesstock.statistiques.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardStatistiquesResponse(
    LocalDate journee,
    BigDecimal chiffreAffairesJour,
    long nombreVentesJour,
    BigDecimal totalCreances,
    long alertesStockCritique,
    BigDecimal valeurStockRevente,
    long nombreArticlesDisponibles,
    List<PointVentesResponse> ventes7Jours,
    List<ArticleTopVentesResponse> topArticles
) {}
