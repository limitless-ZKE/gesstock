package com.gesstock.gesstock.referentiels;

import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.referentiels.dto.RoleRequest;
import com.gesstock.gesstock.referentiels.dto.RoleResponse;
import com.gesstock.gesstock.referentiels.service.RoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les droits de chaque rôle sont écrits dans SecurityConfig : créer un rôle d'un autre nom (l'ancien VENDEUR par exemple)
 * donnerait un rôle qui n'ouvre aucun accès. Le référentiel ne le permet donc pas.
 */
@SpringBootTest
@Transactional
class RolesConnusTests {

    @Autowired
    private RoleService roleService;

    @Test
    void lesAnciensRolesEtLesRolesInventesSontRefuses() {
        for (String nom : new String[] {"VENDEUR", "USER", "STAGIAIRE", "role_vendeur"}) {
            assertThatThrownBy(() -> roleService.creer(new RoleRequest(nom)))
                .as(nom)
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("Rôle inconnu")
                .hasMessageContaining("ADMIN, MANAGER, GESTIONNAIRE, CAISSIER");
        }
    }

    @Test
    void unRoleReconnuEstCreeEnMajusculesSansLePrefixeRole() {
        RoleResponse cree = roleService.creer(new RoleRequest("role_manager"));

        assertThat(cree.name()).isEqualTo("MANAGER");
    }

    @Test
    void onNePeutPasRenommerUnRoleVersUnNomInconnu() {
        Long caissierId = roleService.lister().stream()
            .filter(role -> role.name().equals("CAISSIER")).map(RoleResponse::id).findFirst().orElseThrow();

        assertThatThrownBy(() -> roleService.modifier(caissierId, new RoleRequest("VENDEUR")))
            .isInstanceOf(RequeteInvalideException.class)
            .hasMessageContaining("Rôle inconnu");
    }
}
