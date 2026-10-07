package com.gesstock.gesstock.services;

import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.Vente;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ExcelExportService {

    // Exportation du Stock en Excel
    public ByteArrayInputStream exporterArticlesToExcel(List<Article> articles) throws IOException {
        String[] colonnes = {"ID", "Désignation", "Quantité en Stock", "Prix Unitaire (FCFA)"};

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Inventaire Stock");

            // En-tête
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);

            for (int col = 0; col < colonnes.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(colonnes[col]);
                cell.setCellStyle(headerStyle);
            }

            // Lignes de données
            int rowIdx = 1;
            for (Article article : articles) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(article.getId());
                row.createCell(1).setCellValue(article.getNom());
                row.createCell(2).setCellValue(article.getQuantite());
                row.createCell(3).setCellValue(article.getPrix().doubleValue());
            }

            for (int i = 0; i < colonnes.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    // Exportation des Ventes en Excel
    public ByteArrayInputStream exporterVentesToExcel(List<Vente> ventes) throws IOException {
        String[] colonnes = {"ID Vente", "Article", "Quantité", "Prix Total (FCFA)", "Date & Heure"};

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Historique Ventes");

            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);

            for (int col = 0; col < colonnes.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(colonnes[col]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            int rowIdx = 1;
            for (Vente vente : ventes) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(vente.getId());
                row.createCell(1).setCellValue(vente.getArticle() != null ? vente.getArticle().getNom() : "N/A");
                row.createCell(2).setCellValue(vente.getQuantite());
                row.createCell(3).setCellValue(vente.getPrixTotal().doubleValue());
                row.createCell(4).setCellValue(vente.getDateVente() != null ? vente.getDateVente().format(formatter) : "");
            }

            for (int i = 0; i < colonnes.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}