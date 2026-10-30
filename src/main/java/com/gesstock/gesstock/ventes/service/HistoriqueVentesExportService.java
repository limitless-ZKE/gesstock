package com.gesstock.gesstock.ventes.service;

import com.gesstock.gesstock.common.export.ExportSupport;
import com.gesstock.gesstock.common.export.TableauPdf;
import com.gesstock.gesstock.entities.LigneVente;
import com.gesstock.gesstock.entities.ReglementClient;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.entities.Vente;
import com.gesstock.gesstock.entreprise.dto.EntrepriseResponse;
import com.gesstock.gesstock.entreprise.service.EnteteExcel;
import com.gesstock.gesstock.entreprise.service.EntetePdf;
import com.gesstock.gesstock.entreprise.service.EntrepriseService;
import com.gesstock.gesstock.entreprise.service.TailleLogo;
import com.gesstock.gesstock.ventes.dto.HistoriqueVentesResponse;
import com.gesstock.gesstock.ventes.dto.LigneHistoriqueVente;
import com.gesstock.gesstock.ventes.dto.LigneReglementDette;
import com.gesstock.gesstock.ventes.dto.LigneVenteSoldee;
import com.gesstock.gesstock.ventes.dto.ReglementsDettesResponse;
import com.gesstock.gesstock.ventes.dto.ResumeHistoriqueVentes;
import com.gesstock.gesstock.ventes.dto.ResumeReglementsDettes;
import com.gesstock.gesstock.ventes.dto.ResumeVentesSoldees;
import com.gesstock.gesstock.ventes.dto.TotalParMode;
import com.gesstock.gesstock.ventes.dto.VentesSoldeesResponse;
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Documents imprimables des historiques : toutes les ventes (classeur Excel avec une feuille des articles vendus, PDF en paysage),
 * les ventes soldées et les règlements de dettes ; pour tous les vendeurs ou pour un seul, toujours nommé sous le titre. Les lignes y
 * sont dans l'ordre du temps, avec un sous-total par journée quand la période en couvre plusieurs ; les ventes annulées sont listées
 * mais ne comptent pas dans les totaux.
 */
@Service
@Transactional(readOnly = true)
public class HistoriqueVentesExportService {

    private final HistoriqueVentesService historiqueService;
    private final HistoriqueReglementsService reglementsService;
    private final EntrepriseService entrepriseService;

    public HistoriqueVentesExportService(HistoriqueVentesService historiqueService, HistoriqueReglementsService reglementsService,
                                         EntrepriseService entrepriseService) {
        this.historiqueService = historiqueService;
        this.reglementsService = reglementsService;
        this.entrepriseService = entrepriseService;
    }

    public byte[] exporterExcel(LocalDate debut, LocalDate fin) throws IOException {
        return exporterExcel(debut, fin, null);
    }

