package com.gesstock.gesstock.caisse.service;

import com.gesstock.gesstock.caisse.dto.EcritureCaisse;
import com.gesstock.gesstock.caisse.dto.JournalCaisseResponse;
import com.gesstock.gesstock.caisse.dto.JournalSessionCaisse;
import com.gesstock.gesstock.caisse.dto.TotauxJournalCaisse;
import com.gesstock.gesstock.caisse.dto.TypeEcritureCaisse;
import com.gesstock.gesstock.common.export.ExportSupport;
import com.gesstock.gesstock.common.export.TableauPdf;
import com.gesstock.gesstock.entities.StatutSessionCaisse;
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
 * Documents imprimables du journal de caisse : un PDF en paysage prêt à signer (une section par journée, écritures et solde
 * ligne à ligne, espèces comptées et écart) et un classeur Excel (récapitulatif par journée, puis toutes les écritures à filtrer).
 */
@Service
@Transactional(readOnly = true)
public class JournalCaisseExportService {

    private final JournalCaisseService journalService;
    private final EntrepriseService entrepriseService;

    public JournalCaisseExportService(JournalCaisseService journalService, EntrepriseService entrepriseService) {
        this.journalService = journalService;
        this.entrepriseService = entrepriseService;
    }

    // ------------------------------------------------------------------ PDF

