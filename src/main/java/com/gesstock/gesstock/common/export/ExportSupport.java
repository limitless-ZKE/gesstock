package com.gesstock.gesstock.common.export;

import com.gesstock.gesstock.entities.ModeReglement;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Briques communes des documents imprimables produits par le serveur (historique des ventes, journal de caisse) : formats
 * français des montants et des dates, document PDF en paysage avec numéro de page. Les montants s'écrivent « 1 173 900 »
 * (espace insécable, jamais de décimales inutiles) comme à l'écran.
 */
public final class ExportSupport {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter JOUR_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ExportSupport() {}

    public static String montant(BigDecimal valeur) {
        if (valeur == null) return "";
        boolean entier = valeur.stripTrailingZeros().scale() <= 0;
        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRANCE);
        symboles.setGroupingSeparator(' ');
        symboles.setDecimalSeparator(',');
        return new DecimalFormat(entier ? "#,##0" : "#,##0.00", symboles).format(valeur);
    }

    public static String jour(LocalDate date) {
        return date == null ? "" : JOUR.format(date);
    }

    public static String heure(LocalDateTime date) {
        return date == null ? "" : HEURE.format(date);
    }

    public static String jourHeure(LocalDateTime date) {
        return date == null ? "" : JOUR_HEURE.format(date);
    }

    public static String periode(LocalDate debut, LocalDate fin) {
        return debut.equals(fin) ? "Journée du " + jour(debut) : "Du " + jour(debut) + " au " + jour(fin);
    }

    public static String libelleMode(ModeReglement mode) {
        if (mode == null) return "—";
        return switch (mode) {
            case ESPECES -> "Espèces";
            case CARTE -> "Carte";
            case VIREMENT -> "Virement";
            case CHEQUE -> "Chèque";
            case MOBILE_MONEY -> "Mobile Money";
            case AUTRE -> "Autre";
        };
    }

    /** Document A4 en paysage (les tableaux d'écritures sont larges), marges de 28 pt, titre et numéro de page en pied. */
    public static Document documentPaysage(OutputStream sortie, String titreDuPied) throws DocumentException {
        Document document = new Document(PageSize.A4.rotate(), 28, 28, 28, 36);
        PdfWriter ecrivain = PdfWriter.getInstance(document, sortie);
        ecrivain.setPageEvent(new PiedDePage(titreDuPied));
        document.open();
        return document;
    }

    /** Un paragraphe suivi d'un espace : sans lui, un titre colle (et est rogné par) le tableau qui le suit. */
    public static Paragraph paragraphe(String texte, Font police, float espaceApres) {
        Paragraph paragraphe = new Paragraph(texte, police);
        paragraphe.setSpacingAfter(espaceApres);
        return paragraphe;
    }

    public static Font gras(float taille) {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, taille);
    }

    public static Font normal(float taille) {
        return FontFactory.getFont(FontFactory.HELVETICA, taille);
    }

    /** Pied de chaque page : titre du document à gauche, « Page n » à droite. */
    private static final class PiedDePage extends PdfPageEventHelper {
        private final String titre;

        PiedDePage(String titre) {
            this.titre = titre;
        }

        @Override
        public void onEndPage(PdfWriter ecrivain, Document document) {
            var contenu = ecrivain.getDirectContent();
            float y = document.bottom() - 18;
            ColumnText.showTextAligned(contenu, Element.ALIGN_LEFT, new Phrase(titre, normal(7.5f)), document.left(), y, 0);
            ColumnText.showTextAligned(contenu, Element.ALIGN_RIGHT,
                new Phrase("Page " + ecrivain.getPageNumber(), normal(7.5f)), document.right(), y, 0);
        }
    }
}
