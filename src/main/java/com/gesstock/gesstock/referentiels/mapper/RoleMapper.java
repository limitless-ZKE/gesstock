package com.gesstock.gesstock.referentiels.mapper;

import com.gesstock.gesstock.entities.Role;
import com.gesstock.gesstock.referentiels.dto.RoleResponse;

public final class RoleMapper {

    private RoleMapper() {}

    public static RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getName());
    }
}