    /** @param vendeur le compte dont on imprime les ventes, ou {@code null} pour tous les vendeurs */
    public byte[] exporterExcel(LocalDate debut, LocalDate fin, String vendeur) throws IOException {
        List<Vente> ventes = chronologique(historiqueService.charger(debut, fin, vendeur, HistoriqueVentesService.VENTES_DOCUMENT_MAX, "l'imprimer"));
        HistoriqueVentesResponse historique = historiqueService.construire(debut, fin, ventes);
        try (Workbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Styles styles = new Styles(classeur);
            EntrepriseResponse entreprise = entrepriseService.obtenir();
            byte[] logo = entrepriseService.logo(TailleLogo.MINIATURE).map(EntrepriseService.LogoServi::contenu).orElse(null);

            Sheet feuille = classeur.createSheet("Ventes");
            int ligne = EnteteExcel.ecrire(classeur, feuille, entreprise, logo);
            ligne = titre(feuille, ligne, "Historique des ventes", sousTitre(debut, fin, vendeur), styles);
            ResumeHistoriqueVentes resume = historique.resume();
            ligne = valeur(feuille, ligne, "Ventes validées", resume.nombreValidees(), styles);
            ligne = montant(feuille, ligne, "Chiffre d'affaires TTC", resume.chiffreAffaires(), styles);
            ligne = montant(feuille, ligne, "Remises accordées", resume.remises(), styles);
            ligne = valeur(feuille, ligne, "Ventes annulées (non comptées)", resume.nombreAnnulees(), styles);
            ligne++;
            String[] entetes = {"Date", "Heure", "N° de vente", "Client", "Caissier", "Paiement", "Articles", "Remises", "Total TTC", "Statut"};
            ligne = entetes(feuille, ligne, styles.entete, entetes);
            for (LigneHistoriqueVente vente : historique.ventes()) {
                Row row = feuille.createRow(ligne++);
                cellule(row, 0, vente.dateVente().toLocalDate(), styles.jour);
                cellule(row, 1, vente.dateVente(), styles.heure);
                row.createCell(2).setCellValue(vente.code());
                row.createCell(3).setCellValue(vente.client() == null ? "Client de passage" : vente.client());
                row.createCell(4).setCellValue(vente.vendeur() == null ? "" : vente.vendeur());
                row.createCell(5).setCellValue(vente.aCredit() ? "À crédit" : ExportSupport.libelleMode(vente.modeReglement()));
                row.createCell(6).setCellValue(vente.nombreArticles());
                nombre(row, 7, vente.remises(), styles);
                nombre(row, 8, vente.montantTotal(), styles);
                row.createCell(9).setCellValue(vente.statut() == StatutVente.ANNULEE ? "Annulée" : "Validée");
            }
            largeurs(feuille, entetes.length);

            Sheet articles = classeur.createSheet("Articles vendus");
            int rang = entetes(articles, 0, styles.entete, "Date", "N° de vente", "Statut", "Référence", "Désignation", "Quantité",
                "Prix catalogue", "Prix appliqué", "Promotion par unité", "Montant de la ligne");
            for (Vente vente : ventes) {
                for (LigneVente ligneVente : vente.getLignesVente()) {
                    Row row = articles.createRow(rang++);
                    cellule(row, 0, vente.getDateVente(), styles.jourHeure);
                    row.createCell(1).setCellValue(vente.getCode());
                    row.createCell(2).setCellValue(vente.getStatut() == StatutVente.ANNULEE ? "Annulée" : "Validée");
                    row.createCell(3).setCellValue(ligneVente.getArticle().getReference());
                    row.createCell(4).setCellValue(ligneVente.getArticle().getDesignation());
                    row.createCell(5).setCellValue(ligneVente.getQuantite());
                    BigDecimal catalogue = ligneVente.getPrixCatalogue() == null ? ligneVente.getPrixUnitaire() : ligneVente.getPrixCatalogue();
                    nombre(row, 6, catalogue, styles);
                    nombre(row, 7, ligneVente.getPrixUnitaire(), styles);
                    BigDecimal promotion = ligneVente.getRemiseUnitaire() == null ? BigDecimal.ZERO : ligneVente.getRemiseUnitaire();
                    nombre(row, 8, promotion, styles);
                    nombre(row, 9, ligneVente.getPrixUnitaire().subtract(promotion).multiply(BigDecimal.valueOf(ligneVente.getQuantite())), styles);
                }
            }
            largeurs(articles, 10);

            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }

    public byte[] exporterPdf(LocalDate debut, LocalDate fin) throws IOException {
        return exporterPdf(debut, fin, null);
    }

    /** @param vendeur le compte dont on imprime les ventes, ou {@code null} pour tous les vendeurs */
    public byte[] exporterPdf(LocalDate debut, LocalDate fin, String vendeur) throws IOException {
        List<Vente> ventes = chronologique(historiqueService.charger(debut, fin, vendeur, HistoriqueVentesService.VENTES_DOCUMENT_MAX, "l'imprimer"));
        HistoriqueVentesResponse historique = historiqueService.construire(debut, fin, ventes);
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            EntrepriseResponse entreprise = entrepriseService.obtenir();
            Document document = ExportSupport.documentPaysage(sortie, entreprise.nom() + " · Historique des ventes · " + ExportSupport.periode(debut, fin));
            try {
                EntetePdf.ajouter(document, entreprise, entrepriseService.logo(TailleLogo.ENTETE).map(EntrepriseService.LogoServi::contenu).orElse(null));
                document.add(new Paragraph("Historique des ventes", ExportSupport.gras(16)));
                document.add(new Paragraph(sousTitre(debut, fin, vendeur) + " · édité le " + ExportSupport.jourHeure(LocalDateTime.now()), ExportSupport.normal(9)));
                document.add(new Paragraph(" ", ExportSupport.normal(6)));

                ResumeHistoriqueVentes resume = historique.resume();
                document.add(TableauPdf.creer(new float[] {1f, 1.6f, 1.4f, 1f, 1.4f}, new boolean[] {true, true, true, true, true},
                        "Ventes validées", "Chiffre d'affaires TTC", "Remises accordées", "Ventes annulées", "Montant annulé (non compté)")
                    .ligne(Long.toString(resume.nombreValidees()), ExportSupport.montant(resume.chiffreAffaires()), ExportSupport.montant(resume.remises()),
                        Long.toString(resume.nombreAnnulees()), ExportSupport.montant(resume.montantAnnule()))
                    .table());

                boolean plusieursJours = !debut.equals(fin);
                TableauPdf tableau = TableauPdf.creer(new float[] {plusieursJours ? 1.25f : 0.7f, 1.7f, 2.1f, 1.2f, 1.2f, 0.6f, 1.1f, 1.3f, 1f},
                    new boolean[] {false, false, false, false, false, true, true, true, false},
                    plusieursJours ? "Date et heure" : "Heure", "N° de vente", "Client", "Caissier", "Paiement", "Art.", "Remises", "Total TTC", "Statut");
                LocalDate jourCourant = null;
                long nombreDuJour = 0;
                BigDecimal totalDuJour = BigDecimal.ZERO;
                for (LigneHistoriqueVente vente : historique.ventes()) {
                    LocalDate jour = vente.dateVente().toLocalDate();
                    if (plusieursJours && !jour.equals(jourCourant)) {
                        if (jourCourant != null) sousTotal(tableau, jourCourant, nombreDuJour, totalDuJour);
                        jourCourant = jour;
                        nombreDuJour = 0;
                        totalDuJour = BigDecimal.ZERO;
                    }
                    if (vente.statut() == StatutVente.VALIDEE) {
                        nombreDuJour++;
                        totalDuJour = totalDuJour.add(vente.montantTotal());
                    }
                    tableau.ligne(
                        (plusieursJours ? ExportSupport.jour(jour) + " " : "") + ExportSupport.heure(vente.dateVente()),
                        vente.code(),
                        vente.client() == null ? "Client de passage" : vente.client(),
                        vente.vendeur() == null ? "" : vente.vendeur(),
                        vente.aCredit() ? "À crédit" : ExportSupport.libelleMode(vente.modeReglement()),
                        Integer.toString(vente.nombreArticles()),
                        vente.remises().signum() == 0 ? "—" : ExportSupport.montant(vente.remises()),
                        ExportSupport.montant(vente.montantTotal()),
                        vente.statut() == StatutVente.ANNULEE ? "Annulée" : "Validée");
                }
                if (plusieursJours && jourCourant != null) sousTotal(tableau, jourCourant, nombreDuJour, totalDuJour);
                if (historique.ventes().isEmpty()) {
                    tableau.ligne("", "Aucune vente sur cette période.", "", "", "", "", "", "", "");
                }
                document.add(tableau.table());
                document.add(new Paragraph("Les ventes annulées sont listées pour mémoire : elles ne comptent ni dans les sous-totaux ni dans le chiffre d'affaires. "
                    + "Le chiffre d'affaires est TTC.", ExportSupport.normal(8)));
                document.close();
            } catch (DocumentException exception) {
                throw new IOException("La génération du PDF de l'historique des ventes a échoué", exception);
            }
            return sortie.toByteArray();
        }
    }

