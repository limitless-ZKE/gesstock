package com.gesstock.gesstock.caisse;

import com.gesstock.gesstock.caisse.dto.MouvementCaisseRequest;
import com.gesstock.gesstock.caisse.service.JournalCaissierExportService;
import com.gesstock.gesstock.caisse.service.MouvementCaisseService;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entreprise.dto.EntrepriseRequest;
import com.gesstock.gesstock.entreprise.service.EntrepriseService;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.VenteDirecteRequest;
import com.gesstock.gesstock.ventes.dto.VenteResponse;
import com.gesstock.gesstock.ventes.service.GestionVentesService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journal de caisse d'un caissier à imprimer : on relit pour de bon le PDF (texte extrait) et le classeur Excel. Il porte le nom de
 * l'entreprise et du caissier, ne contient que ses lignes et ne parle ni de fond de caisse ni de solde ni d'écart de clôture.
 */
@SpringBootTest
@Transactional
class DocumentsCaissierTests {

    private static final String ADMIN = "admin";
    private static final String CAISSIER = "caissier";
    private static final DataFormatter FORMAT = new DataFormatter();

    @Autowired
    private JournalCaissierExportService export;
    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private MouvementCaisseService mouvementService;
    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private ArticleRepository articleRepository;

    private String codeVenteDuCaissier;
    private String codeVenteDeLAdmin;

    @BeforeEach
    void preparer() {
        entrepriseService.modifier(new EntrepriseRequest("Baobab SARL", "12 rue du Marché, Dakar", "+221 33 800 00 00",
            "contact@baobab.example", "RCCM SN-DKR-2020-B-1234", null, "Merci !", true), ADMIN);
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        codeVenteDuCaissier = vendre(CAISSIER, "ELEC-001", 1, ModeReglement.ESPECES).code();
        vendre(CAISSIER, "ALIM-001", 4, ModeReglement.MOBILE_MONEY);
        codeVenteDeLAdmin = vendre(ADMIN, "ALIM-001", 1, ModeReglement.ESPECES).code();
        mouvementService.enregistrer(new MouvementCaisseRequest("Achat de sacs", "Fournisseur Diop", new BigDecimal("2500")), CAISSIER);
    }

    private VenteResponse vendre(String acteur, String reference, int quantite, ModeReglement mode) {
        var article = articleRepository.findByReferenceIgnoreCase(reference).orElseThrow();
        BigDecimal recu = mode == ModeReglement.ESPECES ? new BigDecimal("1000000") : article.getPrix().multiply(BigDecimal.valueOf(quantite));
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false,
            List.of(new LigneDocumentRequest(article.getId(), null, BigDecimal.valueOf(quantite), null, null)), BigDecimal.ZERO, mode, recu), acteur);
        return ventesService.validerVente(brouillon.id(), acteur);
    }

    private static List<String> colonne(Sheet feuille, int index) {
        List<String> valeurs = new ArrayList<>();
        for (Row ligne : feuille) valeurs.add(ligne.getCell(index) == null ? "" : FORMAT.formatCellValue(ligne.getCell(index)));
        return valeurs;
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
    void lePdfPorteLEntrepriseLeCaissierSesLignesSesTotauxEtLesDeuxVisas() throws Exception {
        String texte = texteDuPdf(export.exporterPdf(CAISSIER, LocalDate.now(), LocalDate.now()));

        assertThat(texte).contains("Baobab SARL", "Journal de caisse du caissier", "Caissier : caissier", "Journée du");
        assertThat(texte).contains(codeVenteDuCaissier, "Achat de sacs", "Fournisseur Diop");
        assertThat(texte).as("la vente d'un autre n'y figure pas").doesNotContain(codeVenteDeLAdmin);
        assertThat(texte).contains("Espèces à remettre", "242 500");                       // 245 000 − 2 500
        assertThat(texte).contains("Encaissé hors espèces", "Mobile Money 5 200");
        assertThat(texte).as("rien du tiroir de la journée").doesNotContain("Fond de caisse", "Solde théorique", "Écart", "Espèces comptées");
        assertThat(texte).contains("Visa du caissier", "Visa du responsable", "Page 1");
    }

    @Test
    void lePdfDUnePeriodeSansOperationLeDitAuLieuDEtreVide() throws Exception {
        String texte = texteDuPdf(export.exporterPdf(CAISSIER, LocalDate.now().minusDays(40), LocalDate.now().minusDays(30)));

        assertThat(texte).contains("Caissier : caissier", "n'a effectué aucune opération sur cette période");
    }

    @Test
    void leClasseurAUnRecapitulatifEtLesEcrituresDuCaissierSeulement() throws Exception {
        byte[] fichier = export.exporterExcel(CAISSIER, LocalDate.now(), LocalDate.now());

        try (Workbook classeur = new XSSFWorkbook(new ByteArrayInputStream(fichier))) {
            assertThat(classeur.getSheetName(0)).isEqualTo("Récapitulatif");
            assertThat(classeur.getSheetName(1)).isEqualTo("Écritures");
            Sheet recap = classeur.getSheetAt(0);
            assertThat(colonne(recap, 0)).contains("Baobab SARL", "Journal de caisse du caissier", "Caissier : caissier", "Total");
            Row jour = null;
            for (Row ligne : recap) {
                if (ligne.getCell(1) != null && FORMAT.formatCellValue(ligne.getCell(1)).startsWith("CAISSE-")) jour = ligne;
            }
            assertThat(jour).as("une ligne par journée travaillée").isNotNull();
            assertThat(jour.getCell(2).getNumericCellValue()).isEqualTo(2);              // ventes : espèces + Mobile Money
            assertThat(jour.getCell(3).getNumericCellValue()).isEqualTo(245000);         // ventes espèces
            assertThat(jour.getCell(4).getNumericCellValue()).isZero();                  // règlements
            assertThat(jour.getCell(5).getNumericCellValue()).isEqualTo(2500);           // sorties
            assertThat(jour.getCell(6).getNumericCellValue()).isEqualTo(242500);         // espèces à remettre
            assertThat(FORMAT.formatCellValue(jour.getCell(7))).contains("Mobile Money");

            Sheet ecritures = classeur.getSheetAt(1);
            assertThat(colonne(ecritures, 3)).contains("Opération", "Vente", "Sortie").doesNotContain("Ouverture");
            assertThat(colonne(ecritures, 5)).contains(codeVenteDuCaissier).doesNotContain(codeVenteDeLAdmin);
            assertThat(colonne(ecritures, 4)).anyMatch(texte -> texte.contains("Achat de sacs") && texte.contains("Fournisseur Diop"));
        }
    }
}