    public byte[] exporterPdf(LocalDate debut, LocalDate fin) throws IOException {
        JournalCaisseResponse journal = journalService.journal(debut, fin);
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            EntrepriseResponse entreprise = entrepriseService.obtenir();
            Document document = ExportSupport.documentPaysage(sortie, entreprise.nom() + " · Journal de caisse · " + ExportSupport.periode(debut, fin));
            try {
                EntetePdf.ajouter(document, entreprise, entrepriseService.logo(TailleLogo.ENTETE).map(EntrepriseService.LogoServi::contenu).orElse(null));
                document.add(new Paragraph("Journal de caisse", ExportSupport.gras(16)));
                document.add(new Paragraph(ExportSupport.periode(debut, fin) + " · édité le " + ExportSupport.jourHeure(LocalDateTime.now()), ExportSupport.normal(9)));
                document.add(new Paragraph(" ", ExportSupport.normal(6)));

                if (journal.sessions().isEmpty()) {
                    document.add(new Paragraph("Aucune session de caisse n'a été ouverte sur cette période.", ExportSupport.normal(10)));
                } else {
                    if (journal.sessions().size() > 1) recapitulatifPdf(document, journal);
                    for (JournalSessionCaisse session : journal.sessions()) sectionPdf(document, session);
                    document.add(new Paragraph(" ", ExportSupport.normal(14)));
                    document.add(new Paragraph("Visa du caissier : ____________________          Visa du responsable : ____________________", ExportSupport.normal(9)));
                }
                document.close();
            } catch (DocumentException exception) {
                throw new IOException("La génération du PDF du journal de caisse a échoué", exception);
            }
            return sortie.toByteArray();
        }
    }

    private static void recapitulatifPdf(Document document, JournalCaisseResponse journal) throws DocumentException {
        document.add(ExportSupport.paragraphe("Récapitulatif de la période", ExportSupport.gras(11), 5));
        TableauPdf tableau = TableauPdf.creer(new float[] {1f, 1.7f, 1.2f, 1.3f, 1.3f, 1.1f, 1.4f, 1.3f, 1.1f},
            new boolean[] {false, false, true, true, true, true, true, true, true},
            "Journée", "Caissier", "Fond de caisse", "Ventes espèces", "Règlements espèces", "Sorties", "Solde théorique", "Espèces comptées", "Écart");
        for (JournalSessionCaisse session : journal.sessions()) {
            tableau.ligne(ExportSupport.jour(session.journee()), session.ouvertePar() == null ? "" : session.ouvertePar(),
                ExportSupport.montant(session.fondDeCaisse()), ExportSupport.montant(session.encaissementsEspeces()),
                ExportSupport.montant(session.reglementsClientsEspeces()), ExportSupport.montant(session.sorties()),
                ExportSupport.montant(session.soldeTheorique()),
                session.montantCloture() == null ? "non clôturée" : ExportSupport.montant(session.montantCloture()),
                session.ecart() == null ? "—" : ExportSupport.montant(session.ecart()));
        }
        TotauxJournalCaisse totaux = journal.totaux();
        tableau.sousTotal("Total", totaux.nombreSessions() + " journées", ExportSupport.montant(totaux.fondsDeCaisse()),
            ExportSupport.montant(totaux.encaissementsEspeces()), ExportSupport.montant(totaux.reglementsClientsEspeces()),
            ExportSupport.montant(totaux.sorties()), ExportSupport.montant(totaux.soldeTheorique()),
            ExportSupport.montant(totaux.especesComptees()), ExportSupport.montant(totaux.ecarts()));
        document.add(tableau.table());
        if (totaux.sessionsNonCloturees() > 0) {
            document.add(new Paragraph(totaux.sessionsNonCloturees() + " journée(s) non clôturée(s) : leurs espèces comptées et leur écart ne figurent pas dans les totaux.",
                ExportSupport.normal(8)));
        }
        document.add(new Paragraph(" ", ExportSupport.normal(8)));
    }

    private static void sectionPdf(Document document, JournalSessionCaisse session) throws DocumentException {
        String ouverture = "ouverte par " + (session.ouvertePar() == null ? "—" : session.ouvertePar()) + " à " + ExportSupport.heure(session.ouverteLe());
        String cloture = session.statut() == StatutSessionCaisse.FERMEE
            ? "clôturée par " + (session.fermeePar() == null ? "—" : session.fermeePar()) + " à " + ExportSupport.heure(session.fermeeLe())
            : session.journeeDepassee() ? "NON CLÔTURÉE (journée dépassée)" : "en cours";
        Paragraph titre = ExportSupport.paragraphe("Journée du " + ExportSupport.jour(session.journee()) + " — session " + session.code(), ExportSupport.gras(11), 1);
        titre.setKeepTogether(true);
        document.add(titre);
        document.add(ExportSupport.paragraphe(ouverture + " · " + cloture, ExportSupport.normal(8.5f), 6));

        TableauPdf tableau = TableauPdf.creer(new float[] {0.7f, 0.9f, 3.4f, 1.6f, 1.2f, 1.1f, 1.1f, 1.2f},
            new boolean[] {false, false, false, false, false, true, true, true},
            "Heure", "Opération", "Libellé", "Référence", "Par", "Entrée", "Sortie", "Solde");
        for (EcritureCaisse ecriture : session.ecritures()) {
            tableau.ligne(ExportSupport.heure(ecriture.date()), libelleType(ecriture.type()), ecriture.libelle(),
                ecriture.reference() == null ? "" : ecriture.reference(), ecriture.utilisateur() == null ? "" : ecriture.utilisateur(),
                ecriture.entree() == null ? "" : ExportSupport.montant(ecriture.entree()),
                ecriture.sortie() == null ? "" : ExportSupport.montant(ecriture.sortie()),
                ExportSupport.montant(ecriture.solde()));
        }
        tableau.sousTotal("", "", "Solde théorique des espèces", "", "", ExportSupport.montant(session.encaissementsEspeces().add(session.reglementsClientsEspeces()).add(session.fondDeCaisse())),
            ExportSupport.montant(session.sorties()), ExportSupport.montant(session.soldeTheorique()));
        if (session.montantCloture() != null) {
            tableau.sousTotal("", "", "Espèces comptées à la clôture", "", "", "", "", ExportSupport.montant(session.montantCloture()));
            tableau.sousTotal("", "", session.ecart().signum() == 0 ? "Écart : aucun" : session.ecart().signum() > 0 ? "Écart : excédent" : "Écart : manquant",
                "", "", "", "", ExportSupport.montant(session.ecart()));
        }
        document.add(tableau.table());
        String complement = complement(session);
        if (!complement.isEmpty()) document.add(new Paragraph(complement, ExportSupport.normal(8)));
        document.add(new Paragraph(" ", ExportSupport.normal(8)));
    }

    /** Ce qui s'est passé hors du tiroir-caisse ce jour-là : paiements non espèces, ventes à crédit, ventes annulées. */
    static String complement(JournalSessionCaisse session) {
        StringBuilder texte = new StringBuilder();
        if (!session.autresEncaissements().isEmpty()) {
            texte.append("Encaissé hors espèces (non compté ci-dessus) : ").append(horsEspeces(session)).append(". ");
        }
        if (session.ventesACredit().signum() > 0) {
            texte.append("Ventes à crédit (rien encaissé) : ").append(ExportSupport.montant(session.ventesACredit())).append(". ");
        }
        if (session.nombreVentesAnnulees() > 0) {
            texte.append(session.nombreVentesAnnulees()).append(session.nombreVentesAnnulees() > 1 ? " ventes annulées" : " vente annulée").append(" (non comptées). ");
        }
        return texte.toString().trim();
    }

    private static String libelleType(TypeEcritureCaisse type) {
        return switch (type) {
            case OUVERTURE -> "Ouverture";
            case VENTE -> "Vente";
            case REGLEMENT -> "Règlement";
            case SORTIE -> "Sortie";
        };
    }

    // ------------------------------------------------------------------ Excel

    public byte[] exporterExcel(LocalDate debut, LocalDate fin) throws IOException {
        JournalCaisseResponse journal = journalService.journal(debut, fin);
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
            CellStyle jour = classeur.createCellStyle();
            jour.setDataFormat(format.getFormat("dd/mm/yyyy"));
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
            titreLigne.createCell(0).setCellValue("Journal de caisse");
            titreLigne.getCell(0).setCellStyle(titre);
            recap.addMergedRegion(new CellRangeAddress(ligne, ligne, 0, 5));
            recap.createRow(ligne++).createCell(0).setCellValue(ExportSupport.periode(debut, fin));
            ligne++;
            String[] colonnes = {"Journée", "Session", "Ouverte par", "Clôturée par", "Statut", "Fond de caisse", "Ventes", "Ventes espèces",
                "Règlements clients (espèces)", "Sorties", "Solde théorique", "Espèces comptées", "Écart", "Hors espèces", "Ventes à crédit", "Ventes annulées"};
            Row enteteLigne = recap.createRow(ligne++);
            for (int i = 0; i < colonnes.length; i++) {
                enteteLigne.createCell(i).setCellValue(colonnes[i]);
                enteteLigne.getCell(i).setCellStyle(entete);
            }
            for (JournalSessionCaisse session : journal.sessions()) {
                Row row = recap.createRow(ligne++);
                var celluleJour = row.createCell(0);
                celluleJour.setCellValue(session.journee());
                celluleJour.setCellStyle(jour);
                row.createCell(1).setCellValue(session.code() == null ? "" : session.code());
                row.createCell(2).setCellValue(session.ouvertePar() == null ? "" : session.ouvertePar());
                row.createCell(3).setCellValue(session.fermeePar() == null ? "" : session.fermeePar());
                row.createCell(4).setCellValue(session.statut() == StatutSessionCaisse.FERMEE ? "Clôturée" : session.journeeDepassee() ? "Non clôturée" : "En cours");
                montant(row, 5, session.fondDeCaisse(), entier, decimal);
                row.createCell(6).setCellValue(session.nombreVentes());
                montant(row, 7, session.encaissementsEspeces(), entier, decimal);
                montant(row, 8, session.reglementsClientsEspeces(), entier, decimal);
                montant(row, 9, session.sorties(), entier, decimal);
                montant(row, 10, session.soldeTheorique(), entier, decimal);
                if (session.montantCloture() != null) montant(row, 11, session.montantCloture(), entier, decimal);
                if (session.ecart() != null) montant(row, 12, session.ecart(), entier, decimal);
                row.createCell(13).setCellValue(horsEspeces(session));
                montant(row, 14, session.ventesACredit(), entier, decimal);
                row.createCell(15).setCellValue(session.nombreVentesAnnulees());
            }
            TotauxJournalCaisse totaux = journal.totaux();
            Row total = recap.createRow(ligne);
            total.createCell(0).setCellValue("Total");
            total.getCell(0).setCellStyle(gras);
            total.createCell(6).setCellValue(totaux.nombreVentes());
            for (int colonne : new int[] {5, 7, 8, 9, 10, 11, 12, 14}) {
                BigDecimal valeur = switch (colonne) {
                    case 5 -> totaux.fondsDeCaisse();
                    case 7 -> totaux.encaissementsEspeces();
                    case 8 -> totaux.reglementsClientsEspeces();
                    case 9 -> totaux.sorties();
                    case 10 -> totaux.soldeTheorique();
                    case 11 -> totaux.especesComptees();
                    case 12 -> totaux.ecarts();
                    default -> totaux.ventesACredit();
                };
                var cellule = total.createCell(colonne);
                cellule.setCellValue(valeur.doubleValue());
                cellule.setCellStyle(totalEntier);
            }
            total.createCell(15).setCellValue(totaux.nombreVentesAnnulees());
            for (int i = 0; i < colonnes.length; i++) recap.autoSizeColumn(i);

            Sheet ecritures = classeur.createSheet("Écritures");
            String[] colonnesEcritures = {"Journée", "Heure", "Session", "Opération", "Libellé", "Référence", "Par", "Entrée", "Sortie", "Solde"};
            Row enteteEcritures = ecritures.createRow(0);
            for (int i = 0; i < colonnesEcritures.length; i++) {
                enteteEcritures.createCell(i).setCellValue(colonnesEcritures[i]);
                enteteEcritures.getCell(i).setCellStyle(entete);
            }
            int rang = 1;
            for (JournalSessionCaisse session : journal.sessions()) {
                for (EcritureCaisse ecriture : session.ecritures()) {
                    Row row = ecritures.createRow(rang++);
                    var celluleJour = row.createCell(0);
                    celluleJour.setCellValue(session.journee());
                    celluleJour.setCellStyle(jour);
                    var celluleHeure = row.createCell(1);
                    celluleHeure.setCellValue(ecriture.date());
                    celluleHeure.setCellStyle(heure);
                    row.createCell(2).setCellValue(session.code() == null ? "" : session.code());
                    row.createCell(3).setCellValue(libelleType(ecriture.type()));
                    row.createCell(4).setCellValue(ecriture.libelle());
                    row.createCell(5).setCellValue(ecriture.reference() == null ? "" : ecriture.reference());
                    row.createCell(6).setCellValue(ecriture.utilisateur() == null ? "" : ecriture.utilisateur());
                    if (ecriture.entree() != null) montant(row, 7, ecriture.entree(), entier, decimal);
                    if (ecriture.sortie() != null) montant(row, 8, ecriture.sortie(), entier, decimal);
                    montant(row, 9, ecriture.solde(), entier, decimal);
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

    /** « Mobile Money 5 000 ; Carte 2 000 » : ce qui est entré hors espèces ce jour-là. */
    private static String horsEspeces(JournalSessionCaisse session) {
        return session.autresEncaissements().stream()
            .map(e -> ExportSupport.libelleMode(e.mode()) + " " + ExportSupport.montant(e.montant()))
            .collect(Collectors.joining(" ; "));
    }
}
