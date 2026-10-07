package com.gesstock.gesstock.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
// csrf() remplace le dépôt de jetons du filtre dans le contexte partagé : on isole donc ce contexte.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SecurityConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiAnonymeRepond401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void utilisateurConnecteVoitSesRoles() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void lesAutoritesQuiNeSontPasDesRolesNeSontPasExposees() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(user("admin").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("FACTOR_PASSWORD"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles.length()").value(1))
            .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void ecritureSansJetonCsrfEstRefusee() throws Exception {
        mockMvc.perform(post("/api/clients")
                .with(user("admin").roles("ADMIN"))
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void ecritureAvecJetonCsrfPasseLeFiltreCsrf() throws Exception {
        // Corps invalide : la validation répond 400, ce qui prouve que CSRF et rôles sont passés.
        mockMvc.perform(post("/api/clients")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void connexionSansJetonCsrfEstRefusee() throws Exception {
        mockMvc.perform(post("/login")
                .param("username", "admin")
                .param("password", "admin123"))
            .andExpect(status().isForbidden());
    }

    @Test
    void gestionDesUtilisateursEstReserveeAuxAdmins() throws Exception {
        mockMvc.perform(get("/api/referentiels/utilisateurs").with(user("c").roles("CAISSIER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/referentiels/utilisateurs").with(user("a").roles("ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void statistiquesInterditesAuCaissier() throws Exception {
        mockMvc.perform(get("/api/statistiques/dashboard").with(user("c").roles("CAISSIER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void corsAutoriseSeulementLOrigineConfiguree() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .options("/api/auth/me")
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden());
    }
}