    private static void sousTotal(TableauPdf tableau, LocalDate jour, long nombre, BigDecimal total) {
        tableau.sousTotal("", "Total du " + ExportSupport.jour(jour), nombre + (nombre > 1 ? " ventes validées" : " vente validée"),
            "", "", "", "", ExportSupport.montant(total), "");
    }

    // ------------------------------------------------------------------ ventes soldées

    public byte[] exporterSoldeesExcel(LocalDate debut, LocalDate fin, String vendeur) throws IOException {
        VentesSoldeesResponse soldees = soldees(debut, fin, vendeur);
        try (Workbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Styles styles = new Styles(classeur);
            byte[] logo = entrepriseService.logo(TailleLogo.MINIATURE).map(EntrepriseService.LogoServi::contenu).orElse(null);
            Sheet feuille = classeur.createSheet("Ventes soldées");
            int ligne = EnteteExcel.ecrire(classeur, feuille, entrepriseService.obtenir(), logo);
            ligne = titre(feuille, ligne, "Ventes soldées", sousTitre(debut, fin, vendeur), styles);
            ResumeVentesSoldees resume = soldees.resume();
            ligne = valeur(feuille, ligne, "Ventes soldées", resume.nombre(), styles);
            ligne = montant(feuille, ligne, "Montant TTC", resume.montant(), styles);
            ligne = montant(feuille, ligne, "Dont au comptant (" + resume.nombreComptant() + ")", resume.montantComptant(), styles);
            ligne = montant(feuille, ligne, "Dont crédits soldés (" + resume.nombreCreditsSoldes() + ")", resume.montantCreditsSoldes(), styles);
            ligne++;
            String[] entetes = {"Date", "Heure", "N° de vente", "Client", "Caissier", "Paiement", "Soldée le", "Total TTC"};
            ligne = entetes(feuille, ligne, styles.entete, entetes);
            for (LigneVenteSoldee vente : soldees.ventes()) {
                Row row = feuille.createRow(ligne++);
                cellule(row, 0, vente.dateVente().toLocalDate(), styles.jour);
                cellule(row, 1, vente.dateVente(), styles.heure);
                row.createCell(2).setCellValue(vente.code());
                row.createCell(3).setCellValue(vente.client() == null ? "Client de passage" : vente.client());
                row.createCell(4).setCellValue(vente.vendeur() == null ? "" : vente.vendeur());
                row.createCell(5).setCellValue(vente.aCredit() ? "Crédit soldé" : ExportSupport.libelleMode(vente.modeReglement()));
                if (vente.soldeeLe() != null) cellule(row, 6, vente.soldeeLe(), styles.jourHeure);
                nombre(row, 7, vente.montantTotal(), styles);
            }
            largeurs(feuille, entetes.length);
            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }

    public byte[] exporterSoldeesPdf(LocalDate debut, LocalDate fin, String vendeur) throws IOException {
        VentesSoldeesResponse soldees = soldees(debut, fin, vendeur);
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            EntrepriseResponse entreprise = entrepriseService.obtenir();
            Document document = ExportSupport.documentPaysage(sortie, entreprise.nom() + " · Ventes soldées · " + ExportSupport.periode(debut, fin));
            try {
                EntetePdf.ajouter(document, entreprise, entrepriseService.logo(TailleLogo.ENTETE).map(EntrepriseService.LogoServi::contenu).orElse(null));
                document.add(new Paragraph("Ventes soldées", ExportSupport.gras(16)));
                document.add(new Paragraph(sousTitre(debut, fin, vendeur) + " · édité le " + ExportSupport.jourHeure(LocalDateTime.now()), ExportSupport.normal(9)));
                document.add(new Paragraph(" ", ExportSupport.normal(6)));

                ResumeVentesSoldees resume = soldees.resume();
                document.add(TableauPdf.creer(new float[] {1f, 1.5f, 1.8f, 1.8f}, new boolean[] {true, true, true, true},
                        "Ventes soldées", "Montant TTC", "Dont au comptant", "Dont crédits soldés")
                    .ligne(Long.toString(resume.nombre()), ExportSupport.montant(resume.montant()),
                        ventes(resume.nombreComptant()) + " · " + ExportSupport.montant(resume.montantComptant()),
                        ventes(resume.nombreCreditsSoldes()) + " · " + ExportSupport.montant(resume.montantCreditsSoldes()))
                    .table());

                boolean plusieursJours = !debut.equals(fin);
                TableauPdf tableau = TableauPdf.creer(new float[] {plusieursJours ? 1.25f : 0.7f, 1.7f, 2.1f, 1.2f, 2f, 1.3f},
                    new boolean[] {false, false, false, false, false, true},
                    plusieursJours ? "Date et heure" : "Heure", "N° de vente", "Client", "Caissier", "Paiement", "Total TTC");
                LocalDate jourCourant = null;
                long nombreDuJour = 0;
                BigDecimal totalDuJour = BigDecimal.ZERO;
                for (LigneVenteSoldee vente : soldees.ventes()) {
                    LocalDate jour = vente.dateVente().toLocalDate();
                    if (plusieursJours && !jour.equals(jourCourant)) {
                        if (jourCourant != null) sousTotalSoldees(tableau, jourCourant, nombreDuJour, totalDuJour);
                        jourCourant = jour;
                        nombreDuJour = 0;
                        totalDuJour = BigDecimal.ZERO;
                    }
                    nombreDuJour++;
                    totalDuJour = totalDuJour.add(vente.montantTotal());
                    tableau.ligne(
                        (plusieursJours ? ExportSupport.jour(jour) + " " : "") + ExportSupport.heure(vente.dateVente()),
                        vente.code(),
                        vente.client() == null ? "Client de passage" : vente.client(),
                        vente.vendeur() == null ? "" : vente.vendeur(),
                        vente.aCredit()
                            ? "Crédit soldé" + (vente.soldeeLe() == null ? "" : " le " + ExportSupport.jour(vente.soldeeLe().toLocalDate()))
                            : ExportSupport.libelleMode(vente.modeReglement()),
                        ExportSupport.montant(vente.montantTotal()));
                }
                if (plusieursJours && jourCourant != null) sousTotalSoldees(tableau, jourCourant, nombreDuJour, totalDuJour);
                if (soldees.ventes().isEmpty()) {
                    tableau.ligne("", "Aucune vente soldée sur cette période.", "", "", "", "");
                }
                document.add(tableau.table());
                document.add(new Paragraph("Une vente à crédit figure ici à la date où elle a été faite, dès que sa dette est entièrement réglée "
                    + "(« Crédit soldé le … » : date du dernier règlement). Les crédits encore dus et les ventes annulées n'y figurent pas.",
                    ExportSupport.normal(8)));
                document.close();
            } catch (DocumentException exception) {
                throw new IOException("La génération du PDF des ventes soldées a échoué", exception);
            }
            return sortie.toByteArray();
        }
    }

