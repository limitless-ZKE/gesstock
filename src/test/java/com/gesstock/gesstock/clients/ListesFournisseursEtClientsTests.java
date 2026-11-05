package com.gesstock.gesstock.clients;

import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurRequest;
import com.gesstock.gesstock.approvisionnements.dto.LigneApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.service.EntreeStockService;
import com.gesstock.gesstock.approvisionnements.service.FournisseurCatalogueService;
import com.gesstock.gesstock.clients.dto.ClientRequest;
import com.gesstock.gesstock.clients.service.ClientCatalogueService;
import com.gesstock.gesstock.clients.service.ClientExportService;
import com.gesstock.gesstock.clients.service.ClientExportService.FiltreClients;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.fournisseurs.service.FournisseurExportService;
import com.gesstock.gesstock.fournisseurs.service.FournisseurExportService.FiltreFournisseurs;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.statistiques.service.TauxDeMarge;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.VenteDirecteRequest;
import com.gesstock.gesstock.ventes.service.GestionVentesService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
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

/**
 * Demande du client (novembre 2026) : imprimer la liste des fournisseurs (avec ce qu'on leur doit) et celle des clients (avec leurs
 * dettes), filtrées comme à l'écran. Lecture seule.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ListesFournisseursEtClientsTests {

    private static final String ADMIN = "admin";

    @Autowired
    private FournisseurExportService fournisseurExport;
    @Autowired
    private ClientExportService clientExport;
    @Autowired
    private FournisseurCatalogueService fournisseurService;
    @Autowired
    private EntreeStockService entreeService;
    @Autowired
    private ClientCatalogueService clientService;
    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private MockMvc mockMvc;

    private Long article() {
        return articleRepository.findByReferenceIgnoreCase("ALIM-001").orElseThrow().getId();
    }

    /** Un fournisseur ; {@code montant} > 0 : un bon reçu à crédit, donc une dette de ce montant. */
    private Long fournisseur(String nom, String montant) {
        var fournisseur = fournisseurService.creer(new FournisseurRequest(nom, "0100000000", nom.toLowerCase().replace(' ', '.') + "@example.com", "Zone " + nom));
        if (montant != null) {
            var bon = entreeService.creer(new ApprovisionnementRequest("BR-" + nom.replace(' ', '-'), LocalDate.now(), fournisseur.id(),
                List.of(new LigneApprovisionnementRequest(article(), BigDecimal.ONE, new BigDecimal(montant)))));
            entreeService.valider(bon.id(), ADMIN);
        }
        return fournisseur.id();
    }

    /** Un client ; {@code quantite} > 0 : une vente à crédit de ce nombre de sacs de riz (1 300 chacun), donc une dette. */
    private Long client(String nom, Integer quantite) {
        var client = clientService.creer(new ClientRequest(nom, null, "77 000 00 00", "Rue " + nom));
        if (quantite != null) {
            var brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(client.id(), true,
                List.of(new LigneDocumentRequest(article(), null, BigDecimal.valueOf(quantite), null, null)), BigDecimal.ZERO, null, null), ADMIN);
            ventesService.validerVente(brouillon.id(), ADMIN);
        }
        return client.id();
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

    // ------------------------------------------------------------------ fournisseurs

    @Test
    void lesFiltresDeFournisseursSeparentCeuxQuOnDoitDeCeuxQuOnNeDoitPlusEtTrientLesPlusDusDAbord() {
        fournisseur("Petit Doit", "1000");
        fournisseur("Gros Doit", "9000");
        fournisseur("Rien Doit", null);

        var avec = fournisseurExport.retenir(FiltreFournisseurs.AVEC_DETTE, null).stream().map(l -> l.fournisseur().getNom()).toList();
        var sans = fournisseurExport.retenir(FiltreFournisseurs.SANS_DETTE, null).stream().map(l -> l.fournisseur().getNom()).toList();
        var tous = fournisseurExport.retenir(FiltreFournisseurs.TOUS, null).stream().map(l -> l.fournisseur().getNom()).toList();

        assertThat(avec).contains("Petit Doit", "Gros Doit").doesNotContain("Rien Doit");
        assertThat(avec.indexOf("Gros Doit")).as("le plus gros solde d'abord").isLessThan(avec.indexOf("Petit Doit"));
        assertThat(sans).contains("Rien Doit").doesNotContain("Petit Doit", "Gros Doit");
        assertThat(tous).contains("Petit Doit", "Gros Doit", "Rien Doit");
        assertThat(fournisseurExport.retenir(FiltreFournisseurs.TOUS, "gros").stream().map(l -> l.fournisseur().getNom()))
            .containsExactly("Gros Doit");
        assertThat(fournisseurExport.retenir(FiltreFournisseurs.TOUS, "zzzzzz")).isEmpty();
        assertThat(fournisseurExport.retenir(FiltreFournisseurs.AVEC_DETTE, "gros").get(0).soldeDu()).isEqualByComparingTo("9000");
    }

    @Test
    void laListeDesFournisseursEstUnVraiPdfEtUnVraiExcelAvecLeursSoldes() throws Exception {
        fournisseur("Grossiste Imprimé", "4500");

        String texte = texteDuPdf(fournisseurExport.exporterPdf(FiltreFournisseurs.AVEC_DETTE, "imprimé"));
        assertThat(texte).contains("Liste des fournisseurs", "Fournisseurs à qui nous devons de l'argent", "Grossiste Imprimé", "Nous leur devons", "4 500");

        try (Workbook classeur = new XSSFWorkbook(new ByteArrayInputStream(fournisseurExport.exporterExcel(FiltreFournisseurs.AVEC_DETTE, "imprimé")))) {
            Sheet feuille = classeur.getSheetAt(0);
            boolean trouve = false;
            for (var ligne : feuille) {
                if (ligne.getCell(0) != null && "Grossiste Imprimé".equals(ligne.getCell(0).toString())) {
                    trouve = true;
                    assertThat(ligne.getCell(4).getNumericCellValue()).isEqualTo(4500.0);
                }
            }
            assertThat(trouve).isTrue();
        }
        assertThat(new String(fournisseurExport.exporterPdf(FiltreFournisseurs.TOUS, "zzzzzz"), 0, 5)).as("même vide, un vrai document").isEqualTo("%PDF-");
    }

    @Test
    void lesListesDeFournisseursSontReserveesAuStockEtUnFiltreInconnuEstRefuse() throws Exception {
        for (String role : new String[] {"ADMIN", "MANAGER", "GESTIONNAIRE"}) {
            mockMvc.perform(get("/api/fournisseurs/export/pdf").param("filtre", "avec-dette").with(user("u").roles(role)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-fournisseurs-avec-dette.pdf"));
        }
        mockMvc.perform(get("/api/fournisseurs/export/excel").with(user("u").roles("MANAGER")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-fournisseurs-tous.xlsx"));
        mockMvc.perform(get("/api/fournisseurs/export/pdf").with(user("u").roles("CAISSIER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/fournisseurs/export/pdf")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/fournisseurs/export/pdf").param("filtre", "n'importe quoi").with(user("u").roles("ADMIN"))).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ clients

    @Test
    void lesFiltresDeClientsSeparentAvecEtSansDetteEtTrientLesPlusGrosDebiteursDAbord() {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        client("Petit Débiteur", 1);
        client("Gros Débiteur", 5);
        client("Bon Payeur", null);
        LocalDate aujourdhui = LocalDate.now();

        var avec = clientExport.retenir(FiltreClients.AVEC_DETTE, 30, null, aujourdhui).stream().map(l -> l.client().nom()).toList();
        var sans = clientExport.retenir(FiltreClients.SANS_DETTE, 30, null, aujourdhui).stream().map(l -> l.client().nom()).toList();

        assertThat(avec).contains("Petit Débiteur", "Gros Débiteur").doesNotContain("Bon Payeur");
        assertThat(avec.indexOf("Gros Débiteur")).isLessThan(avec.indexOf("Petit Débiteur"));
        assertThat(sans).contains("Bon Payeur").doesNotContain("Petit Débiteur", "Gros Débiteur");
        assertThat(clientExport.retenir(FiltreClients.TOUS, 30, "gros", aujourdhui).stream().map(l -> l.client().nom())).containsExactly("Gros Débiteur");
        var gros = clientExport.retenir(FiltreClients.AVEC_DETTE, 30, "gros", aujourdhui).get(0);
        assertThat(gros.dette()).isEqualByComparingTo("6500");
        assertThat(gros.nombreDeDettes()).isEqualTo(1);
        assertThat(gros.detteLaPlusAncienne()).isEqualTo(aujourdhui);
    }

    @Test
    void laDetteAncienneRetientLesClientsDontLaDetteLaPlusAncienneAuMoinsCeNombreDeJours() {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        client("Client à relancer", 2);
        LocalDate aujourdhui = LocalDate.now();

        assertThat(clientExport.retenir(FiltreClients.DETTE_ANCIENNE, 30, "relancer", aujourdhui)).as("dette du jour : pas encore ancienne").isEmpty();
        assertThat(clientExport.retenir(FiltreClients.DETTE_ANCIENNE, 30, "relancer", aujourdhui.plusDays(29))).isEmpty();
        var apres30 = clientExport.retenir(FiltreClients.DETTE_ANCIENNE, 30, "relancer", aujourdhui.plusDays(30));
        assertThat(apres30).hasSize(1);
        assertThat(apres30.get(0).anciennete(aujourdhui.plusDays(30))).isEqualTo(30);
        assertThat(clientExport.retenir(FiltreClients.DETTE_ANCIENNE, 60, "relancer", aujourdhui.plusDays(45))).as("plus de 60 jours : pas encore").isEmpty();
        assertThat(clientExport.retenir(FiltreClients.DETTE_ANCIENNE, 60, "relancer", aujourdhui.plusDays(61))).hasSize(1);
    }

    @Test
    void laListeDesClientsEstUnVraiPdfEtUnVraiExcelAvecLeursDettes() throws Exception {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        client("Client Imprimé", 3);

        String texte = texteDuPdf(clientExport.exporterPdf(FiltreClients.AVEC_DETTE, 30, "imprimé"));
        assertThat(texte).contains("Liste des clients", "Clients avec une dette en cours", "Client Imprimé", "Dette en cours", "3 900", "Dette la plus ancienne");
        assertThat(texteDuPdf(clientExport.exporterPdf(FiltreClients.DETTE_ANCIENNE, 90, "imprimé"))).contains("dette a plus de 90 jours");

        try (Workbook classeur = new XSSFWorkbook(new ByteArrayInputStream(clientExport.exporterExcel(FiltreClients.AVEC_DETTE, 30, "imprimé")))) {
            boolean trouve = false;
            for (var ligne : classeur.getSheetAt(0)) {
                if (ligne.getCell(0) != null && "Client Imprimé".equals(ligne.getCell(0).toString())) {
                    trouve = true;
                    assertThat(ligne.getCell(4).getNumericCellValue()).isEqualTo(3900.0);
                }
            }
            assertThat(trouve).isTrue();
        }
    }

    @Test
    void lesListesDeClientsSontOuvertesAuxMetiersQuiVoientLesClientsEtLesParametresInvalidesSontRefuses() throws Exception {
        for (String role : new String[] {"ADMIN", "MANAGER", "GESTIONNAIRE", "CAISSIER"}) {
            mockMvc.perform(get("/api/clients/export/pdf").param("filtre", "dette-ancienne").param("jours", "60").with(user("u").roles(role)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-clients-dette-ancienne-60j.pdf"));
        }
        mockMvc.perform(get("/api/clients/export/excel").param("filtre", "sans-dette").with(user("u").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=gesstock-clients-sans-dette.xlsx"));
        mockMvc.perform(get("/api/clients/export/pdf")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/clients/export/pdf").param("filtre", "inconnu").with(user("u").roles("ADMIN"))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/clients/export/pdf").param("jours", "0").with(user("u").roles("ADMIN"))).andExpect(status().isBadRequest());
        assertThat(ClientExportService.jours(null)).isEqualTo(30);
        assertThatThrownBy(() -> ClientExportService.jours(4000)).isInstanceOf(RequeteInvalideException.class);
    }

    // ------------------------------------------------------------------ taux de marge

    @Test
    void leTauxDeMargeSeCalculeSurLePrixDeVenteEtResteVideSansCout() {
        assertThat(TauxDeMarge.de(new BigDecimal("1000"), new BigDecimal("600"))).isEqualByComparingTo("40");
        assertThat(TauxDeMarge.de(new BigDecimal("1300"), new BigDecimal("900"))).isEqualByComparingTo("30.8");
        assertThat(TauxDeMarge.de(new BigDecimal("250"), new BigDecimal("300"))).isEqualByComparingTo("-20");
        assertThat(TauxDeMarge.de(new BigDecimal("1000"), BigDecimal.ZERO)).as("coût inconnu : pas de taux").isNull();
        assertThat(TauxDeMarge.de(BigDecimal.ZERO, new BigDecimal("300"))).isNull();
        assertThat(TauxDeMarge.texte(new BigDecimal("40.0"))).isEqualTo("40 %");
        assertThat(TauxDeMarge.texte(new BigDecimal("30.8"))).isEqualTo("30,8 %");
        assertThat(TauxDeMarge.texte(null)).isEqualTo("—");
    }
}
