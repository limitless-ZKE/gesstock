package com.gesstock.gesstock.referentiels.mapper;

import com.gesstock.gesstock.entities.UniteMesure;
import com.gesstock.gesstock.referentiels.dto.UniteMesureRequest;
import com.gesstock.gesstock.referentiels.dto.UniteMesureResponse;

public final class UniteMesureMapper {

    private UniteMesureMapper() {}

    public static void updateEntity(UniteMesureRequest request, UniteMesure uniteMesure) {
        uniteMesure.setCode(request.code().trim());
        uniteMesure.setLibelle(request.libelle().trim());
    }

    public static UniteMesureResponse toResponse(UniteMesure uniteMesure) {
        return new UniteMesureResponse(uniteMesure.getId(), uniteMesure.getCode(), uniteMesure.getLibelle());
    }
}