    private VentesSoldeesResponse soldees(LocalDate debut, LocalDate fin, String vendeur) {
        List<Vente> ventes = chronologique(historiqueService.chargerSoldees(debut, fin, vendeur, HistoriqueVentesService.VENTES_DOCUMENT_MAX, "l'imprimer"));
        return historiqueService.construireSoldees(debut, fin, ventes);
    }

    private static String ventes(long nombre) {
        return nombre + (nombre > 1 ? " ventes" : " vente");
    }

    private static void sousTotalSoldees(TableauPdf tableau, LocalDate jour, long nombre, BigDecimal total) {
        tableau.sousTotal("", "Total du " + ExportSupport.jour(jour), nombre + (nombre > 1 ? " ventes soldées" : " vente soldée"),
            "", "", ExportSupport.montant(total));
    }

    // ------------------------------------------------------------------ règlements de dettes

    public byte[] exporterReglementsExcel(LocalDate debut, LocalDate fin, String vendeur) throws IOException {
        ReglementsDettesResponse reglements = reglements(debut, fin, vendeur);
        try (Workbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Styles styles = new Styles(classeur);
            byte[] logo = entrepriseService.logo(TailleLogo.MINIATURE).map(EntrepriseService.LogoServi::contenu).orElse(null);
            Sheet feuille = classeur.createSheet("Règlements de dettes");
            int ligne = EnteteExcel.ecrire(classeur, feuille, entrepriseService.obtenir(), logo);
            ligne = titre(feuille, ligne, "Règlements de dettes", sousTitre(debut, fin, vendeur), styles);
            ResumeReglementsDettes resume = reglements.resume();
            ligne = valeur(feuille, ligne, "Règlements reçus", resume.nombre(), styles);
            ligne = montant(feuille, ligne, "Total encaissé", resume.total(), styles);
            for (TotalParMode mode : resume.parMode()) {
                ligne = montant(feuille, ligne, "Dont " + ExportSupport.libelleMode(mode.mode()) + " (" + mode.nombre() + ")", mode.montant(), styles);
            }
            ligne = valeur(feuille, ligne, "Dettes soldées par ces règlements", resume.dettesSoldees(), styles);
            ligne++;
            String[] entetes = {"Date", "Heure", "Client", "Vente", "Paiement", "Référence", "Encaissé par", "Montant", "Reste dû après"};
            ligne = entetes(feuille, ligne, styles.entete, entetes);
            for (LigneReglementDette reglement : reglements.reglements()) {
                Row row = feuille.createRow(ligne++);
                cellule(row, 0, reglement.dateReglement().toLocalDate(), styles.jour);
                cellule(row, 1, reglement.dateReglement(), styles.heure);
                row.createCell(2).setCellValue(reglement.client());
                row.createCell(3).setCellValue(reglement.vente() == null ? "" : reglement.vente());
                row.createCell(4).setCellValue(ExportSupport.libelleMode(reglement.modeReglement()));
                row.createCell(5).setCellValue(reglement.reference() == null ? "" : reglement.reference());
                row.createCell(6).setCellValue(reglement.recuPar() == null ? "" : reglement.recuPar());
                nombre(row, 7, reglement.montant(), styles);
                if (reglement.detteAnnulee()) row.createCell(8).setCellValue("Dette annulée");
                else nombre(row, 8, reglement.resteApres(), styles);
            }
            largeurs(feuille, entetes.length);
            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }

    public byte[] exporterReglementsPdf(LocalDate debut, LocalDate fin, String vendeur) throws IOException {
        ReglementsDettesResponse reglements = reglements(debut, fin, vendeur);
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            EntrepriseResponse entreprise = entrepriseService.obtenir();
            Document document = ExportSupport.documentPaysage(sortie, entreprise.nom() + " · Règlements de dettes · " + ExportSupport.periode(debut, fin));
            try {
                EntetePdf.ajouter(document, entreprise, entrepriseService.logo(TailleLogo.ENTETE).map(EntrepriseService.LogoServi::contenu).orElse(null));
                document.add(new Paragraph("Règlements de dettes", ExportSupport.gras(16)));
                document.add(new Paragraph(sousTitre(debut, fin, vendeur) + " · édité le " + ExportSupport.jourHeure(LocalDateTime.now()), ExportSupport.normal(9)));
                document.add(new Paragraph(" ", ExportSupport.normal(6)));

                ResumeReglementsDettes resume = reglements.resume();
                StringBuilder parMode = new StringBuilder();
                for (TotalParMode mode : resume.parMode()) {
                    if (!parMode.isEmpty()) parMode.append(" · ");
                    parMode.append(ExportSupport.libelleMode(mode.mode())).append(" ").append(ExportSupport.montant(mode.montant()));
                }
                document.add(TableauPdf.creer(new float[] {1f, 1.4f, 1.1f, 3.5f}, new boolean[] {true, true, true, false},
                        "Règlements", "Total encaissé", "Dettes soldées", "Par moyen de paiement")
                    .ligne(Long.toString(resume.nombre()), ExportSupport.montant(resume.total()), Long.toString(resume.dettesSoldees()),
                        parMode.isEmpty() ? "—" : parMode.toString())
                    .table());

                boolean plusieursJours = !debut.equals(fin);
                TableauPdf tableau = TableauPdf.creer(new float[] {plusieursJours ? 1.25f : 0.7f, 2f, 1.6f, 1.1f, 1.1f, 1.1f, 1.2f, 1.2f},
                    new boolean[] {false, false, false, false, false, false, true, true},
                    plusieursJours ? "Date et heure" : "Heure", "Client", "Vente", "Paiement", "Référence", "Encaissé par", "Montant", "Reste dû après");
                LocalDate jourCourant = null;
                long nombreDuJour = 0;
                BigDecimal totalDuJour = BigDecimal.ZERO;
                for (LigneReglementDette reglement : reglements.reglements()) {
                    LocalDate jour = reglement.dateReglement().toLocalDate();
                    if (plusieursJours && !jour.equals(jourCourant)) {
                        if (jourCourant != null) sousTotalReglements(tableau, jourCourant, nombreDuJour, totalDuJour);
                        jourCourant = jour;
                        nombreDuJour = 0;
                        totalDuJour = BigDecimal.ZERO;
                    }
                    nombreDuJour++;
                    totalDuJour = totalDuJour.add(reglement.montant());
                    tableau.ligne(
                        (plusieursJours ? ExportSupport.jour(jour) + " " : "") + ExportSupport.heure(reglement.dateReglement()),
                        reglement.client(),
                        reglement.vente() == null ? "" : reglement.vente(),
                        ExportSupport.libelleMode(reglement.modeReglement()),
                        reglement.reference() == null ? "" : reglement.reference(),
                        reglement.recuPar() == null ? "" : reglement.recuPar(),
                        ExportSupport.montant(reglement.montant()),
                        reglement.detteAnnulee() ? "Dette annulée"
                            : reglement.resteApres().signum() == 0 ? "Soldée" : ExportSupport.montant(reglement.resteApres()));
                }
                if (plusieursJours && jourCourant != null) sousTotalReglements(tableau, jourCourant, nombreDuJour, totalDuJour);
                if (reglements.reglements().isEmpty()) {
                    tableau.ligne("", "Aucun règlement de dette sur cette période.", "", "", "", "", "", "");
                }
                document.add(tableau.table());
                document.add(new Paragraph("Reste dû après : ce qu'il restait à payer sur la dette juste après ce règlement ; « Soldée » : ce règlement "
                    + "a payé la dette jusqu'au dernier franc. « Encaissé par » : le compte qui a reçu l'argent.", ExportSupport.normal(8)));
                document.close();
            } catch (DocumentException exception) {
                throw new IOException("La génération du PDF des règlements de dettes a échoué", exception);
            }
            return sortie.toByteArray();
        }
    }

