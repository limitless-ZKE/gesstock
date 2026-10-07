package com.gesstock.gesstock.common;

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

/** Le handler unique doit répondre de la même façon quel que soit le module métier. */
@SpringBootTest
@AutoConfigureMockMvc
// csrf() remplace le dépôt de jetons du filtre dans le contexte partagé : on isole donc ce contexte.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GlobalApiExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ressourceAbsenteRepond404EnProblemDetail() throws Exception {
        mockMvc.perform(get("/api/ventes/987654").with(user("admin").roles("ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Not Found"))
            .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void clientAbsentUtiliseLeMemeFormat() throws Exception {
        mockMvc.perform(get("/api/clients/987654").with(user("admin").roles("ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    void validationListeLesChampsEnErreur() throws Exception {
        mockMvc.perform(post("/api/clients")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.erreurs").exists());
    }

    @Test
    void corpsMalFormeRepond400() throws Exception {
        mockMvc.perform(post("/api/clients")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content("{pas du json"))
            .andExpect(status().isBadRequest());
    }
}
