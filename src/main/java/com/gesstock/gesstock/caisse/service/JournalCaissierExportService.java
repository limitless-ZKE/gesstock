package com.gesstock.gesstock.caisse.service;

import com.gesstock.gesstock.caisse.dto.EcritureCaissier;
import com.gesstock.gesstock.caisse.dto.EncaissementHorsEspeces;
import com.gesstock.gesstock.caisse.dto.JournalCaissierJour;
import com.gesstock.gesstock.caisse.dto.JournalCaissierResponse;
import com.gesstock.gesstock.caisse.dto.TotauxJournalCaissier;
import com.gesstock.gesstock.caisse.dto.TypeEcritureCaisse;
import com.gesstock.gesstock.common.export.ExportSupport;
import com.gesstock.gesstock.common.export.TableauPdf;
import com.gesstock.gesstock.entreprise.dto.EntrepriseResponse;
import com.gesstock.gesstock.entreprise.service.EnteteExcel;
import com.gesstock.gesstock.entreprise.service.EntetePdf;
import com.gesstock.gesstock.entreprise.service.EntrepriseService;
import com.gesstock.gesstock.entreprise.service.TailleLogo;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Journal de caisse d'un caissier à imprimer et à signer : un PDF en paysage (une section par journée travaillée, ses lignes, son
 * cumul et ses espèces à remettre) et un classeur Excel (récapitulatif par journée, puis toutes ses lignes à filtrer). Aucune
 * information du tiroir de la journée (fond de caisse, solde, écart) : elles ne sont pas les siennes.
 */
@Service
@Transactional(readOnly = true)
public class JournalCaissierExportService {

    private final JournalCaisseService journalService;
    private final EntrepriseService entrepriseService;

    public JournalCaissierExportService(JournalCaisseService journalService, EntrepriseService entrepriseService) {
        this.journalService = journalService;
        this.entrepriseService = entrepriseService;
    }

    // ------------------------------------------------------------------ PDF

