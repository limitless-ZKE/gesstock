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
            assertThat(format.formatCellValue(feuille.getRow(0).getCell(0))).contains(inventaire.code()).contains("tout le catalogue");
            assertThat(format.formatCellValue(feuille.getRow(2).getCell(5))).isEqualTo("Stock compté");
            assertThat(feuille.getLastRowNum() - 2).as("une ligne par article").isEqualTo(6);
            // chaque article y figure avec son stock théorique ; la colonne « Stock compté » est vide
            boolean telephoneTrouve = false;
            for (int index = 3; index <= feuille.getLastRowNum(); index++) {
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
