package com.gesstock.gesstock.entreprise.service;

import com.gesstock.gesstock.entreprise.dto.EntrepriseResponse;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.ArrayList;
import java.util.List;

/**
 * En-tête d'une feuille Excel : logo, puis nom et coordonnées de l'entreprise. Écrit tout en haut de la feuille ; renvoie
 * l'index de la première ligne libre pour le contenu du document.
 */
public final class EnteteExcel {

    /** Lignes réservées au logo : 4 × 20 points (environ 106 px) pour un logo de 80 px de haut au plus. */
    private static final int LIGNES_LOGO = 4;
    /** Largeur (en colonnes) sur laquelle les textes sont fusionnés : la fusion les exclut du calcul de largeur automatique. */
    private static final int COLONNES_TEXTE = 6;

    private EnteteExcel() {}

    public static int ecrire(Workbook classeur, Sheet feuille, EntrepriseResponse entreprise, byte[] logoPng) {
        int ligne = 0;
        if (logoPng != null) {
            int image = classeur.addPicture(logoPng, Workbook.PICTURE_TYPE_PNG);
            Drawing<?> dessin = feuille.createDrawingPatriarch();
            ClientAnchor ancre = classeur.getCreationHelper().createClientAnchor();
            ancre.setCol1(0);
            ancre.setRow1(0);
            dessin.createPicture(ancre, image).resize();   // taille d'origine : le logo fourni est déjà au format « miniature »
            for (int index = 0; index < LIGNES_LOGO; index++) {
                feuille.createRow(index).setHeightInPoints(20);
            }
            ligne = LIGNES_LOGO;
        }

        Font gras = classeur.createFont();
        gras.setBold(true);
        gras.setFontHeightInPoints((short) 14);
        CellStyle styleNom = classeur.createCellStyle();
        styleNom.setFont(gras);

        ecrireTexte(feuille, ligne++, entreprise.nom(), styleNom);
        for (String texte : coordonnees(entreprise)) {
            ecrireTexte(feuille, ligne++, texte, null);
        }
        return ligne + 1;   // une ligne vide avant le document
    }

    /** Les lignes de coordonnées renseignées, dans l'ordre d'impression. */
    public static List<String> coordonnees(EntrepriseResponse entreprise) {
        List<String> lignes = new ArrayList<>();
        ajouter(lignes, entreprise.adresse());
        List<String> contact = new ArrayList<>();
        if (entreprise.telephone() != null) {
            contact.add("Tél. " + entreprise.telephone());
        }
        if (entreprise.email() != null) {
            contact.add(entreprise.email());
        }
        if (!contact.isEmpty()) {
            lignes.add(String.join(" · ", contact));
        }
        ajouter(lignes, entreprise.identifiantLegal());
        ajouter(lignes, entreprise.siteWeb());
        return lignes;
    }

    private static void ajouter(List<String> lignes, String texte) {
        if (texte != null && !texte.isBlank()) {
            lignes.add(texte);
        }
    }

    private static void ecrireTexte(Sheet feuille, int index, String texte, CellStyle style) {
        Row ligne = feuille.getRow(index) != null ? feuille.getRow(index) : feuille.createRow(index);
        var cellule = ligne.createCell(0);
        cellule.setCellValue(texte);
        if (style != null) {
            cellule.setCellStyle(style);
        }
        feuille.addMergedRegion(new CellRangeAddress(index, index, 0, COLONNES_TEXTE - 1));
    }
}
