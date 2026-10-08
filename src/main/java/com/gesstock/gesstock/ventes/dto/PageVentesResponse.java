package com.gesstock.gesstock.ventes.dto;

import java.util.List;

/** Une page de ventes (les plus récentes d'abord) et le nombre total de ventes correspondantes. */
public record PageVentesResponse(List<VenteResponse> ventes, long total) {}
