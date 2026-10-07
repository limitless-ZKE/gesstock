package com.gesstock.gesstock.statistiques.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PointVentesResponse(LocalDate journee, long nombreVentes, BigDecimal chiffreAffaires) {}
