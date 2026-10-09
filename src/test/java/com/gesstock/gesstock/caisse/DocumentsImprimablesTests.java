package com.gesstock.gesstock.caisse;

import com.gesstock.gesstock.caisse.dto.MouvementCaisseRequest;
import com.gesstock.gesstock.caisse.service.JournalCaisseExportService;
import com.gesstock.gesstock.caisse.service.MouvementCaisseService;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entreprise.dto.EntrepriseRequest;
import com.gesstock.gesstock.entreprise.service.EntrepriseService;
import com.gesstock.gesstock.referentiels.dto.ClotureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.SessionCaisseResponse;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.VenteDirecteRequest;
import com.gesstock.gesstock.ventes.dto.VenteResponse;
import com.gesstock.gesstock.ventes.service.GestionVentesService;
import com.gesstock.gesstock.ventes.service.HistoriqueVentesExportService;
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
 * Les documents à imprimer (journal de caisse, historique des ventes) portent le nom de l'entreprise, la période, les écritures et
 * les totaux : on les relit pour de bon (classeur Excel ouvert, texte du PDF extrait), pas seulement leur taille.
 */
@SpringBootTest
@Transactional
class DocumentsImprimablesTests {

    private static final String ADMIN = "admin";
    private static final DataFormatter FORMAT = new DataFormatter();

    @Autowired
    private JournalCaisseExportService journalExport;
    @Autowired
    private HistoriqueVentesExportService historiqueExport;
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

    private String codeVenteEspeces;

    @BeforeEach
    void preparer() {
        entrepriseService.modifier(new EntrepriseRequest("Baobab SARL", "12 rue du Marché, Dakar", "+221 33 800 00 00",
            "contact@baobab.example", "RCCM SN-DKR-2020-B-1234", null, "Merci !", true), ADMIN);
        SessionCaisseResponse session = sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        codeVenteEspeces = vendre("ELEC-001", 1, ModeReglement.ESPECES).code();
        vendre("ALIM-001", 4, ModeReglement.MOBILE_MONEY);
        mouvementService.enregistrer(new MouvementCaisseRequest("Achat de sacs", "Fournisseur Diop", new BigDecimal("2500")), ADMIN);
        sessionService.cloturer(session.id(), new ClotureSessionRequest(new BigDecimal("243500")), ADMIN);   // théorique 243 500
    }

