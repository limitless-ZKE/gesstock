package com.gesstock.gesstock.services;

import com.gesstock.gesstock.entities.LigneVente;
import com.gesstock.gesstock.entities.Vente;
import com.gesstock.gesstock.repositories.VenteRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // <-- Import important

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Service
public class PdfService {

    @Autowired
    private VenteRepository venteRepository;

    @Transactional(readOnly = true) // <-- Permet de garder la session active pour charger les lignes de vente
    public ByteArrayInputStream genererRecuPdf(Long venteId) {
        Vente vente = venteRepository.findById(venteId)
                .orElseThrow(() -> new RuntimeException("Vente introuvable avec l'ID : " + venteId));

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            Document document = new Document(PageSize.A5, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

            Paragraph titreApp = new Paragraph("GESSTOCK - REÇU DE VENTE", titleFont);
            titreApp.setAlignment(Element.ALIGN_CENTER);
            document.add(titreApp);

            Paragraph sousTitre = new Paragraph("Gestion de Stock & Ventes", normalFont);
            sousTitre.setAlignment(Element.ALIGN_CENTER);
            document.add(sousTitre);

            document.add(new Paragraph("\n"));
            document.add(new Paragraph("Code de vente : " + vente.getCode(), boldFont));
            
            String dateFormatted = vente.getDateVente() != null 
                ? vente.getDateVente().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) 
                : "N/A";
            document.add(new Paragraph("Date : " + dateFormatted, normalFont));
            
            String nomClient = (vente.getClient() != null && vente.getClient().getNom() != null) 
                ? vente.getClient().getNom() 
                : "Client Passager / Anonyme";
            document.add(new Paragraph("Client : " + nomClient, normalFont));

            document.add(new Paragraph("--------------------------------------------------------------------------------", normalFont));
            document.add(new Paragraph("\n"));

            Paragraph sectionArticles = new Paragraph("Désignation des articles :", boldFont);
            document.add(sectionArticles);
            document.add(new Paragraph("\n"));

            if (vente.getLignesVente() != null && !vente.getLignesVente().isEmpty()) {
                for (LigneVente ligne : vente.getLignesVente()) {
                    String nomArticle = (ligne.getArticle() != null) ? ligne.getArticle().getNom() : "Article inconnu";
                    document.add(new Paragraph("- " + nomArticle, boldFont));
                    document.add(new Paragraph("  Quantité : " + ligne.getQuantite() + " | Prix unitaire : " + ligne.getPrixUnitaire() + " FCFA", normalFont));
                    document.add(new Paragraph("  Sous-total : " + ligne.getPrixUnitaire().multiply(java.math.BigDecimal.valueOf(ligne.getQuantite())) + " FCFA", normalFont));
                    document.add(new Paragraph("  ------------------------------------------------------------------------------", normalFont));
                }
            } else {
                document.add(new Paragraph("Aucun article trouvé pour cette vente.", normalFont));
            }

            document.add(new Paragraph("\n"));

            Paragraph total = new Paragraph("MONTANT TOTAL : " + vente.getMontantTotal() + " FCFA", headerFont);
            total.setAlignment(Element.ALIGN_RIGHT);
            document.add(total);

            document.add(new Paragraph("\n\n"));

            Paragraph footer = new Paragraph("Merci pour votre confiance !", normalFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        return new ByteArrayInputStream(out.toByteArray());
    }
}