    private ReglementsDettesResponse reglements(LocalDate debut, LocalDate fin, String vendeur) {
        List<ReglementClient> liste = new ArrayList<>(reglementsService.charger(debut, fin, vendeur, HistoriqueReglementsService.REGLEMENTS_DOCUMENT_MAX, "l'imprimer"));
        liste.sort(Comparator.comparing(ReglementClient::getDateReglement).thenComparing(ReglementClient::getId));
        return reglementsService.construire(debut, fin, liste);
    }

    private static void sousTotalReglements(TableauPdf tableau, LocalDate jour, long nombre, BigDecimal total) {
        tableau.sousTotal("", "Total du " + ExportSupport.jour(jour), nombre + (nombre > 1 ? " règlements" : " règlement"),
            "", "", "", ExportSupport.montant(total), "");
    }

    /** « 1er au 31 octobre 2026 · vendeur : marie » ou « … · tous les vendeurs » : on sait toujours de qui parle le document. */
    private static String sousTitre(LocalDate debut, LocalDate fin, String vendeur) {
        return ExportSupport.periode(debut, fin) + " · " + (vendeur == null ? "tous les vendeurs" : "vendeur : " + vendeur);
    }

    private static List<Vente> chronologique(List<Vente> ventes) {
        List<Vente> copie = new ArrayList<>(ventes);
        copie.sort(Comparator.comparing(Vente::getDateVente).thenComparing(Vente::getId));
        return copie;
    }

