package com.gesstock.gesstock.common.export;

import com.gesstock.gesstock.entreprise.dto.EntrepriseResponse;
import com.gesstock.gesstock.entreprise.service.EnteteExcel;
import com.gesstock.gesstock.entreprise.service.EntetePdf;
import com.gesstock.gesstock.entreprise.service.EntrepriseService;
import com.gesstock.gesstock.entreprise.service.TailleLogo;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Une liste à imprimer (PDF A4 portrait) ou à retravailler (Excel) : en-tête de l'entreprise, titre, sous-titre qui rappelle les
 * filtres choisis, un tableau et une ligne de total facultative. Les valeurs sont du texte, des nombres (montants : espaces
 * entre les milliers au PDF, cellule numérique à l'Excel) ou vides.
 */
public final class ListeImprimable {

    /** @param largeur proportion de la colonne dans le PDF ; @param aDroite colonne de montants ou de nombres */
    public record Colonne(String intitule, float largeur, boolean aDroite) {}

    private final String titre;
    private final String sousTitre;
    private final String messageVide;
    private final List<Colonne> colonnes;
    private final List<List<Object>> lignes = new ArrayList<>();
    private List<Object> total;

    public ListeImprimable(String titre, String sousTitre, String messageVide, List<Colonne> colonnes) {
        this.titre = titre;
        this.sousTitre = sousTitre;
        this.messageVide = messageVide;
        this.colonnes = colonnes;
    }

    public ListeImprimable ligne(Object... valeurs) {
        lignes.add(java.util.Arrays.asList(valeurs));
        return this;
    }

    /** La ligne de total, écrite sous le tableau (une valeur par colonne, vide quand il n'y a rien à totaliser). */
    public ListeImprimable total(Object... valeurs) {
        total = java.util.Arrays.asList(valeurs);
        return this;
    }

    public int nombreDeLignes() {
        return lignes.size();
    }

    // ------------------------------------------------------------------ PDF

    public byte[] pdf(EntrepriseService entrepriseService) throws IOException {
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            EntrepriseResponse entreprise = entrepriseService.obtenir();
            Document document = ExportSupport.documentPortrait(sortie, entreprise.nom() + " · " + titre);
            try {
                EntetePdf.ajouter(document, entreprise, entrepriseService.logo(TailleLogo.ENTETE).map(EntrepriseService.LogoServi::contenu).orElse(null));
                document.add(new Paragraph(titre, ExportSupport.gras(16)));
                document.add(new Paragraph(sousTitre, ExportSupport.normal(9)));
                document.add(new Paragraph(" ", ExportSupport.normal(6)));
                if (lignes.isEmpty()) {
                    document.add(new Paragraph(messageVide, ExportSupport.normal(10)));
                } else {
                    float[] largeurs = new float[colonnes.size()];
                    boolean[] aDroite = new boolean[colonnes.size()];
                    String[] intitules = new String[colonnes.size()];
                    for (int i = 0; i < colonnes.size(); i++) {
                        largeurs[i] = colonnes.get(i).largeur();
                        aDroite[i] = colonnes.get(i).aDroite();
                        intitules[i] = colonnes.get(i).intitule();
                    }
                    TableauPdf tableau = TableauPdf.creer(largeurs, aDroite, intitules);
                    for (List<Object> valeurs : lignes) tableau.ligne(textes(valeurs));
                    if (total != null) tableau.sousTotal(textes(total));
                    document.add(tableau.table());
                }
                document.close();
            } catch (DocumentException exception) {
                throw new IOException("La génération du PDF « " + titre + " » a échoué", exception);
            }
            return sortie.toByteArray();
        }
    }

    private String[] textes(List<Object> valeurs) {
        String[] textes = new String[colonnes.size()];
        for (int i = 0; i < textes.length; i++) {
            Object valeur = i < valeurs.size() ? valeurs.get(i) : null;
            textes[i] = valeur instanceof BigDecimal montant ? ExportSupport.montant(montant) : valeur == null ? "" : valeur.toString();
        }
        return textes;
    }

    // ------------------------------------------------------------------ Excel

    public byte[] excel(EntrepriseService entrepriseService) throws IOException {
        try (Workbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            CellStyle entete = classeur.createCellStyle();
            entete.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font gras = classeur.createFont();
            gras.setBold(true);
            entete.setFont(gras);
            DataFormat format = classeur.createDataFormat();
            CellStyle nombre = classeur.createCellStyle();
            nombre.setDataFormat(format.getFormat("#,##0.##"));
            CellStyle ligneTotal = classeur.createCellStyle();
            ligneTotal.setFont(gras);
            ligneTotal.setDataFormat(format.getFormat("#,##0.##"));

            Sheet feuille = classeur.createSheet(titre.length() > 31 ? titre.substring(0, 31) : titre);
            int debut = EnteteExcel.ecrire(classeur, feuille, entrepriseService.obtenir(),
                entrepriseService.logo(TailleLogo.MINIATURE).map(EntrepriseService.LogoServi::contenu).orElse(null));
            feuille.createRow(debut).createCell(0).setCellValue(titre + " · " + sousTitre);
            Row ligneEntete = feuille.createRow(debut + 2);
            for (int i = 0; i < colonnes.size(); i++) {
                var cellule = ligneEntete.createCell(i);
                cellule.setCellValue(colonnes.get(i).intitule());
                cellule.setCellStyle(entete);
            }
            int numero = debut + 3;
            for (List<Object> valeurs : lignes) ecrire(feuille.createRow(numero++), valeurs, nombre);
            if (total != null) ecrire(feuille.createRow(numero), total, ligneTotal);
            for (int i = 0; i < colonnes.size(); i++) feuille.autoSizeColumn(i);
            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }

    private void ecrire(Row ligne, List<Object> valeurs, CellStyle styleNombre) {
        for (int i = 0; i < colonnes.size() && i < valeurs.size(); i++) {
            Object valeur = valeurs.get(i);
            if (valeur == null) continue;
            var cellule = ligne.createCell(i);
            if (valeur instanceof Number nombre) {
                cellule.setCellValue(nombre.doubleValue());
                cellule.setCellStyle(styleNombre);
            } else {
                cellule.setCellValue(valeur.toString());
            }
        }
    }
}
