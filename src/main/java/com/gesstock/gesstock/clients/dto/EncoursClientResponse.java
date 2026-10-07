package com.gesstock.gesstock.clients.dto;

import java.math.BigDecimal;
import java.util.List;

public record EncoursClientResponse(
    Long clientId,
    String client,
    BigDecimal montantDu,
    List<DetteResponse> dettes
) {}
