package com.gesstock.gesstock.articles.importation;

import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lit un fichier d'articles : Excel (.xlsx, .xls) ou texte (.csv, .txt) séparé par des points-virgules, des virgules ou des
 * tabulations. La première ligne non vide porte les titres des colonnes. Tout est lu comme du texte : les nombres sont
 * interprétés plus tard, avec un message précis par ligne en cas de problème.
 */
public final class LecteurFichierArticles {

    /** Une ligne du fichier : son numéro tel que l'utilisateur la voit (la première ligne du fichier est la ligne 1). */
    public record LigneBrute(int numero, Map<ColonneImport, String> valeurs) {
        public String texte(ColonneImport colonne) {
            String valeur = valeurs.get(colonne);
            return valeur == null ? "" : valeur.trim();
        }
    }

    /** Le contenu lu : les colonnes reconnues, les titres ignorés et les lignes de données. */
    public record TableauLu(List<ColonneImport> colonnes, List<String> colonnesIgnorees, List<LigneBrute> lignes) {}

    public static final int LIGNES_MAX = 2000;

    private LecteurFichierArticles() {}

    public static TableauLu lire(String nomDuFichier, byte[] contenu) {
        if (contenu == null || contenu.length == 0) {
            throw new RequeteInvalideException("Le fichier est vide : choisissez le fichier d'articles à importer.");
        }
        String nom = nomDuFichier == null ? "" : nomDuFichier.toLowerCase(Locale.ROOT);
        List<List<String>> lignes;
        if (nom.endsWith(".xlsx") || nom.endsWith(".xls")) {
            lignes = lireExcel(contenu);
        } else if (nom.endsWith(".csv") || nom.endsWith(".txt")) {
            lignes = lireTexte(contenu);
        } else {
            throw new RequeteInvalideException("Ce type de fichier n'est pas pris en charge : utilisez un fichier Excel (.xlsx) ou CSV (.csv).");
        }
        return organiser(lignes);
    }

    // ------------------------------------------------------------------ organisation en colonnes reconnues

    private static TableauLu organiser(List<List<String>> toutes) {
        int ligneDesTitres = -1;
        for (int i = 0; i < toutes.size(); i++) {
            if (toutes.get(i).stream().anyMatch(valeur -> !valeur.isBlank())) {
                ligneDesTitres = i;
                break;
            }
        }
        if (ligneDesTitres < 0) {
            throw new RequeteInvalideException("Le fichier ne contient aucune donnée.");
        }
        Map<ColonneImport, Integer> positions = new EnumMap<>(ColonneImport.class);
        List<String> ignorees = new ArrayList<>();
        List<String> titres = toutes.get(ligneDesTitres);
        for (int i = 0; i < titres.size(); i++) {
            String titre = titres.get(i).trim();
            if (titre.isEmpty()) continue;
            var colonne = ColonneImport.reconnaitre(titre);
            if (colonne.isPresent() && !positions.containsKey(colonne.get())) positions.put(colonne.get(), i);
            else ignorees.add(titre);
        }
        List<String> manquantes = new ArrayList<>();
        for (ColonneImport colonne : ColonneImport.values()) {
            if (colonne.obligatoire() && !positions.containsKey(colonne)) manquantes.add("« " + colonne.intitule() + " »");
        }
        if (!manquantes.isEmpty()) {
            throw new RequeteInvalideException("Il manque la colonne " + String.join(" et la colonne ", manquantes)
                + " dans la première ligne du fichier. Téléchargez le modèle pour voir les titres attendus.");
        }
        List<LigneBrute> lignes = new ArrayList<>();
        for (int i = ligneDesTitres + 1; i < toutes.size(); i++) {
            List<String> cellules = toutes.get(i);
            Map<ColonneImport, String> valeurs = new EnumMap<>(ColonneImport.class);
            boolean vide = true;
            for (Map.Entry<ColonneImport, Integer> position : positions.entrySet()) {
                String valeur = position.getValue() < cellules.size() ? cellules.get(position.getValue()).trim() : "";
                if (!valeur.isEmpty()) vide = false;
                valeurs.put(position.getKey(), valeur);
            }
            if (vide) continue;
            if (lignes.size() >= LIGNES_MAX) {
                throw new RequeteInvalideException("Le fichier contient plus de " + LIGNES_MAX + " articles : découpez-le en plusieurs fichiers.");
            }
            lignes.add(new LigneBrute(i + 1, valeurs));
        }
        if (lignes.isEmpty()) {
            throw new RequeteInvalideException("Le fichier ne contient aucun article sous la ligne des titres.");
        }
        return new TableauLu(List.copyOf(positions.keySet()), ignorees, lignes);
    }

    // ------------------------------------------------------------------ Excel

