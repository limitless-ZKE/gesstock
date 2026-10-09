package com.gesstock.gesstock.common.export;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

import java.awt.Color;

/**
 * Tableau PDF aux couleurs sobres des documents GesStock : en-têtes sur fond vert pâle, répétés en haut de chaque page, simple
 * trait sous chaque ligne, colonnes de montants alignées à droite.
 */
public final class TableauPdf {

    private static final Color FOND_ENTETE = new Color(0xDD, 0xEA, 0xE3);
    private static final Color FOND_SOUS_TOTAL = new Color(0xF0, 0xF2, 0xEF);
    private static final Color TRAIT = new Color(0xB9, 0xBF, 0xB7);

    private final PdfPTable table;
    private final boolean[] aDroite;

    private TableauPdf(PdfPTable table, boolean[] aDroite) {
        this.table = table;
        this.aDroite = aDroite;
    }

    /**
     * @param largeurs proportions des colonnes
     * @param aDroite  pour chaque colonne, vrai si elle porte des montants (alignée à droite)
     * @param entetes  intitulés, un par colonne
     */
    public static TableauPdf creer(float[] largeurs, boolean[] aDroite, String... entetes) {
        PdfPTable table = new PdfPTable(largeurs);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSpacingAfter(6);
        TableauPdf tableau = new TableauPdf(table, aDroite);
        for (int i = 0; i < entetes.length; i++) {
            tableau.cellule(entetes[i], ExportSupport.gras(8), FOND_ENTETE, aDroite[i]);
        }
        return tableau;
    }

    public TableauPdf ligne(String... valeurs) {
        for (int i = 0; i < valeurs.length; i++) cellule(valeurs[i], ExportSupport.normal(8), null, aDroite[i]);
        return this;
    }

    public TableauPdf sousTotal(String... valeurs) {
        for (int i = 0; i < valeurs.length; i++) cellule(valeurs[i], ExportSupport.gras(8), FOND_SOUS_TOTAL, aDroite[i]);
        return this;
    }

    public PdfPTable table() {
        return table;
    }

    private void cellule(String texte, Font police, Color fond, boolean droite) {
        PdfPCell cellule = new PdfPCell(new Phrase(texte == null ? "" : texte, police));
        cellule.setPadding(3.5f);
        cellule.setBorder(Rectangle.BOTTOM);
        cellule.setBorderColor(TRAIT);
        cellule.setBorderWidth(0.5f);
        if (fond != null) cellule.setBackgroundColor(fond);
        cellule.setHorizontalAlignment(droite ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        table.addCell(cellule);
    }
}