    // ------------------------------------------------------------------ outils Excel

    private static final class Styles {
        final CellStyle entete;
        final CellStyle titre;
        final CellStyle gras;
        final CellStyle jour;
        final CellStyle heure;
        final CellStyle jourHeure;
        final CellStyle entier;
        final CellStyle decimal;

        Styles(Workbook classeur) {
            Font fort = classeur.createFont();
            fort.setBold(true);
            entete = classeur.createCellStyle();
            entete.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            entete.setFont(fort);
            Font grand = classeur.createFont();
            grand.setBold(true);
            grand.setFontHeightInPoints((short) 13);
            titre = classeur.createCellStyle();
            titre.setFont(grand);
            gras = classeur.createCellStyle();
            gras.setFont(fort);
            var format = classeur.createDataFormat();
            jour = classeur.createCellStyle();
            jour.setDataFormat(format.getFormat("dd/mm/yyyy"));
            heure = classeur.createCellStyle();
            heure.setDataFormat(format.getFormat("hh:mm"));
            jourHeure = classeur.createCellStyle();
            jourHeure.setDataFormat(format.getFormat("dd/mm/yyyy hh:mm"));
            entier = classeur.createCellStyle();
            entier.setDataFormat(format.getFormat("#,##0"));
            decimal = classeur.createCellStyle();
            decimal.setDataFormat(format.getFormat("#,##0.00"));
        }
    }

