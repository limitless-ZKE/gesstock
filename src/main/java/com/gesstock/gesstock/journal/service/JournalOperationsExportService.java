package com.gesstock.gesstock.journal.service;

import com.gesstock.gesstock.common.export.ExportSupport;
import com.gesstock.gesstock.common.export.TableauPdf;
import com.gesstock.gesstock.entities.JournalActivite;
import com.gesstock.gesstock.entreprise.dto.EntrepriseResponse;
import com.gesstock.gesstock.entreprise.service.EnteteExcel;
import com.gesstock.gesstock.entreprise.service.EntetePdf;
import com.gesstock.gesstock.entreprise.service.EntrepriseService;
import com.gesstock.gesstock.entreprise.service.TailleLogo;
import com.gesstock.gesstock.journal.dto.JournalOperationsResponse;
import com.gesstock.gesstock.journal.dto.NombreParLibelle;
import com.gesstock.gesstock.journal.dto.OperationJournal;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Le journal des opérations à imprimer (PDF en paysage) ou à retravailler (Excel) : dans l'ordre du temps, avec un total par
 * journée quand la période en couvre plusieurs, et sous le titre l'utilisateur (ou « tous les utilisateurs ») et la famille choisis.
 */
@Service
@Transactional(readOnly = true)
public class JournalOperationsExportService {

    private final JournalOperationsService journalService;
    private final EntrepriseService entrepriseService;

    public JournalOperationsExportService(JournalOperationsService journalService, EntrepriseService entrepriseService) {
        this.journalService = journalService;
        this.entrepriseService = entrepriseService;
    }

