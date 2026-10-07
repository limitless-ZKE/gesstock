package com.gesstock.gesstock.promotions.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

public record PromotionResponse(
    Long id,
    String nom,
    BigDecimal pourcentage,
    LocalDateTime debut,
    LocalDateTime fin,
    boolean active,
    Set<Long> articleIds
) {}
