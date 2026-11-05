package com.gesstock.gesstock.approvisionnements;

import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementResponse;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurRequest;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurResponse;
import com.gesstock.gesstock.approvisionnements.dto.LigneApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.service.ApprovisionnementExportService;
import com.gesstock.gesstock.approvisionnements.service.EntreeStockService;
import com.gesstock.gesstock.approvisionnements.service.FournisseurCatalogueService;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Demande du client (novembre 2026) : imprimer un bon d'approvisionnement, avant comme après sa réception. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BonApprovisionnementImprimableTests {

    @Autowired
    private ApprovisionnementExportService exportService;
    @Autowired
    private EntreeStockService entreeService;
    @Autowired
    private FournisseurCatalogueService fournisseurService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private MockMvc mockMvc;

    private ApprovisionnementResponse unBon(String numero) {
        FournisseurResponse fournisseur = fournisseurService.creer(new FournisseurRequest("Grande Distribution Sall", "0100000000", "s@example.com", "Zone industrielle"));
        Long article = articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow().getId();
        return entreeService.creer(new ApprovisionnementRequest(numero, LocalDate.now(), fournisseur.id(), List.of(
            new LigneApprovisionnementRequest(article, new BigDecimal("2.5"), new BigDecimal("190000"), new BigDecimal("260000")))));
    }

    private static String texteDuPdf(byte[] pdf) throws Exception {
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        PdfReader lecteur = new PdfReader(pdf);
        PdfTextExtractor extracteur = new PdfTextExtractor(lecteur);
        StringBuilder texte = new StringBuilder();
        for (int page = 1; page <= lecteur.getNumberOfPages(); page++) texte.append(extracteur.getTextFromPage(page)).append('\n');
        lecteur.close();
        return texte.toString().replaceAll("[\\u00A0 ]+", " ");
    }

    @Test
    void leBonImprimePorteLeFournisseurLesLignesLeTotalEtLesTroisSignaturesSansLePrixDeRevente() throws Exception {
        ApprovisionnementResponse bon = unBon("BR-IMP-1");

        String texte = texteDuPdf(exportService.exporterPdf(bon.id()));

        assertThat(texte).contains("Bon d'approvisionnement BR-IMP-1", "brouillon", "Grande Distribution Sall", "Zone industrielle");
        assertThat(texte).contains("ELEC-001", "2,5", "190 000", "475 000");                        // 2,5 × 190 000
        assertThat(texte).contains("Réceptionné par", "Le fournisseur ou son livreur");
        assertThat(texte).as("le fournisseur n'a pas à connaître le prix de revente").doesNotContain("260 000");
    }

    @Test
    void apresReceptionLeBonIndiqueQuIlEstRecuEnStock() throws Exception {
        ApprovisionnementResponse bon = unBon("BR-IMP-2");
        entreeService.valider(bon.id(), "admin");

        String texte = texteDuPdf(exportService.exporterPdf(bon.id()));

        assertThat(texte).contains("reçu en stock le", "BR-IMP-2");
        assertThat(texte).doesNotContain("brouillon");
    }

    @Test
    void lNomDeFichierEstSansCaractereDangereuxEtUnBonInconnuEstRefuse() {
        ApprovisionnementResponse bon = unBon("BR/IMP 3");

        assertThat(exportService.nomDeFichier(bon.id())).isEqualTo("gesstock-bon-BR-IMP-3");
        assertThatThrownBy(() -> exportService.exporterPdf(-1L)).isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    void lImpressionEstReserveeAuxMemesRolesQueLesBons() throws Exception {
        Long id = unBon("BR-IMP-4").id();

        for (String role : new String[] {"ADMIN", "MANAGER", "GESTIONNAIRE"}) {
            mockMvc.perform(get("/api/approvisionnements/" + id + "/export/pdf").with(user("u").roles(role)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-bon-BR-IMP-4.pdf"));
        }
        mockMvc.perform(get("/api/approvisionnements/" + id + "/export/pdf").with(user("u").roles("CAISSIER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/approvisionnements/" + id + "/export/pdf")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/approvisionnements/999999/export/pdf").with(user("u").roles("ADMIN"))).andExpect(status().isNotFound());
    }
}
