package com.gesstock.gesstock.approvisionnements.service;

import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.common.export.ExportSupport;
import com.gesstock.gesstock.common.export.TableauPdf;
import com.gesstock.gesstock.entities.Approvisionnement;
import com.gesstock.gesstock.entities.Fournisseur;
import com.gesstock.gesstock.entities.LigneApprovisionnement;
import com.gesstock.gesstock.entreprise.dto.EntrepriseResponse;
import com.gesstock.gesstock.entreprise.service.EntetePdf;
import com.gesstock.gesstock.entreprise.service.EntrepriseService;
import com.gesstock.gesstock.entreprise.service.TailleLogo;
import com.gesstock.gesstock.repositories.ApprovisionnementRepository;
import com.gesstock.gesstock.stock.Quantites;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Le bon d'approvisionnement à imprimer : fournisseur, articles, quantités, prix d'achat, total, et trois cases de signature
 * (celui qui l'établit, celui qui réceptionne, le fournisseur ou son livreur). Il ne montre jamais le prix de revente, que le
 * fournisseur n'a pas à connaître.
 */
@Service
@Transactional(readOnly = true)
public class ApprovisionnementExportService {

    private final ApprovisionnementRepository approvisionnementRepository;
    private final EntrepriseService entrepriseService;

    public ApprovisionnementExportService(ApprovisionnementRepository approvisionnementRepository, EntrepriseService entrepriseService) {
        this.approvisionnementRepository = approvisionnementRepository;
        this.entrepriseService = entrepriseService;
    }

    /** Le numéro du bon, utilisable dans un nom de fichier. */
    public String nomDeFichier(Long id) {
        String numero = trouver(id).getNumero();
        return "gesstock-bon-" + numero.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    public byte[] exporterPdf(Long id) throws IOException {
        Approvisionnement bon = trouver(id);
        List<LigneApprovisionnement> lignes = bon.getLignes() == null ? List.of() : bon.getLignes();
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            EntrepriseResponse entreprise = entrepriseService.obtenir();
            Document document = ExportSupport.documentPortrait(sortie, entreprise.nom() + " · Bon d'approvisionnement " + bon.getNumero());
            try {
                EntetePdf.ajouter(document, entreprise, entrepriseService.logo(TailleLogo.ENTETE).map(EntrepriseService.LogoServi::contenu).orElse(null));
                document.add(new Paragraph("Bon d'approvisionnement " + bon.getNumero(), ExportSupport.gras(16)));
                document.add(ExportSupport.paragraphe(etat(bon), ExportSupport.normal(9), 8));
                document.add(coordonnees(bon));
                document.add(new Paragraph(" ", ExportSupport.normal(6)));

                TableauPdf tableau = TableauPdf.creer(new float[] {1.3f, 3.6f, 1f, 1.1f, 1.4f, 1.6f},
                    new boolean[] {false, false, true, false, true, true},
                    "Référence", "Désignation", "Quantité", "Unité", "Prix d'achat", "Montant");
                BigDecimal total = BigDecimal.ZERO;
                for (LigneApprovisionnement ligne : lignes) {
                    BigDecimal montant = Quantites.montant(ligne.getPrixUnitaire(), ligne.getQuantite());
                    total = total.add(montant);
                    var article = ligne.getArticle();
                    tableau.ligne(article.getReference(), article.getDesignation(), Quantites.texte(ligne.getQuantite()),
                        article.getUniteMesure() == null ? "" : article.getUniteMesure().getLibelle(),
                        ExportSupport.montant(ligne.getPrixUnitaire()), ExportSupport.montant(montant));
                }
                if (lignes.isEmpty()) {
                    document.add(new Paragraph("Ce bon ne contient aucune ligne.", ExportSupport.normal(10)));
                } else {
                    tableau.sousTotal("Total", lignes.size() + (lignes.size() > 1 ? " articles" : " article"), "", "", "", ExportSupport.montant(total));
                    document.add(tableau.table());
                }
                document.add(new Paragraph(" ", ExportSupport.normal(14)));
                document.add(signatures(bon));
                document.add(ExportSupport.paragraphe("Édité le " + ExportSupport.jourHeure(LocalDateTime.now()), ExportSupport.normal(8), 0));
                document.close();
            } catch (DocumentException exception) {
                throw new IOException("La génération du PDF du bon d'approvisionnement a échoué", exception);
            }
            return sortie.toByteArray();
        }
    }

    private static String etat(Approvisionnement bon) {
        String date = "Bon du " + ExportSupport.jour(bon.getDateApprovisionnement());
        return switch (bon.getStatut()) {
            case BROUILLON -> date + " · brouillon, pas encore reçu en stock";
            case RECU -> date + " · reçu en stock" + (bon.getRecuLe() == null ? "" : " le " + ExportSupport.jourHeure(bon.getRecuLe()));
            case ANNULE -> date + " · annulé";
        };
    }

    private static PdfPTable coordonnees(Approvisionnement bon) throws DocumentException {
        Fournisseur fournisseur = bon.getFournisseur();
        PdfPTable table = new PdfPTable(new float[] {1.2f, 4f});
        table.setWidthPercentage(100);
        ligneDInformation(table, "Fournisseur", fournisseur == null ? "—" : fournisseur.getNom());
        if (fournisseur != null && fournisseur.getTelephone() != null && !fournisseur.getTelephone().isBlank()) {
            ligneDInformation(table, "Téléphone", fournisseur.getTelephone());
        }
        if (fournisseur != null && fournisseur.getAdresse() != null && !fournisseur.getAdresse().isBlank()) {
            ligneDInformation(table, "Adresse", fournisseur.getAdresse());
        }
        if (bon.getCreePar() != null) {
            ligneDInformation(table, "Établi par", bon.getCreePar().getUsername());
        }
        return table;
    }

    private static void ligneDInformation(PdfPTable table, String intitule, String valeur) {
        PdfPCell gauche = new PdfPCell(new Phrase(intitule, ExportSupport.gras(9)));
        PdfPCell droite = new PdfPCell(new Phrase(valeur == null ? "—" : valeur, ExportSupport.normal(9)));
        for (PdfPCell cellule : new PdfPCell[] {gauche, droite}) {
            cellule.setBorder(Rectangle.NO_BORDER);
            cellule.setPaddingBottom(2);
            table.addCell(cellule);
        }
    }

    /** Trois cases à signer à la main : on garde le bon signé avec la facture ou le bon de livraison du fournisseur. */
    private static PdfPTable signatures(Approvisionnement bon) {
        PdfPTable table = new PdfPTable(new float[] {1f, 0.12f, 1f, 0.12f, 1f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        String etabliPar = bon.getCreePar() == null ? "Établi par" : "Établi par " + bon.getCreePar().getUsername();
        String[] intitules = {etabliPar, "Réceptionné par", "Le fournisseur ou son livreur"};
        for (int i = 0; i < intitules.length; i++) {
            PdfPCell cellule = new PdfPCell(new Phrase(intitules[i], ExportSupport.normal(9)));
            cellule.setHorizontalAlignment(Element.ALIGN_CENTER);
            cellule.setPadding(5);
            cellule.setFixedHeight(64);
            table.addCell(cellule);
            if (i < intitules.length - 1) {
                PdfPCell espace = new PdfPCell(new Phrase(""));
                espace.setBorder(Rectangle.NO_BORDER);
                table.addCell(espace);
            }
        }
        return table;
    }

    private Approvisionnement trouver(Long id) {
        return approvisionnementRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Approvisionnement introuvable: " + id));
    }
}