    private static List<List<String>> lireExcel(byte[] contenu) {
        try (Workbook classeur = WorkbookFactory.create(new ByteArrayInputStream(contenu))) {
            Sheet feuille = classeur.getNumberOfSheets() == 0 ? null : classeur.getSheetAt(0);
            if (feuille == null) throw new RequeteInvalideException("Le fichier Excel ne contient aucune feuille.");
            DataFormatter formateur = new DataFormatter(Locale.FRANCE);
            FormulaEvaluator evaluateur = classeur.getCreationHelper().createFormulaEvaluator();
            List<List<String>> lignes = new ArrayList<>();
            int derniere = feuille.getLastRowNum();
            for (int numero = 0; numero <= derniere; numero++) {
                Row ligne = feuille.getRow(numero);
                List<String> cellules = new ArrayList<>();
                if (ligne != null) {
                    for (int colonne = 0; colonne < Math.max(0, ligne.getLastCellNum()); colonne++) {
                        cellules.add(texteDeLaCellule(ligne.getCell(colonne), formateur, evaluateur));
                    }
                }
                lignes.add(cellules);
            }
            return lignes;
        } catch (RequeteInvalideException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new RequeteInvalideException("Ce fichier Excel n'a pas pu être lu : enregistrez-le au format .xlsx puis réessayez.");
        }
    }

    /**
     * Un nombre Excel est lu avec toute sa précision (pas avec le format d'affichage : « 1 500 F » vaut 1500) ; le reste est lu
     * comme il s'affiche (« 1001 » reste « 1001 »).
     */
    private static String texteDeLaCellule(Cell cellule, DataFormatter formateur, FormulaEvaluator evaluateur) {
        if (cellule == null) return "";
        CellType type = cellule.getCellType();
        if (type == CellType.FORMULA) {
            type = evaluateur.evaluateFormulaCell(cellule);
        }
        if (type == CellType.NUMERIC && !org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cellule)) {
            BigDecimal nombre = BigDecimal.valueOf(cellule.getNumericCellValue()).stripTrailingZeros();
            return nombre.scale() < 0 ? nombre.setScale(0).toPlainString() : nombre.toPlainString();
        }
        return formateur.formatCellValue(cellule, evaluateur).trim();
    }

    // ------------------------------------------------------------------ texte (CSV)

    private static List<List<String>> lireTexte(byte[] contenu) {
        String texte = decoder(contenu);
        char separateur = separateur(texte);
        List<List<String>> lignes = new ArrayList<>();
        List<String> ligne = new ArrayList<>();
        StringBuilder cellule = new StringBuilder();
        boolean entreGuillemets = false;
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            if (entreGuillemets) {
                if (c == '"') {
                    if (i + 1 < texte.length() && texte.charAt(i + 1) == '"') {
                        cellule.append('"');
                        i++;
                    } else {
                        entreGuillemets = false;
                    }
                } else {
                    cellule.append(c);
                }
            } else if (c == '"' && cellule.toString().isBlank()) {
                entreGuillemets = true;
                cellule.setLength(0);
            } else if (c == separateur) {
                ligne.add(cellule.toString());
                cellule.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texte.length() && texte.charAt(i + 1) == '\n') i++;
                ligne.add(cellule.toString());
                cellule.setLength(0);
                lignes.add(ligne);
                ligne = new ArrayList<>();
            } else {
                cellule.append(c);
            }
        }
        if (cellule.length() > 0 || !ligne.isEmpty()) {
            ligne.add(cellule.toString());
            lignes.add(ligne);
        }
        return lignes;
    }

    /** UTF-8 (avec ou sans marque d'ordre) ; à défaut, Windows-1252, ce qu'écrit Excel en français pour un CSV. */
    static String decoder(byte[] contenu) {
        int debut = contenu.length >= 3 && (contenu[0] & 0xFF) == 0xEF && (contenu[1] & 0xFF) == 0xBB && (contenu[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(contenu, debut, contenu.length - debut)).toString();
        } catch (CharacterCodingException exception) {
            return new String(contenu, debut, contenu.length - debut, Charset.forName("windows-1252"));
        }
    }

    /** Le séparateur le plus fréquent dans la première ligne non vide : « ; » (Excel français), « , » ou une tabulation. */
    static char separateur(String texte) {
        String premiere = "";
        for (String ligne : texte.split("\n")) {
            if (!ligne.isBlank()) {
                premiere = ligne;
                break;
            }
        }
        int pointsVirgules = 0;
        int virgules = 0;
        int tabulations = 0;
        boolean entreGuillemets = false;
        for (char c : premiere.toCharArray()) {
            if (c == '"') entreGuillemets = !entreGuillemets;
            else if (!entreGuillemets) {
                if (c == ';') pointsVirgules++;
                else if (c == ',') virgules++;
                else if (c == '\t') tabulations++;
            }
        }
        if (pointsVirgules >= virgules && pointsVirgules >= tabulations && pointsVirgules > 0) return ';';
        if (tabulations > virgules && tabulations > 0) return '\t';
        return virgules > 0 ? ',' : ';';
    }
}
