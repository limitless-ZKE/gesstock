package com.gesstock.gesstock.inventaires.service;

import com.gesstock.gesstock.inventaires.dto.InventaireResponse;
import com.gesstock.gesstock.inventaires.dto.LigneInventaireResponse;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Feuille de comptage : une ligne par article avec son stock théorique et une colonne « Compté » vide à remplir au rayon,
 * puis l'écart calculé par une formule Excel (la feuille reste utilisable hors connexion).
 */
@Service
public class InventaireExportService {

    public byte[] exporterExcel(InventaireResponse inventaire, List<LigneInventaireResponse> lignes) throws IOException {
        try (Workbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            CellStyle entete = classeur.createCellStyle();
            entete.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font gras = classeur.createFont();
            gras.setBold(true);
            entete.setFont(gras);

            Sheet feuille = classeur.createSheet("Inventaire");
            Row titre = feuille.createRow(0);
            titre.createCell(0).setCellValue("Inventaire " + inventaire.code() + " — "
                + (inventaire.categorie() == null ? "tout le catalogue" : "catégorie " + inventaire.categorie())
                + " — ouvert par " + inventaire.ouvertPar());
            Row ligneEntete = feuille.createRow(2);
            String[] colonnes = {"Référence", "Désignation", "Catégorie", "Unité", "Stock théorique", "Stock compté", "Écart", "Coût moyen", "Motif", "Commentaire"};
            for (int index = 0; index < colonnes.length; index++) {
                var cellule = ligneEntete.createCell(index);
                cellule.setCellValue(colonnes[index]);
                cellule.setCellStyle(entete);
            }
            int numero = 3;
            for (LigneInventaireResponse ligne : lignes) {
                Row row = feuille.createRow(numero);
                row.createCell(0).setCellValue(ligne.reference());
                row.createCell(1).setCellValue(ligne.designation());
                row.createCell(2).setCellValue(ligne.categorie() == null ? "" : ligne.categorie());
                row.createCell(3).setCellValue(ligne.unite() == null ? "" : ligne.unite());
                row.createCell(4).setCellValue(ligne.stockTheorique());
                if (ligne.stockCompte() != null) {
                    row.createCell(5).setCellValue(ligne.stockCompte());
                } else {
                    row.createCell(5); // à remplir au comptage
                }
                row.createCell(6).setCellFormula("IF(F" + (numero + 1) + "=\"\",\"\",F" + (numero + 1) + "-E" + (numero + 1) + ")");
                row.createCell(7).setCellValue(ligne.coutUnitaire().doubleValue());
                row.createCell(8).setCellValue(ligne.motif() == null ? "" : ligne.motif().libelle());
                row.createCell(9).setCellValue(ligne.commentaire() == null ? "" : ligne.commentaire());
                numero++;
            }
            for (int index = 0; index < colonnes.length; index++) {
                feuille.autoSizeColumn(index);
            }
            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }
}
