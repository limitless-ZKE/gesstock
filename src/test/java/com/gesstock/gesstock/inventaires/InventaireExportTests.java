package com.gesstock.gesstock.inventaires;

import com.gesstock.gesstock.entities.PerimetreInventaire;
import com.gesstock.gesstock.inventaires.dto.InventaireRequest;
import com.gesstock.gesstock.inventaires.dto.InventaireResponse;
import com.gesstock.gesstock.inventaires.service.InventaireExportService;
import com.gesstock.gesstock.inventaires.service.InventaireService;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** La feuille de comptage exportée : une ligne par article, stock théorique rempli, colonne « Stock compté » à remplir. */
@SpringBootTest
@Transactional
class InventaireExportTests {

    @Autowired
    private InventaireService inventaireService;
    @Autowired
    private InventaireExportService exportService;

    @Test
    void laFeuilleDeComptageListeChaqueArticleAvecSonStockTheorique() throws Exception {
        InventaireResponse inventaire = inventaireService.ouvrir(new InventaireRequest(PerimetreInventaire.TOUT, null, null), "gestionnaire");

        byte[] fichier = exportService.exporterExcel(inventaire, inventaireService.toutesLesLignes(inventaire.id()));

        try (Workbook classeur = new XSSFWorkbook(new ByteArrayInputStream(fichier))) {
            Sheet feuille = classeur.getSheet("Inventaire");
            DataFormatter format = new DataFormatter();
            // l'en-tête de l'entreprise occupe le haut de la feuille : on retrouve la ligne des titres de colonnes
            int enTete = -1;
            for (int index = 0; index <= feuille.getLastRowNum(); index++) {
                if (feuille.getRow(index) != null && "Stock compté".equals(format.formatCellValue(feuille.getRow(index).getCell(5)))) {
                    enTete = index;
                }
            }
            assertThat(enTete).as("ligne des titres de colonnes").isPositive();
            assertThat(format.formatCellValue(feuille.getRow(enTete - 2).getCell(0))).contains(inventaire.code()).contains("tout le catalogue");
            assertThat(feuille.getLastRowNum() - enTete).as("une ligne par article").isEqualTo(6);
            // chaque article y figure avec son stock théorique ; la colonne « Stock compté » est vide
            boolean telephoneTrouve = false;
            for (int index = enTete + 1; index <= feuille.getLastRowNum(); index++) {
                if (format.formatCellValue(feuille.getRow(index).getCell(0)).equals("ELEC-001")) {
                    telephoneTrouve = true;
                    assertThat(format.formatCellValue(feuille.getRow(index).getCell(4))).isEqualTo("12");
                    assertThat(format.formatCellValue(feuille.getRow(index).getCell(5))).isEmpty();
                }
            }
            assertThat(telephoneTrouve).isTrue();
        }
    }
}
