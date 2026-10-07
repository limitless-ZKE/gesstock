package com.gesstock.gesstock.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

/**
 * Contexte dédié : le post-processeur csrf() des autres tests remplace le dépôt de jetons du filtre,
 * ce qui fausserait la vérification du vrai cookie XSRF-TOKEN.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CsrfCookieTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void cookieCsrfEstEcritDesLaPremiereReponseMemeEn401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }
}
