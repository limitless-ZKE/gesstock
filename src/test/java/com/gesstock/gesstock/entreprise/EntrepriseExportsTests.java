package com.gesstock.gesstock.entreprise;

import com.gesstock.gesstock.entreprise.dto.EntrepriseRequest;
import com.gesstock.gesstock.entreprise.service.EntrepriseService;
import com.gesstock.gesstock.entities.PerimetreInventaire;
import com.gesstock.gesstock.inventaires.dto.InventaireRequest;
import com.gesstock.gesstock.inventaires.dto.InventaireResponse;
import com.gesstock.gesstock.inventaires.service.InventaireExportService;
import com.gesstock.gesstock.inventaires.service.InventaireService;
import com.gesstock.gesstock.statistiques.dto.RapportStatistiquesResponse;
import com.gesstock.gesstock.statistiques.service.StatistiquesExportService;
import com.gesstock.gesstock.statistiques.service.StatistiquesService;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Les documents produits par le serveur (feuille d'inventaire, rapport Excel et PDF) portent le nom et le logo de l'entreprise. */
@SpringBootTest
@Transactional
class EntrepriseExportsTests {

    @Autowired
    private EntrepriseService entrepriseService;
    @Autowired
    private InventaireService inventaireService;
    @Autowired
    private InventaireExportService inventaireExport;
    @Autowired
    private StatistiquesService statistiquesService;
    @Autowired
    private StatistiquesExportService statistiquesExport;

    private void configurerEntreprise() {
        entrepriseService.modifier(new EntrepriseRequest("Baobab SARL", "12 rue du Marché, Dakar", "+221 33 800 00 00",
            "contact@baobab.example", "RCCM SN-DKR-2020-B-1234", null, "Merci !", true), "admin");
    }

    private static List<String> premieresColonnes(Sheet feuille) {
        DataFormatter format = new DataFormatter();
        List<String> textes = new ArrayList<>();
        for (Row ligne : feuille) {
            if (ligne.getCell(0) != null) {
                textes.add(format.formatCellValue(ligne.getCell(0)));
            }
        }
        return textes;
    }

    private byte[] feuilleInventaire() throws Exception {
        InventaireResponse inventaire = inventaireService.ouvrir(new InventaireRequest(PerimetreInventaire.TOUT, null, null), "gestionnaire");
        return inventaireExport.exporterExcel(inventaire, inventaireService.toutesLesLignes(inventaire.id()));
    }

    @Test
    void laFeuilleDInventaireCommenceParLesCoordonneesEtLeLogoDeLEntreprise() throws Exception {
        configurerEntreprise();
        entrepriseService.remplacerLogo(ImagesDeTest.png(900, 300), "admin");

        try (Workbook classeur = new XSSFWorkbook(new ByteArrayInputStream(feuilleInventaire()))) {
            Sheet feuille = classeur.getSheet("Inventaire");
            List<String> colonneA = premieresColonnes(feuille);
            assertThat(colonneA).contains("Baobab SARL", "12 rue du Marché, Dakar", "Tél. +221 33 800 00 00 · contact@baobab.example", "RCCM SN-DKR-2020-B-1234");
            int titre = colonneA.indexOf(colonneA.stream().filter(texte -> texte.startsWith("Inventaire INV-")).findFirst().orElseThrow());
            assertThat(colonneA.indexOf("Baobab SARL")).as("le nom précède le titre de l'inventaire").isLessThan(titre);
            assertThat(classeur.getAllPictures()).as("le logo est inséré dans la feuille").hasSize(1);
            assertThat(feuille.getDrawingPatriarch()).isNotNull();
        }
    }

    @Test
    void sansLogoLaFeuilleNEnContientPasEtLeContenuResteAlignee() throws Exception {
        configurerEntreprise();

        try (Workbook classeur = new XSSFWorkbook(new ByteArrayInputStream(feuilleInventaire()))) {
            Sheet feuille = classeur.getSheet("Inventaire");
            assertThat(classeur.getAllPictures()).isEmpty();
            DataFormatter format = new DataFormatter();
            int enTete = -1;
            for (Row ligne : feuille) {
                if (ligne.getCell(5) != null && "Stock compté".equals(format.formatCellValue(ligne.getCell(5)))) {
                    enTete = ligne.getRowNum();
                }
            }
            assertThat(enTete).as("la ligne d'en-tête du tableau est retrouvée").isPositive();
            assertThat(feuille.getLastRowNum() - enTete).as("une ligne par article").isEqualTo(6);
            assertThat(format.formatCellValue(feuille.getRow(enTete - 2).getCell(0))).contains("Inventaire INV-");
        }
    }

    @Test
    void leRapportExcelPorteLEnTeteDeLEntrepriseSurSaPremiereFeuille() throws Exception {
        configurerEntreprise();
        entrepriseService.remplacerLogo(ImagesDeTest.png(500, 500), "admin");
        RapportStatistiquesResponse rapport = statistiquesService.rapport(LocalDate.now(), LocalDate.now());

        try (Workbook classeur = new XSSFWorkbook(new ByteArrayInputStream(statistiquesExport.exporterExcel(rapport)))) {
            Sheet synthese = classeur.getSheet("Synthèse");
            List<String> colonneA = premieresColonnes(synthese);
            assertThat(colonneA).contains("Baobab SARL", "Indicateur", "Chiffre d'affaires");
            assertThat(colonneA.indexOf("Baobab SARL")).isLessThan(colonneA.indexOf("Indicateur"));
            assertThat(classeur.getAllPictures()).hasSize(1);
        }
    }

    @Test
    void leRapportPdfEmbarqueLeLogoQuandIlExisteEtSeGenereSansLui() throws Exception {
        configurerEntreprise();
        RapportStatistiquesResponse rapport = statistiquesService.rapport(LocalDate.now(), LocalDate.now());

        byte[] sansLogo = statistiquesExport.exporterPdf(rapport);
        entrepriseService.remplacerLogo(ImagesDeTest.png(900, 300), "admin");
        byte[] avecLogo = statistiquesExport.exporterPdf(rapport);

        assertThat(ImagesDeTest.contient(sansLogo, "%PDF-")).isTrue();
        assertThat(ImagesDeTest.contientMotif(sansLogo, "/Subtype\\s*/Image")).as("pas d'image sans logo").isFalse();
        assertThat(ImagesDeTest.contientMotif(avecLogo, "/Subtype\\s*/Image")).as("le logo est embarqué dans le PDF").isTrue();
        assertThat(avecLogo.length).isGreaterThan(sansLogo.length);
    }
}
