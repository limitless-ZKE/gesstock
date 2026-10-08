package com.gesstock.gesstock.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ForceBruteTests {

    @TestConfiguration
    static class HorlogeDeTest {
        @Bean
        @Primary
        HorlogeReglable horlogeReglable() {
            return new HorlogeReglable();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Clock horloge;

    private MockHttpServletRequestBuilder connexion(String utilisateur, String motDePasse) throws Exception {
        String jeton = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN").getValue();
        return post("/api/auth/login").cookie(new Cookie("XSRF-TOKEN", jeton)).header("X-XSRF-TOKEN", jeton)
            .param("username", utilisateur).param("password", motDePasse);
    }

    @Test
    void apresCinqMauvaisMotsDePasseMemeLeBonEstRefuseJusquALaFinDuBlocage() throws Exception {
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(connexion("caissier", "mauvais" + i)).andExpect(status().isUnauthorized());
        }

        // le 6e essai tombe sur le BON mot de passe : il est refusé quand même, avec un message qui dit combien attendre
        mockMvc.perform(connexion("caissier", "caissier123"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("TROP_DE_TENTATIVES"))
            .andExpect(header().string("Retry-After", "900"));

        // un autre compte, ou la même personne plus tard, n'est pas touché
        mockMvc.perform(connexion("gestionnaire", "gestionnaire123")).andExpect(status().isNoContent());
        ((HorlogeReglable) horloge).avancer(Duration.ofMinutes(16));
        mockMvc.perform(connexion("caissier", "caissier123")).andExpect(status().isNoContent());
    }

    @Test
    void unSuccesEntreDeuxEchecsRemetLeCompteurAZero() throws Exception {
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(connexion("admin", "mauvais" + i)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(connexion("admin", "admin123")).andExpect(status().isNoContent());
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(connexion("admin", "encore" + i)).andExpect(status().isUnauthorized()); // 4 nouveaux, pas 8
        }
        mockMvc.perform(connexion("admin", "admin123")).andExpect(status().isNoContent());
    }
}