    public byte[] exporterPdf(LocalDate debut, LocalDate fin, String utilisateur, OperationsConnues.Famille famille) throws IOException {
        JournalOperationsResponse journal = chronologique(debut, fin, utilisateur, famille);
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            EntrepriseResponse entreprise = entrepriseService.obtenir();
            Document document = ExportSupport.documentPaysage(sortie, entreprise.nom() + " · Journal des opérations · " + ExportSupport.periode(debut, fin));
            try {
                EntetePdf.ajouter(document, entreprise, entrepriseService.logo(TailleLogo.ENTETE).map(EntrepriseService.LogoServi::contenu).orElse(null));
                document.add(new Paragraph("Journal des opérations", ExportSupport.gras(16)));
                document.add(new Paragraph(sousTitre(debut, fin, utilisateur, famille) + " · édité le " + ExportSupport.jourHeure(LocalDateTime.now()),
                    ExportSupport.normal(9)));
                document.add(new Paragraph(" ", ExportSupport.normal(6)));

                document.add(TableauPdf.creer(new float[] {0.9f, 3f, 4f}, new boolean[] {true, false, false},
                        "Opérations", "Par utilisateur", "Les plus fréquentes")
                    .ligne(Long.toString(journal.nombre()), resume(journal.parUtilisateur(), 8), resume(journal.parOperation(), 6))
                    .table());

                boolean plusieursJours = !debut.equals(fin);
                TableauPdf tableau = TableauPdf.creer(new float[] {plusieursJours ? 1.25f : 0.7f, 1.1f, 1.9f, 5.3f},
                    new boolean[] {false, false, false, false},
                    plusieursJours ? "Date et heure" : "Heure", "Utilisateur", "Opération", "Détail");
                LocalDate jourCourant = null;
                long nombreDuJour = 0;
                for (OperationJournal operation : journal.operations()) {
                    LocalDate jour = operation.date().toLocalDate();
                    if (plusieursJours && !jour.equals(jourCourant)) {
                        if (jourCourant != null) sousTotal(tableau, jourCourant, nombreDuJour);
                        jourCourant = jour;
                        nombreDuJour = 0;
                    }
                    nombreDuJour++;
                    tableau.ligne(
                        (plusieursJours ? ExportSupport.jour(jour) + " " : "") + ExportSupport.heure(operation.date()),
                        operation.utilisateur() == null ? "—" : operation.utilisateur(),
                        operation.operation(),
                        operation.details() == null ? "" : operation.details());
                }
                if (plusieursJours && jourCourant != null) sousTotal(tableau, jourCourant, nombreDuJour);
                if (journal.operations().isEmpty()) {
                    tableau.ligne("", "", "Aucune opération sur cette période.", "");
                }
                document.add(tableau.table());
                document.add(new Paragraph("Chaque opération est enregistrée au moment où elle est faite, avec son auteur. Les connexions et les changements "
                    + "d'articles, de catégories, d'unités de vente, de clients et de comptes ne figurent qu'à partir de la mise à jour de GesStock "
                    + "qui les a ajoutés.", ExportSupport.normal(8)));
                document.close();
            } catch (DocumentException exception) {
                throw new IOException("La génération du PDF du journal des opérations a échoué", exception);
            }
            return sortie.toByteArray();
        }
    }

    public byte[] exporterExcel(LocalDate debut, LocalDate fin, String utilisateur, OperationsConnues.Famille famille) throws IOException {
        JournalOperationsResponse journal = chronologique(debut, fin, utilisateur, famille);
        try (Workbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Font fort = classeur.createFont();
            fort.setBold(true);
            CellStyle entete = classeur.createCellStyle();
            entete.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            entete.setFont(fort);
            Font grand = classeur.createFont();
            grand.setBold(true);
            grand.setFontHeightInPoints((short) 13);
            CellStyle titre = classeur.createCellStyle();
            titre.setFont(grand);
            CellStyle gras = classeur.createCellStyle();
            gras.setFont(fort);
            var format = classeur.createDataFormat();
            CellStyle jour = classeur.createCellStyle();
            jour.setDataFormat(format.getFormat("dd/mm/yyyy"));
            CellStyle heure = classeur.createCellStyle();
            heure.setDataFormat(format.getFormat("hh:mm"));

            Sheet feuille = classeur.createSheet("Journal des opérations");
            byte[] logo = entrepriseService.logo(TailleLogo.MINIATURE).map(EntrepriseService.LogoServi::contenu).orElse(null);
            int ligne = EnteteExcel.ecrire(classeur, feuille, entrepriseService.obtenir(), logo);
            feuille.addMergedRegion(new CellRangeAddress(ligne, ligne, 0, 5));
            Row rangTitre = feuille.createRow(ligne++);
            rangTitre.createCell(0).setCellValue("Journal des opérations");
            rangTitre.getCell(0).setCellStyle(titre);
            feuille.addMergedRegion(new CellRangeAddress(ligne, ligne, 0, 5));
            feuille.createRow(ligne++).createCell(0).setCellValue(sousTitre(debut, fin, utilisateur, famille));
            ligne++;
            ligne = valeur(feuille, ligne, "Opérations", journal.nombre(), gras);
            for (NombreParLibelle compte : journal.parUtilisateur()) {
                ligne = valeur(feuille, ligne, "Dont " + compte.libelle(), compte.nombre(), gras);
            }
            ligne++;
            String[] colonnes = {"Date", "Heure", "Utilisateur", "Famille", "Opération", "Détail", "Élément", "Poste"};
            Row rangEntete = feuille.createRow(ligne++);
            for (int i = 0; i < colonnes.length; i++) {
                rangEntete.createCell(i).setCellValue(colonnes[i]);
                rangEntete.getCell(i).setCellStyle(entete);
            }
            for (OperationJournal operation : journal.operations()) {
                Row rang = feuille.createRow(ligne++);
                rang.createCell(0).setCellValue(operation.date().toLocalDate());
                rang.getCell(0).setCellStyle(jour);
                rang.createCell(1).setCellValue(operation.date());
                rang.getCell(1).setCellStyle(heure);
                rang.createCell(2).setCellValue(operation.utilisateur() == null ? "" : operation.utilisateur());
                rang.createCell(3).setCellValue(operation.famille());
                rang.createCell(4).setCellValue(operation.operation());
                rang.createCell(5).setCellValue(operation.details() == null ? "" : operation.details());
                rang.createCell(6).setCellValue(operation.element());
                rang.createCell(7).setCellValue(operation.poste() == null ? "" : operation.poste());
            }
            for (int colonne = 0; colonne < colonnes.length; colonne++) {
                if (colonne != 5) feuille.autoSizeColumn(colonne);
            }
            feuille.setColumnWidth(5, 90 * 256);   // le détail peut être long : largeur fixe plutôt qu'une colonne de 300 caractères
            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }

    /** Pour un document, les opérations se lisent dans l'ordre du temps. */
    private JournalOperationsResponse chronologique(LocalDate debut, LocalDate fin, String utilisateur, OperationsConnues.Famille famille) {
        List<JournalActivite> operations = new ArrayList<>(journalService.charger(debut, fin, utilisateur, famille,
            JournalOperationsService.OPERATIONS_DOCUMENT_MAX, "l'imprimer"));
        operations.sort(Comparator.comparing(JournalActivite::getDateActivite).thenComparing(JournalActivite::getId));
        return journalService.construire(debut, fin, operations);
    }

    /** « 1er au 31 octobre 2026 · utilisateur : marie · Ventes » : on sait toujours de quoi parle le document. */
    private static String sousTitre(LocalDate debut, LocalDate fin, String utilisateur, OperationsConnues.Famille famille) {
        return ExportSupport.periode(debut, fin) + " · " + (utilisateur == null ? "tous les utilisateurs" : "utilisateur : " + utilisateur)
            + (famille == null ? "" : " · " + famille.libelle());
    }

    /** « admin 12 · marie 30 » : les premiers d'une liste de nombres, puis « … » s'il y en a d'autres. */
    private static String resume(List<NombreParLibelle> nombres, int combien) {
        if (nombres.isEmpty()) return "—";
        String debut = nombres.stream().limit(combien).map(nombre -> nombre.libelle() + " " + nombre.nombre()).collect(Collectors.joining(" · "));
        return nombres.size() > combien ? debut + " · …" : debut;
    }

    private static void sousTotal(TableauPdf tableau, LocalDate jour, long nombre) {
        tableau.sousTotal("", "Total du " + ExportSupport.jour(jour), nombre + (nombre > 1 ? " opérations" : " opération"), "");
    }

    private static int valeur(Sheet feuille, int ligne, String libelle, long valeur, CellStyle style) {
        feuille.addMergedRegion(new CellRangeAddress(ligne, ligne, 0, 2));
        Row rang = feuille.createRow(ligne);
        rang.createCell(0).setCellValue(libelle);
        rang.getCell(0).setCellStyle(style);
        rang.createCell(3).setCellValue(valeur);
        return ligne + 1;
    }
}
