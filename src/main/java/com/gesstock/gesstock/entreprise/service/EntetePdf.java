package com.gesstock.gesstock.entreprise.service;

import com.gesstock.gesstock.entreprise.dto.EntrepriseResponse;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

import java.io.IOException;

/** En-tête d'un document PDF : logo à gauche (60 mm × 25 mm au plus), nom et coordonnées de l'entreprise à côté. */
public final class EntetePdf {

    /** 60 mm et 25 mm en points PDF (1 mm = 2,835 pt). */
    private static final float LOGO_LARGEUR_MAX = 170f;
    private static final float LOGO_HAUTEUR_MAX = 71f;

    private EntetePdf() {}

    public static void ajouter(Document document, EntrepriseResponse entreprise, byte[] logoPng) throws DocumentException, IOException {
        boolean avecLogo = logoPng != null;
        PdfPTable table = new PdfPTable(avecLogo ? new float[] {1f, 2.4f} : new float[] {1f});
        table.setWidthPercentage(100);

        if (avecLogo) {
            Image logo = Image.getInstance(logoPng);
            logo.scaleToFit(LOGO_LARGEUR_MAX, LOGO_HAUTEUR_MAX);
            PdfPCell celluleLogo = new PdfPCell(logo, false);
            celluleLogo.setBorder(Rectangle.NO_BORDER);
            celluleLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(celluleLogo);
        }

        PdfPCell texte = new PdfPCell();
        texte.setBorder(Rectangle.NO_BORDER);
        texte.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Font gras = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
        texte.addElement(new Paragraph(entreprise.nom(), gras));
        for (String ligne : EnteteExcel.coordonnees(entreprise)) {
            texte.addElement(new Paragraph(ligne, normal));
        }
        table.addCell(texte);

        document.add(table);
        document.add(new Paragraph(" "));
    }
}