    public byte[] exporterPdf(String caissier, LocalDate debut, LocalDate fin) throws IOException {
        JournalCaissierResponse journal = journalService.journalDuCaissier(caissier, debut, fin);
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            EntrepriseResponse entreprise = entrepriseService.obtenir();
            Document document = ExportSupport.documentPaysage(sortie,
                entreprise.nom() + " · Journal de caisse du caissier " + journal.caissier() + " · " + ExportSupport.periode(debut, fin));
            try {
                EntetePdf.ajouter(document, entreprise, entrepriseService.logo(TailleLogo.ENTETE).map(EntrepriseService.LogoServi::contenu).orElse(null));
                document.add(new Paragraph("Journal de caisse du caissier", ExportSupport.gras(16)));
                document.add(new Paragraph("Caissier : " + journal.caissier(), ExportSupport.gras(11)));
                document.add(new Paragraph(ExportSupport.periode(debut, fin) + " · édité le " + ExportSupport.jourHeure(LocalDateTime.now()), ExportSupport.normal(9)));
                document.add(new Paragraph(" ", ExportSupport.normal(6)));

                if (journal.jours().isEmpty()) {
                    document.add(new Paragraph(journal.caissier() + " n'a effectué aucune opération sur cette période.", ExportSupport.normal(10)));
                } else {
                    if (journal.jours().size() > 1) recapitulatifPdf(document, journal);
                    for (JournalCaissierJour jour : journal.jours()) sectionPdf(document, jour);
                    document.add(new Paragraph(" ", ExportSupport.normal(14)));
                    document.add(new Paragraph("Visa du caissier : ____________________          Visa du responsable : ____________________", ExportSupport.normal(9)));
                }
                document.close();
            } catch (DocumentException exception) {
                throw new IOException("La génération du PDF du journal du caissier a échoué", exception);
            }
            return sortie.toByteArray();
        }
    }

    private static void recapitulatifPdf(Document document, JournalCaissierResponse journal) throws DocumentException {
        document.add(ExportSupport.paragraphe("Récapitulatif de la période", ExportSupport.gras(11), 5));
        TableauPdf tableau = TableauPdf.creer(new float[] {1.2f, 0.9f, 1.5f, 1.6f, 1.2f, 1.6f},
            new boolean[] {false, true, true, true, true, true},
            "Journée", "Ventes", "Ventes espèces", "Règlements espèces", "Sorties", "Espèces à remettre");
        for (JournalCaissierJour jour : journal.jours()) {
            tableau.ligne(ExportSupport.jour(jour.journee()), String.valueOf(jour.nombreVentes()), ExportSupport.montant(jour.ventesEspeces()),
                ExportSupport.montant(jour.reglementsEspeces()), ExportSupport.montant(jour.sorties()), ExportSupport.montant(jour.especesARemettre()));
        }
        TotauxJournalCaissier totaux = journal.totaux();
        tableau.sousTotal("Total", String.valueOf(totaux.nombreVentes()), ExportSupport.montant(totaux.ventesEspeces()),
            ExportSupport.montant(totaux.reglementsEspeces()), ExportSupport.montant(totaux.sorties()), ExportSupport.montant(totaux.especesARemettre()));
        document.add(tableau.table());
        document.add(new Paragraph(" ", ExportSupport.normal(8)));
    }

    private static void sectionPdf(Document document, JournalCaissierJour jour) throws DocumentException {
        Paragraph titre = ExportSupport.paragraphe("Journée du " + ExportSupport.jour(jour.journee()) + " — session " + jour.code(), ExportSupport.gras(11), 1);
        titre.setKeepTogether(true);
        document.add(titre);
        // Une seconde ligne sépare le titre du tableau (collé, il serait rogné par l'en-tête du tableau), comme dans le journal complet.
        document.add(ExportSupport.paragraphe(jour.nombreVentes() + (jour.nombreVentes() > 1 ? " ventes validées" : " vente validée") + " ce jour-là", ExportSupport.normal(8.5f), 6));

        if (!jour.ecritures().isEmpty()) {
            TableauPdf tableau = TableauPdf.creer(new float[] {0.7f, 0.9f, 3.8f, 1.7f, 1.2f, 1.2f, 1.2f},
                new boolean[] {false, false, false, false, true, true, true},
                "Heure", "Opération", "Libellé", "Référence", "Entrée", "Sortie", "Cumul");
            for (EcritureCaissier ecriture : jour.ecritures()) {
                tableau.ligne(ExportSupport.heure(ecriture.date()), libelleType(ecriture.type()), ecriture.libelle(),
                    ecriture.reference() == null ? "" : ecriture.reference(),
                    ecriture.entree() == null ? "" : ExportSupport.montant(ecriture.entree()),
                    ecriture.sortie() == null ? "" : ExportSupport.montant(ecriture.sortie()),
                    ExportSupport.montant(ecriture.cumul()));
            }
            tableau.sousTotal("", "", "Espèces à remettre", "", ExportSupport.montant(jour.ventesEspeces().add(jour.reglementsEspeces())),
                ExportSupport.montant(jour.sorties()), ExportSupport.montant(jour.especesARemettre()));
            document.add(tableau.table());
        }
        String complement = complement(jour);
        if (!complement.isEmpty()) document.add(new Paragraph(complement, ExportSupport.normal(8)));
        document.add(new Paragraph(" ", ExportSupport.normal(8)));
    }

    /** Ce que ce caissier a fait hors du tiroir-caisse ce jour-là : paiements non espèces, ventes à crédit, ventes annulées. */
    private static String complement(JournalCaissierJour jour) {
        StringBuilder texte = new StringBuilder();
        if (!jour.autresEncaissements().isEmpty()) {
            texte.append("Encaissé hors espèces (non compté dans les espèces à remettre) : ").append(horsEspeces(jour)).append(". ");
        }
        if (jour.ventesACredit().signum() > 0) {
            texte.append("Ventes à crédit (rien encaissé) : ").append(ExportSupport.montant(jour.ventesACredit())).append(". ");
        }
        if (jour.nombreVentesAnnulees() > 0) {
            texte.append(jour.nombreVentesAnnulees()).append(jour.nombreVentesAnnulees() > 1 ? " ventes annulées" : " vente annulée").append(" (non comptées). ");
        }
        return texte.toString().trim();
    }

    private static String libelleType(TypeEcritureCaisse type) {
        return switch (type) {
            case OUVERTURE -> "Ouverture";
            case VENTE -> "Vente";
            case REGLEMENT -> "Règlement";
            case SORTIE -> "Sortie";
            case ENTREE_FONDS -> "Entrée de fonds";
        };
    }

    // ------------------------------------------------------------------ Excel

    public byte[] exporterExcel(String caissier, LocalDate debut, LocalDate fin) throws IOException {
        JournalCaissierResponse journal = journalService.journalDuCaissier(caissier, debut, fin);
        try (Workbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Font fort = classeur.createFont();
            fort.setBold(true);
            Font grand = classeur.createFont();
            grand.setBold(true);
            grand.setFontHeightInPoints((short) 13);
            CellStyle entete = classeur.createCellStyle();
            entete.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            entete.setFont(fort);
            CellStyle titre = classeur.createCellStyle();
            titre.setFont(grand);
            CellStyle gras = classeur.createCellStyle();
            gras.setFont(fort);
            var format = classeur.createDataFormat();
            CellStyle jourStyle = classeur.createCellStyle();
            jourStyle.setDataFormat(format.getFormat("dd/mm/yyyy"));
            CellStyle heure = classeur.createCellStyle();
            heure.setDataFormat(format.getFormat("hh:mm"));
            CellStyle entier = classeur.createCellStyle();
            entier.setDataFormat(format.getFormat("#,##0"));
            CellStyle decimal = classeur.createCellStyle();
            decimal.setDataFormat(format.getFormat("#,##0.00"));
            CellStyle totalEntier = classeur.createCellStyle();
            totalEntier.cloneStyleFrom(entier);
            totalEntier.setFont(fort);

            EntrepriseResponse entreprise = entrepriseService.obtenir();
            byte[] logo = entrepriseService.logo(TailleLogo.MINIATURE).map(EntrepriseService.LogoServi::contenu).orElse(null);

            Sheet recap = classeur.createSheet("Récapitulatif");
            int ligne = EnteteExcel.ecrire(classeur, recap, entreprise, logo);
            recap.addMergedRegion(new CellRangeAddress(ligne, ligne, 0, 5));
            Row titreLigne = recap.createRow(ligne++);
            titreLigne.createCell(0).setCellValue("Journal de caisse du caissier");
            titreLigne.getCell(0).setCellStyle(titre);
            recap.addMergedRegion(new CellRangeAddress(ligne, ligne, 0, 5));
            Row caissierLigne = recap.createRow(ligne++);
            caissierLigne.createCell(0).setCellValue("Caissier : " + journal.caissier());
            caissierLigne.getCell(0).setCellStyle(gras);
            recap.addMergedRegion(new CellRangeAddress(ligne, ligne, 0, 5));
            recap.createRow(ligne++).createCell(0).setCellValue(ExportSupport.periode(debut, fin));
            ligne++;
            String[] colonnes = {"Journée", "Session", "Ventes", "Ventes espèces", "Règlements clients (espèces)", "Sorties", "Espèces à remettre",
                "Hors espèces", "Ventes à crédit", "Ventes annulées"};
            Row enteteLigne = recap.createRow(ligne++);
            for (int i = 0; i < colonnes.length; i++) {
                enteteLigne.createCell(i).setCellValue(colonnes[i]);
                enteteLigne.getCell(i).setCellStyle(entete);
            }
            for (JournalCaissierJour jour : journal.jours()) {
                Row row = recap.createRow(ligne++);
                var celluleJour = row.createCell(0);
                celluleJour.setCellValue(jour.journee());
                celluleJour.setCellStyle(jourStyle);
                row.createCell(1).setCellValue(jour.code() == null ? "" : jour.code());
                row.createCell(2).setCellValue(jour.nombreVentes());
                montant(row, 3, jour.ventesEspeces(), entier, decimal);
                montant(row, 4, jour.reglementsEspeces(), entier, decimal);
                montant(row, 5, jour.sorties(), entier, decimal);
                montant(row, 6, jour.especesARemettre(), entier, decimal);
                row.createCell(7).setCellValue(horsEspeces(jour));
                montant(row, 8, jour.ventesACredit(), entier, decimal);
                row.createCell(9).setCellValue(jour.nombreVentesAnnulees());
            }
            TotauxJournalCaissier totaux = journal.totaux();
            Row total = recap.createRow(ligne);
            total.createCell(0).setCellValue("Total");
            total.getCell(0).setCellStyle(gras);
            total.createCell(2).setCellValue(totaux.nombreVentes());
            for (int colonne : new int[] {3, 4, 5, 6, 8}) {
                BigDecimal valeur = switch (colonne) {
                    case 3 -> totaux.ventesEspeces();
                    case 4 -> totaux.reglementsEspeces();
                    case 5 -> totaux.sorties();
                    case 6 -> totaux.especesARemettre();
                    default -> totaux.ventesACredit();
                };
                var cellule = total.createCell(colonne);
                cellule.setCellValue(valeur.doubleValue());
                cellule.setCellStyle(totalEntier);
            }
            total.createCell(9).setCellValue(totaux.nombreVentesAnnulees());
            for (int i = 0; i < colonnes.length; i++) recap.autoSizeColumn(i);

            Sheet ecritures = classeur.createSheet("Écritures");
            String[] colonnesEcritures = {"Journée", "Heure", "Session", "Opération", "Libellé", "Référence", "Entrée", "Sortie", "Cumul"};
            Row enteteEcritures = ecritures.createRow(0);
            for (int i = 0; i < colonnesEcritures.length; i++) {
                enteteEcritures.createCell(i).setCellValue(colonnesEcritures[i]);
                enteteEcritures.getCell(i).setCellStyle(entete);
            }
            int rang = 1;
            for (JournalCaissierJour jour : journal.jours()) {
                for (EcritureCaissier ecriture : jour.ecritures()) {
                    Row row = ecritures.createRow(rang++);
                    var celluleJour = row.createCell(0);
                    celluleJour.setCellValue(jour.journee());
                    celluleJour.setCellStyle(jourStyle);
                    var celluleHeure = row.createCell(1);
                    celluleHeure.setCellValue(ecriture.date());
                    celluleHeure.setCellStyle(heure);
                    row.createCell(2).setCellValue(jour.code() == null ? "" : jour.code());
                    row.createCell(3).setCellValue(libelleType(ecriture.type()));
                    row.createCell(4).setCellValue(ecriture.libelle());
                    row.createCell(5).setCellValue(ecriture.reference() == null ? "" : ecriture.reference());
                    if (ecriture.entree() != null) montant(row, 6, ecriture.entree(), entier, decimal);
                    if (ecriture.sortie() != null) montant(row, 7, ecriture.sortie(), entier, decimal);
                    montant(row, 8, ecriture.cumul(), entier, decimal);
                }
            }
            for (int i = 0; i < colonnesEcritures.length; i++) ecritures.autoSizeColumn(i);

            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }

    /** Un montant est un vrai nombre (totalisable dans Excel) ; les centimes ne s'affichent que s'il y en a. */
    private static void montant(Row row, int colonne, BigDecimal valeur, CellStyle entier, CellStyle decimal) {
        var cellule = row.createCell(colonne);
        cellule.setCellValue(valeur.doubleValue());
        cellule.setCellStyle(valeur.stripTrailingZeros().scale() <= 0 ? entier : decimal);
    }

    /** « Mobile Money 5 000 ; Carte 2 000 » : ce que ce caissier a encaissé hors espèces ce jour-là. */
    private static String horsEspeces(JournalCaissierJour jour) {
        return jour.autresEncaissements().stream()
            .map((EncaissementHorsEspeces e) -> ExportSupport.libelleMode(e.mode()) + " " + ExportSupport.montant(e.montant()))
            .collect(Collectors.joining(" ; "));
    }
}