    private VenteResponse vendre(String reference, int quantite, ModeReglement mode) {
        var article = articleRepository.findByReferenceIgnoreCase(reference).orElseThrow();
        // en espèces on remet un gros billet ; un autre moyen de paiement doit correspondre exactement au total
        BigDecimal recu = mode == ModeReglement.ESPECES ? new BigDecimal("1000000") : article.getPrix().multiply(BigDecimal.valueOf(quantite));
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false,
            List.of(new LigneDocumentRequest(article.getId(), null, quantite, null, null)), BigDecimal.ZERO, mode, recu), ADMIN);
        return ventesService.validerVente(brouillon.id(), ADMIN);
    }

    private static List<String> colonne(Sheet feuille, int index) {
        List<String> valeurs = new ArrayList<>();
        for (Row ligne : feuille) {
            valeurs.add(ligne.getCell(index) == null ? "" : FORMAT.formatCellValue(ligne.getCell(index)));
        }
        return valeurs;
    }

    private static String texteDuPdf(byte[] pdf) throws Exception {
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        PdfReader lecteur = new PdfReader(pdf);
        PdfTextExtractor extracteur = new PdfTextExtractor(lecteur);
        StringBuilder texte = new StringBuilder();
        for (int page = 1; page <= lecteur.getNumberOfPages(); page++) texte.append(extracteur.getTextFromPage(page)).append('\n');
        lecteur.close();
        // espaces insécables et doubles : le texte extrait d'un PDF n'a pas de mise en forme fiable
        return texte.toString().replaceAll("[\\u00A0 ]+", " ");
    }

    @Test
    void leJournalDeCaisseExcelADeuxFeuillesAvecLeRecapitulatifEtLesEcritures() throws Exception {
        byte[] fichier = journalExport.exporterExcel(LocalDate.now(), LocalDate.now());

        try (Workbook classeur = new XSSFWorkbook(new ByteArrayInputStream(fichier))) {
            assertThat(classeur.getSheetName(0)).isEqualTo("Récapitulatif");
            assertThat(classeur.getSheetName(1)).isEqualTo("Écritures");
            Sheet recap = classeur.getSheetAt(0);
            assertThat(colonne(recap, 0)).contains("Baobab SARL", "Journal de caisse", "Total");
            Row journee = null;
            for (Row ligne : recap) {
                if (ligne.getCell(1) != null && FORMAT.formatCellValue(ligne.getCell(1)).startsWith("CAISSE-")) journee = ligne;
            }
            assertThat(journee).as("une ligne par journée").isNotNull();
            assertThat(FORMAT.formatCellValue(journee.getCell(4))).isEqualTo("Clôturée");
            assertThat(journee.getCell(5).getNumericCellValue()).isEqualTo(1000);        // fond
            assertThat(journee.getCell(7).getNumericCellValue()).isEqualTo(245000);      // ventes en espèces
            assertThat(journee.getCell(9).getNumericCellValue()).isEqualTo(2500);        // sorties
            assertThat(journee.getCell(10).getNumericCellValue()).isEqualTo(243500);     // solde théorique
            assertThat(journee.getCell(11).getNumericCellValue()).isEqualTo(243500);     // compté
            assertThat(journee.getCell(12).getNumericCellValue()).isZero();              // écart
            assertThat(FORMAT.formatCellValue(journee.getCell(13))).contains("Mobile Money");

            Sheet ecritures = classeur.getSheetAt(1);
            assertThat(colonne(ecritures, 3)).containsSubsequence("Opération", "Ouverture");
            assertThat(colonne(ecritures, 3)).contains("Vente", "Sortie");
            assertThat(colonne(ecritures, 5)).contains(codeVenteEspeces);
            assertThat(colonne(ecritures, 4)).anyMatch(texte -> texte.contains("Achat de sacs") && texte.contains("Fournisseur Diop"));
        }
    }

    @Test
    void leJournalDeCaissePdfPorteLEnteteLesEcrituresLesSoldesEtLaZoneDeVisa() throws Exception {
        String texte = texteDuPdf(journalExport.exporterPdf(LocalDate.now(), LocalDate.now()));

        assertThat(texte).contains("Baobab SARL", "Journal de caisse", "Journée du", "Fond de caisse à l'ouverture");
        assertThat(texte).contains(codeVenteEspeces, "Achat de sacs", "Fournisseur Diop");
        assertThat(texte).contains("Solde théorique des espèces", "Espèces comptées à la clôture", "Écart : aucun");
        assertThat(texte).contains("243 500");
        assertThat(texte).contains("Encaissé hors espèces", "Mobile Money 5 200");
        assertThat(texte).contains("Visa du caissier", "Visa du responsable", "Page 1");
    }

    @Test
    void leJournalDUnePeriodeSansSessionLeDitAuLieuDEtreVide() throws Exception {
        String texte = texteDuPdf(journalExport.exporterPdf(LocalDate.now().minusDays(40), LocalDate.now().minusDays(30)));

        assertThat(texte).contains("Aucune session de caisse n'a été ouverte sur cette période.");
        assertThat(texte).contains("Du " + LocalDate.now().minusDays(40).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    @Test
    void lHistoriqueDesVentesExcelListeLesVentesLesArticlesEtLeResume() throws Exception {
        byte[] fichier = historiqueExport.exporterExcel(LocalDate.now(), LocalDate.now());

        try (Workbook classeur = new XSSFWorkbook(new ByteArrayInputStream(fichier))) {
            assertThat(classeur.getSheetName(0)).isEqualTo("Ventes");
            assertThat(classeur.getSheetName(1)).isEqualTo("Articles vendus");
            Sheet ventes = classeur.getSheetAt(0);
            assertThat(colonne(ventes, 0)).contains("Baobab SARL", "Historique des ventes", "Ventes validées", "Chiffre d'affaires TTC");
            assertThat(colonne(ventes, 2)).contains("N° de vente", codeVenteEspeces);
            Row entete = null;
            Row vente = null;
            for (Row ligne : ventes) {
                String code = ligne.getCell(2) == null ? "" : FORMAT.formatCellValue(ligne.getCell(2));
                if (code.equals("N° de vente")) entete = ligne;
                if (code.equals(codeVenteEspeces)) vente = ligne;
            }
            assertThat(entete).isNotNull();
            assertThat(vente).isNotNull();
            assertThat(FORMAT.formatCellValue(vente.getCell(5))).isEqualTo("Espèces");
            assertThat(vente.getCell(8).getNumericCellValue()).as("total TTC numérique, totalisable").isEqualTo(245000);
            assertThat(FORMAT.formatCellValue(vente.getCell(9))).isEqualTo("Validée");

            Sheet articles = classeur.getSheetAt(1);
            assertThat(colonne(articles, 3)).contains("ELEC-001", "ALIM-001");
            assertThat(colonne(articles, 5)).contains("4");
        }
    }

    @Test
    void lHistoriqueDesVentesPdfPorteLEnteteLesVentesEtLesTotaux() throws Exception {
        String texte = texteDuPdf(historiqueExport.exporterPdf(LocalDate.now(), LocalDate.now()));

        assertThat(texte).contains("Baobab SARL", "Historique des ventes", "Journée du");
        assertThat(texte).contains(codeVenteEspeces, "Client de passage", "Espèces", "Mobile Money", "Validée");
        assertThat(texte).contains("Ventes validées", "Chiffre d'affaires TTC", "250 200");
        assertThat(texte).contains("Page 1");
    }

    @Test
    void surPlusieursJoursLHistoriqueAjouteUnSousTotalParJournee() throws Exception {
        String texte = texteDuPdf(historiqueExport.exporterPdf(LocalDate.now().minusDays(2), LocalDate.now()));

        assertThat(texte).contains("Total du " + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        assertThat(texte).contains("2 ventes validées");
        assertThat(texte).contains("Du ");
    }
}