    private static int titre(Sheet feuille, int ligne, String titre, String sousTitre, Styles styles) {
        fusionner(feuille, ligne, 0, 5);
        Row row = feuille.createRow(ligne++);
        row.createCell(0).setCellValue(titre);
        row.getCell(0).setCellStyle(styles.titre);
        fusionner(feuille, ligne, 0, 5);
        feuille.createRow(ligne++).createCell(0).setCellValue(sousTitre);
        return ligne + 1;
    }

    private static void fusionner(Sheet feuille, int ligne, int de, int a) {
        feuille.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(ligne, ligne, de, a));
    }

    private static int valeur(Sheet feuille, int ligne, String libelle, long valeur, Styles styles) {
        fusionner(feuille, ligne, 0, 2);
        Row row = feuille.createRow(ligne);
        row.createCell(0).setCellValue(libelle);
        row.getCell(0).setCellStyle(styles.gras);
        row.createCell(3).setCellValue(valeur);
        return ligne + 1;
    }

    private static int montant(Sheet feuille, int ligne, String libelle, BigDecimal valeur, Styles styles) {
        fusionner(feuille, ligne, 0, 2);
        Row row = feuille.createRow(ligne);
        row.createCell(0).setCellValue(libelle);
        row.getCell(0).setCellStyle(styles.gras);
        nombre(row, 3, valeur, styles);
        return ligne + 1;
    }

    private static int entetes(Sheet feuille, int ligne, CellStyle style, String... libelles) {
        Row row = feuille.createRow(ligne);
        for (int i = 0; i < libelles.length; i++) {
            row.createCell(i).setCellValue(libelles[i]);
            row.getCell(i).setCellStyle(style);
        }
        return ligne + 1;
    }

    private static void cellule(Row row, int colonne, LocalDate date, CellStyle style) {
        var cellule = row.createCell(colonne);
        cellule.setCellValue(date);
        cellule.setCellStyle(style);
    }

    private static void cellule(Row row, int colonne, LocalDateTime date, CellStyle style) {
        var cellule = row.createCell(colonne);
        cellule.setCellValue(date);
        cellule.setCellStyle(style);
    }

    /** Un montant est un vrai nombre (on peut le totaliser dans Excel) ; les centimes ne s'affichent que s'il y en a. */
    private static void nombre(Row row, int colonne, BigDecimal valeur, Styles styles) {
        var cellule = row.createCell(colonne);
        cellule.setCellValue(valeur.doubleValue());
        cellule.setCellStyle(valeur.stripTrailingZeros().scale() <= 0 ? styles.entier : styles.decimal);
    }

    /** Les libellés du bloc de synthèse sont fusionnés (colonnes A à C) : seul le contenu du tableau règle la largeur des colonnes. */
    private static void largeurs(Sheet feuille, int colonnes) {
        for (int colonne = 0; colonne < colonnes; colonne++) feuille.autoSizeColumn(colonne);
    }
}
