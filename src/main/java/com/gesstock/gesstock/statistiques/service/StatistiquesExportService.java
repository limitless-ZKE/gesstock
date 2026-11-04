package com.gesstock.gesstock.statistiques.service;

import com.gesstock.gesstock.entreprise.dto.EntrepriseResponse;
import com.gesstock.gesstock.entreprise.service.EnteteExcel;
import com.gesstock.gesstock.entreprise.service.EntetePdf;
import com.gesstock.gesstock.entreprise.service.EntrepriseService;
import com.gesstock.gesstock.entreprise.service.TailleLogo;
import com.gesstock.gesstock.statistiques.dto.AchatRapportResponse;
import com.gesstock.gesstock.statistiques.dto.BilanDepensesResponse;
import com.gesstock.gesstock.statistiques.dto.DepenseRapportResponse;
import com.gesstock.gesstock.statistiques.dto.MargeArticleResponse;
import com.gesstock.gesstock.statistiques.dto.MargeCategorieResponse;
import com.gesstock.gesstock.statistiques.dto.RapportStatistiquesResponse;
import com.gesstock.gesstock.statistiques.dto.SessionCaisseRapportResponse;
import com.gesstock.gesstock.journal.dto.JournalActiviteResponse;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class StatistiquesExportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EntrepriseService entrepriseService;

    public StatistiquesExportService(EntrepriseService entrepriseService) {
        this.entrepriseService = entrepriseService;
    }

    public byte[] exporterExcel(RapportStatistiquesResponse rapport) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle header = workbook.createCellStyle();
            header.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            header.setFont(headerFont);

            Sheet synthese = workbook.createSheet("Synthèse");
            EntrepriseResponse entreprise = entrepriseService.obtenir();
            byte[] logo = entrepriseService.logo(TailleLogo.MINIATURE).map(EntrepriseService.LogoServi::contenu).orElse(null);
            int debut = EnteteExcel.ecrire(workbook, synthese, entreprise, logo);
            addSummary(synthese, rapport, header, debut);

            Sheet categories = workbook.createSheet("Marges par catégorie");
            addHeader(categories, header, "Catégorie", "Chiffre d'affaires", "Coût d'achat", "Marge brute");
            int rowNumber = 1;
            for (MargeCategorieResponse marge : rapport.margesParCategorie()) {
                Row row = categories.createRow(rowNumber++);
                row.createCell(0).setCellValue(marge.categorie());
                row.createCell(1).setCellValue(marge.chiffreAffaires().doubleValue());
                row.createCell(2).setCellValue(marge.coutAchat().doubleValue());
                row.createCell(3).setCellValue(marge.margeBrute().doubleValue());
            }
            autoSize(categories, 4);

            Sheet articles = workbook.createSheet("Marges par produit");
            addHeader(articles, header, "Référence", "Produit", "Quantité vendue", "Chiffre d'affaires", "Coût d'achat", "Marge brute");
            rowNumber = 1;
            for (MargeArticleResponse marge : rapport.margesParArticle()) {
                Row row = articles.createRow(rowNumber++);
                row.createCell(0).setCellValue(marge.reference());
                row.createCell(1).setCellValue(marge.designation());
                row.createCell(2).setCellValue(marge.quantiteVendue().doubleValue());
                row.createCell(3).setCellValue(marge.chiffreAffaires().doubleValue());
                row.createCell(4).setCellValue(marge.coutAchat().doubleValue());
                row.createCell(5).setCellValue(marge.margeBrute().doubleValue());
            }
            autoSize(articles, 6);

            Sheet depenses = workbook.createSheet("Dépenses");
            BilanDepensesResponse bilan = rapport.bilanDepenses();
            addHeader(depenses, header, "Type", "Date et heure", "Référence ou motif", "Fournisseur ou bénéficiaire", "Montant");
            rowNumber = 1;
            for (AchatRapportResponse bon : bilan.bonsRecus()) {
                Row row = depenses.createRow(rowNumber++);
                row.createCell(0).setCellValue("Achat (bon réceptionné)");
                row.createCell(1).setCellValue(bon.recuLe() == null ? "" : bon.recuLe().format(DATE_TIME));
                row.createCell(2).setCellValue(bon.numero());
                row.createCell(3).setCellValue(bon.fournisseur());
                row.createCell(4).setCellValue(bon.montant().doubleValue());
            }
            for (DepenseRapportResponse depense : bilan.depenses()) {
                Row row = depenses.createRow(rowNumber++);
                row.createCell(0).setCellValue("Sortie de caisse");
                row.createCell(1).setCellValue(depense.date().format(DATE_TIME));
                row.createCell(2).setCellValue(depense.motif());
                row.createCell(3).setCellValue(depense.beneficiaire());
                row.createCell(4).setCellValue(depense.montant().doubleValue());
            }
            Row totalLigne = depenses.createRow(rowNumber);
            totalLigne.createCell(0).setCellValue("Total des dépenses");
            totalLigne.createCell(4).setCellValue(bilan.totalDepenses().doubleValue());
            autoSize(depenses, 5);

            Sheet caisse = workbook.createSheet("Journal de caisse");
            addHeader(caisse, header, "Date", "Session", "Caissier", "Statut", "Fond de caisse", "Chiffre d'affaires", "Encaissements ventes", "Règlements clients (espèces)", "Sorties de caisse", "Clôture", "Ventes", "Entrées de fonds");
            rowNumber = 1;
            for (SessionCaisseRapportResponse session : rapport.sessionsCaisse()) {
                Row row = caisse.createRow(rowNumber++);
                row.createCell(0).setCellValue(session.journee().toString());
                row.createCell(1).setCellValue(session.code() == null ? "" : session.code());
                row.createCell(2).setCellValue(session.caissier());
                row.createCell(3).setCellValue(session.statut().name());
                row.createCell(4).setCellValue(session.fondDeCaisse().doubleValue());
                row.createCell(5).setCellValue(session.chiffreAffaires().doubleValue());
                row.createCell(6).setCellValue(session.montantEncaisse().doubleValue());
                row.createCell(7).setCellValue(session.reglementsClientsEspeces().doubleValue());
                row.createCell(8).setCellValue(session.sortiesCaisse().doubleValue());
                if (session.montantCloture() != null) row.createCell(9).setCellValue(session.montantCloture().doubleValue());
                row.createCell(10).setCellValue(session.nombreVentes());
                row.createCell(11).setCellValue(session.entreesDeFonds().doubleValue());
            }
            autoSize(caisse, 12);

            Sheet journal = workbook.createSheet("Journal d'activité");
            addHeader(journal, header, "Date et heure", "Action", "Entité", "Référence", "Utilisateur", "Détails");
            rowNumber = 1;
            for (JournalActiviteResponse activite : rapport.journalActivites()) {
                Row row = journal.createRow(rowNumber++);
                row.createCell(0).setCellValue(activite.dateActivite().format(
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                row.createCell(1).setCellValue(activite.action());
                row.createCell(2).setCellValue(activite.entite());
                if (activite.entiteId() != null) row.createCell(3).setCellValue(activite.entiteId());
                row.createCell(4).setCellValue(activite.utilisateur() == null ? "" : activite.utilisateur());
                row.createCell(5).setCellValue(activite.details() == null ? "" : activite.details());
            }
            autoSize(journal, 6);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    public byte[] exporterPdf(RapportStatistiquesResponse rapport) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document();
            try {
                PdfWriter.getInstance(document, output);
                document.open();
                Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
                Font section = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
                EntetePdf.ajouter(document, entrepriseService.obtenir(),
                    entrepriseService.logo(TailleLogo.ENTETE).map(EntrepriseService.LogoServi::contenu).orElse(null));
                document.add(new Paragraph("Rapport financier", title));
                document.add(new Paragraph("Période : " + rapport.debut() + " au " + rapport.fin()));
                document.add(new Paragraph(" "));
                document.add(new Paragraph("Synthèse", section));
                PdfPTable summary = new PdfPTable(3);
                summary.setWidthPercentage(100);
                addPdfHeader(summary, "Chiffre d'affaires", "Ventes", "Marge brute");
                summary.addCell(rapport.chiffreAffaires().toPlainString());
                summary.addCell(Long.toString(rapport.nombreVentes()));
                summary.addCell(rapport.margeBrute().toPlainString());
                document.add(summary);
                document.add(new Paragraph(" "));
                addBilanDepenses(document, section, rapport);
                addMarginTables(document, section, rapport);
                addCashTable(document, section, rapport.sessionsCaisse());
                addJournalTable(document, section, rapport.journalActivites());
                document.close();
            } catch (DocumentException exception) {
                throw new IOException("La génération du rapport PDF a échoué", exception);
            }
            return output.toByteArray();
        }
    }

    private void addSummary(Sheet sheet, RapportStatistiquesResponse rapport, CellStyle header, int debut) {
        addHeader(sheet, debut, header, "Indicateur", "Valeur");
        String[][] values = {
            {"Période du", rapport.debut().toString()},
            {"Période au", rapport.fin().toString()},
            {"Chiffre d'affaires", rapport.chiffreAffaires().toPlainString()},
            {"Nombre de ventes", Long.toString(rapport.nombreVentes())},
            {"Marge brute", rapport.margeBrute().toPlainString()},
            {"Achats (bons réceptionnés)", rapport.bilanDepenses().achats().toPlainString()},
            {"Autres dépenses (sorties de caisse)", rapport.bilanDepenses().autresDepenses().toPlainString()},
            {"Total des dépenses", rapport.bilanDepenses().totalDepenses().toPlainString()},
            {"Résultat (chiffre d'affaires − dépenses)", rapport.bilanDepenses().resultat().toPlainString()},
            {"Entrées de fonds (hors chiffre d'affaires)", rapport.bilanDepenses().entreesDeFonds().toPlainString()}
        };
        for (int i = 0; i < values.length; i++) {
            Row row = sheet.createRow(debut + i + 1);
            row.createCell(0).setCellValue(values[i][0]);
            row.createCell(1).setCellValue(values[i][1]);
        }
        autoSize(sheet, 2);
    }

    private void addBilanDepenses(Document document, Font section, RapportStatistiquesResponse rapport) throws DocumentException {
        BilanDepensesResponse bilan = rapport.bilanDepenses();
        document.add(new Paragraph("Dépenses et résultat", section));
        PdfPTable resume = new PdfPTable(5);
        resume.setWidthPercentage(100);
        addPdfHeader(resume, "Chiffre d'affaires", "Achats", "Autres dépenses", "Résultat", "Entrées de fonds");
        resume.addCell(rapport.chiffreAffaires().toPlainString());
        resume.addCell(bilan.achats().toPlainString());
        resume.addCell(bilan.autresDepenses().toPlainString());
        resume.addCell(bilan.resultat().toPlainString());
        resume.addCell(bilan.entreesDeFonds().toPlainString());
        document.add(resume);
        document.add(new Paragraph("Résultat = chiffre d'affaires − achats − autres dépenses. Les achats sont les bons d'approvisionnement réceptionnés ; les autres dépenses sont les sorties de caisse saisies à la main. Les entrées de fonds ne sont pas du chiffre d'affaires."));
        if (!bilan.bonsRecus().isEmpty() || !bilan.depenses().isEmpty()) {
            PdfPTable detail = new PdfPTable(5);
            detail.setWidthPercentage(100);
            addPdfHeader(detail, "Type", "Date et heure", "Référence ou motif", "Fournisseur ou bénéficiaire", "Montant");
            for (AchatRapportResponse bon : bilan.bonsRecus()) {
                detail.addCell("Achat");
                detail.addCell(bon.recuLe() == null ? "" : bon.recuLe().format(DATE_TIME));
                detail.addCell(bon.numero());
                detail.addCell(bon.fournisseur());
                detail.addCell(bon.montant().toPlainString());
            }
            for (DepenseRapportResponse depense : bilan.depenses()) {
                detail.addCell("Sortie de caisse");
                detail.addCell(depense.date().format(DATE_TIME));
                detail.addCell(depense.motif());
                detail.addCell(depense.beneficiaire());
                detail.addCell(depense.montant().toPlainString());
            }
            document.add(new Paragraph(" "));
            document.add(detail);
        }
        document.add(new Paragraph(" "));
    }

    private void addMarginTables(Document document, Font section, RapportStatistiquesResponse rapport)
        throws DocumentException {
        document.add(new Paragraph("Marges par catégorie", section));
        PdfPTable categories = new PdfPTable(4);
        categories.setWidthPercentage(100);
        addPdfHeader(categories, "Catégorie", "Chiffre d'affaires", "Coût", "Marge");
        for (MargeCategorieResponse marge : rapport.margesParCategorie()) {
            categories.addCell(marge.categorie());
            categories.addCell(marge.chiffreAffaires().toPlainString());
            categories.addCell(marge.coutAchat().toPlainString());
            categories.addCell(marge.margeBrute().toPlainString());
        }
        document.add(categories);
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Marges par produit", section));
        PdfPTable articles = new PdfPTable(5);
        articles.setWidthPercentage(100);
        addPdfHeader(articles, "Référence", "Produit", "Quantité", "Chiffre d'affaires", "Marge");
        for (MargeArticleResponse marge : rapport.margesParArticle()) {
            articles.addCell(marge.reference());
            articles.addCell(marge.designation());
            articles.addCell(com.gesstock.gesstock.stock.Quantites.texte(marge.quantiteVendue()));
            articles.addCell(marge.chiffreAffaires().toPlainString());
            articles.addCell(marge.margeBrute().toPlainString());
        }
        document.add(articles);
        document.add(new Paragraph(" "));
    }

    private void addCashTable(Document document, Font section, List<SessionCaisseRapportResponse> sessions)
        throws DocumentException {
        document.add(new Paragraph("Journal de caisse par caissier", section));
        PdfPTable caisse = new PdfPTable(9);
        caisse.setWidthPercentage(100);
        addPdfHeader(caisse, "Date", "Session", "Caissier", "Fond", "Ventes", "Encaissements", "Règl. clients", "Sorties", "Clôture");
        for (SessionCaisseRapportResponse session : sessions) {
            caisse.addCell(session.journee().toString());
            caisse.addCell(session.code() == null ? "" : session.code());
            caisse.addCell(session.caissier());
            caisse.addCell(session.fondDeCaisse().toPlainString());
            caisse.addCell(session.chiffreAffaires().toPlainString());
            caisse.addCell(session.montantEncaisse().toPlainString());
            caisse.addCell(session.reglementsClientsEspeces().toPlainString());
            caisse.addCell(session.sortiesCaisse().toPlainString());
            caisse.addCell(session.montantCloture() == null ? "Ouverte" : session.montantCloture().toPlainString());
        }
        document.add(caisse);
        document.add(new Paragraph("Les encaissements couvrent les règlements enregistrés sur les ventes rattachées à la session. Les règlements clients en espèces et les sorties de caisse (retraits, dépenses) sont indiqués dans des colonnes séparées."));
    }

    private void addJournalTable(Document document, Font section, List<JournalActiviteResponse> activites)
        throws DocumentException {
        document.add(new Paragraph("Journal d'activité", section));
        PdfPTable journal = new PdfPTable(5);
        journal.setWidthPercentage(100);
        addPdfHeader(journal, "Date et heure", "Action", "Entité", "Utilisateur", "Détails");
        for (JournalActiviteResponse activite : activites) {
            journal.addCell(activite.dateActivite().format(DATE_TIME));
            journal.addCell(activite.action());
            journal.addCell(activite.entite());
            journal.addCell(activite.utilisateur() == null ? "" : activite.utilisateur());
            journal.addCell(activite.details() == null ? "" : activite.details());
        }
        document.add(journal);
    }

    private void addHeader(Sheet sheet, CellStyle style, String... labels) {
        addHeader(sheet, 0, style, labels);
    }

    private void addHeader(Sheet sheet, int rowIndex, CellStyle style, String... labels) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < labels.length; i++) {
            row.createCell(i).setCellValue(labels[i]);
            row.getCell(i).setCellStyle(style);
        }
    }

    private void addPdfHeader(PdfPTable table, String... labels) {
        for (String label : labels) table.addCell(new Phrase(label));
    }

    private void autoSize(Sheet sheet, int columns) {
        for (int index = 0; index < columns; index++) sheet.autoSizeColumn(index);
    }
}
