package com.gesstock.gesstock.articles.service;

import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.common.export.ExportSupport;
import com.gesstock.gesstock.common.export.TableauPdf;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entreprise.dto.EntrepriseResponse;
import com.gesstock.gesstock.entreprise.service.EnteteExcel;
import com.gesstock.gesstock.entreprise.service.EntetePdf;
import com.gesstock.gesstock.entreprise.service.EntrepriseService;
import com.gesstock.gesstock.entreprise.service.TailleLogo;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.CategorieArticleRepository;
import com.gesstock.gesstock.stock.Quantites;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Listes imprimables des articles, classés par catégorie : tout le catalogue ou seulement le stock disponible, bas ou en
 * rupture, avec ou sans le stock. Ne montre jamais le prix d'achat (la liste peut être remise à un caissier ou à un client).
 */
@Service
@Transactional(readOnly = true)
public class ArticleExportService {

    /** Quels articles : les mêmes filtres que l'écran des articles. */
    public enum FiltreStock {
        TOUS("Tous les articles", "tous"),
        DISPONIBLE("Stock disponible", "disponible"),
        BAS("Stock bas", "stock-bas"),
        RUPTURE("En rupture", "rupture");

        private final String libelle;
        private final String nomDeFichier;

        FiltreStock(String libelle, String nomDeFichier) {
            this.libelle = libelle;
            this.nomDeFichier = nomDeFichier;
        }

        public String libelle() { return libelle; }

        public String nomDeFichier() { return nomDeFichier; }

        boolean retient(Article article) {
            BigDecimal stock = article.getQuantiteStock();
            return switch (this) {
                case TOUS -> true;
                case DISPONIBLE -> stock.signum() > 0;
                case BAS -> stock.signum() > 0 && stock.compareTo(BigDecimal.valueOf(article.getSeuilAlerte())) <= 0;
                case RUPTURE -> stock.signum() <= 0;
            };
        }

        /** Lit le paramètre de l'adresse ; vide = tous les articles. */
        public static FiltreStock de(String texte) {
            if (texte == null || texte.isBlank()) return TOUS;
            String propre = texte.trim().toLowerCase(Locale.ROOT);
            for (FiltreStock filtre : values()) {
                if (filtre.name().toLowerCase(Locale.ROOT).equals(propre) || filtre.nomDeFichier.equals(propre)) return filtre;
            }
            throw new RequeteInvalideException("Filtre de stock inconnu : « " + texte + " » (tous, disponible, bas ou rupture)");
        }
    }

    private static final String SANS_CATEGORIE = "Sans catégorie";

    private final ArticleRepository articleRepository;
    private final CategorieArticleRepository categorieRepository;
    private final EntrepriseService entrepriseService;

    public ArticleExportService(ArticleRepository articleRepository, CategorieArticleRepository categorieRepository,
                                EntrepriseService entrepriseService) {
        this.articleRepository = articleRepository;
        this.categorieRepository = categorieRepository;
        this.entrepriseService = entrepriseService;
    }

    /** Les articles retenus, regroupés par catégorie (ordre alphabétique, « Sans catégorie » en dernier), puis par nom. */
    Map<String, List<Article>> regrouper(FiltreStock filtre, Long categorieId) {
        if (categorieId != null && !categorieRepository.existsById(categorieId)) {
            throw new RequeteInvalideException("Catégorie introuvable : " + categorieId);
        }
        Comparator<String> parCategorie = Comparator.comparing((String nom) -> SANS_CATEGORIE.equals(nom))
            .thenComparing(String.CASE_INSENSITIVE_ORDER);
        Map<String, List<Article>> groupes = new TreeMap<>(parCategorie);
        for (Article article : articleRepository.findAll()) {
            if (!filtre.retient(article)) continue;
            if (categorieId != null && (article.getCategorie() == null || !categorieId.equals(article.getCategorie().getId()))) continue;
            String nom = article.getCategorie() == null ? SANS_CATEGORIE : article.getCategorie().getNom();
            groupes.computeIfAbsent(nom, cle -> new ArrayList<>()).add(article);
        }
        Comparator<Article> parNom = Comparator.comparing(Article::getNom, String.CASE_INSENSITIVE_ORDER);
        groupes.values().forEach(liste -> liste.sort(parNom));
        return new LinkedHashMap<>(groupes);
    }

    /** Même regroupement, pour les tests (le service le garde privé au paquet). */
    public Map<String, List<Article>> regrouperPourTest(FiltreStock filtre, Long categorieId) {
        return regrouper(filtre, categorieId);
    }

    private static BigDecimal valeurRevente(Article article) {
        return Quantites.montant(article.getPrix(), article.getQuantiteStock());
    }

    private static String unite(Article article) {
        return article.getUniteMesure() == null ? "" : article.getUniteMesure().getLibelle();
    }

    private String sousTitre(FiltreStock filtre, Long categorieId, boolean avecStock) {
        String categorie = categorieId == null ? "toutes les catégories"
            : "catégorie " + categorieRepository.findById(categorieId).map(c -> c.getNom()).orElse("?");
        return filtre.libelle() + " · " + categorie + (avecStock ? " · avec le stock" : " · liste simple")
            + " · édité le " + ExportSupport.jourHeure(LocalDateTime.now());
    }

    // ------------------------------------------------------------------ PDF

    public byte[] exporterPdf(FiltreStock filtre, Long categorieId, boolean avecStock) throws IOException {
        Map<String, List<Article>> groupes = regrouper(filtre, categorieId);
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            EntrepriseResponse entreprise = entrepriseService.obtenir();
            Document document = ExportSupport.documentPortrait(sortie, entreprise.nom() + " · Liste des articles");
            try {
                EntetePdf.ajouter(document, entreprise, entrepriseService.logo(TailleLogo.ENTETE).map(EntrepriseService.LogoServi::contenu).orElse(null));
                document.add(new Paragraph("Liste des articles par catégorie", ExportSupport.gras(16)));
                document.add(new Paragraph(sousTitre(filtre, categorieId, avecStock), ExportSupport.normal(9)));
                document.add(new Paragraph(" ", ExportSupport.normal(6)));

                TableauPdf tableau = avecStock
                    ? TableauPdf.creer(new float[] {1.3f, 3.4f, 1f, 1f, 0.8f, 1.2f, 1.4f}, new boolean[] {false, false, false, true, true, true, true},
                        "Référence", "Désignation", "Unité", "Stock", "Seuil", "Prix de vente", "Valeur à la revente")
                    : TableauPdf.creer(new float[] {1.4f, 4.2f, 1.2f, 1.4f}, new boolean[] {false, false, false, true},
                        "Référence", "Désignation", "Unité", "Prix de vente");
                long nombre = 0;
                BigDecimal valeurTotale = BigDecimal.ZERO;
                for (Map.Entry<String, List<Article>> groupe : groupes.entrySet()) {
                    List<Article> articles = groupe.getValue();
                    BigDecimal valeurGroupe = articles.stream().map(ArticleExportService::valeurRevente).reduce(BigDecimal.ZERO, BigDecimal::add);
                    String resume = articles.size() + (articles.size() > 1 ? " articles" : " article");
                    if (avecStock) tableau.sousTotal(groupe.getKey(), resume, "", "", "", "", ExportSupport.montant(valeurGroupe));
                    else tableau.sousTotal(groupe.getKey(), resume, "", "");
                    for (Article article : articles) {
                        if (avecStock) {
                            tableau.ligne(article.getReference(), article.getNom(), unite(article), Quantites.texte(article.getQuantiteStock()),
                                Integer.toString(article.getSeuilAlerte()), ExportSupport.montant(article.getPrix()),
                                ExportSupport.montant(valeurRevente(article)));
                        } else {
                            tableau.ligne(article.getReference(), article.getNom(), unite(article), ExportSupport.montant(article.getPrix()));
                        }
                    }
                    nombre += articles.size();
                    valeurTotale = valeurTotale.add(valeurGroupe);
                }
                if (nombre == 0) {
                    document.add(new Paragraph("Aucun article ne correspond à ce choix.", ExportSupport.normal(10)));
                } else {
                    String resumeTotal = nombre + (nombre > 1 ? " articles" : " article");
                    if (avecStock) tableau.sousTotal("Total", resumeTotal, "", "", "", "", ExportSupport.montant(valeurTotale));
                    else tableau.sousTotal("Total", resumeTotal, "", "");
                    document.add(tableau.table());
                    if (avecStock) {
                        document.add(new Paragraph("Valeur à la revente = stock × prix de vente.", ExportSupport.normal(8)));
                    }
                }
                document.close();
            } catch (DocumentException exception) {
                throw new IOException("La génération du PDF de la liste des articles a échoué", exception);
            }
            return sortie.toByteArray();
        }
    }

    // ------------------------------------------------------------------ Excel

    public byte[] exporterExcel(FiltreStock filtre, Long categorieId, boolean avecStock) throws IOException {
        Map<String, List<Article>> groupes = regrouper(filtre, categorieId);
        try (Workbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            CellStyle entete = classeur.createCellStyle();
            entete.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font gras = classeur.createFont();
            gras.setBold(true);
            entete.setFont(gras);
            DataFormat format = classeur.createDataFormat();
            CellStyle montant = classeur.createCellStyle();
            montant.setDataFormat(format.getFormat("#,##0.##"));
            CellStyle quantite = classeur.createCellStyle();
            quantite.setDataFormat(format.getFormat("0.###"));
            CellStyle total = classeur.createCellStyle();
            total.setFont(gras);
            total.setDataFormat(format.getFormat("#,##0.##"));

            Sheet feuille = classeur.createSheet("Articles");
            int debut = EnteteExcel.ecrire(classeur, feuille, entrepriseService.obtenir(),
                entrepriseService.logo(TailleLogo.MINIATURE).map(EntrepriseService.LogoServi::contenu).orElse(null));
            feuille.createRow(debut).createCell(0).setCellValue("Liste des articles par catégorie · " + sousTitre(filtre, categorieId, avecStock));
            String[] colonnes = avecStock
                ? new String[] {"Catégorie", "Référence", "Désignation", "Unité", "Stock", "Seuil d'alerte", "Prix de vente", "Valeur à la revente"}
                : new String[] {"Catégorie", "Référence", "Désignation", "Unité", "Prix de vente"};
            Row ligneEntete = feuille.createRow(debut + 2);
            for (int i = 0; i < colonnes.length; i++) {
                var cellule = ligneEntete.createCell(i);
                cellule.setCellValue(colonnes[i]);
                cellule.setCellStyle(entete);
            }
            int numero = debut + 3;
            BigDecimal valeurTotale = BigDecimal.ZERO;
            for (Map.Entry<String, List<Article>> groupe : groupes.entrySet()) {
                for (Article article : groupe.getValue()) {
                    Row row = feuille.createRow(numero++);
                    row.createCell(0).setCellValue(groupe.getKey());
                    row.createCell(1).setCellValue(article.getReference());
                    row.createCell(2).setCellValue(article.getNom());
                    row.createCell(3).setCellValue(unite(article));
                    if (avecStock) {
                        var cStock = row.createCell(4);
                        cStock.setCellValue(article.getQuantiteStock().doubleValue());
                        cStock.setCellStyle(quantite);
                        row.createCell(5).setCellValue(article.getSeuilAlerte());
                        var cPrix = row.createCell(6);
                        cPrix.setCellValue(article.getPrix().doubleValue());
                        cPrix.setCellStyle(montant);
                        var cValeur = row.createCell(7);
                        cValeur.setCellValue(valeurRevente(article).doubleValue());
                        cValeur.setCellStyle(montant);
                        valeurTotale = valeurTotale.add(valeurRevente(article));
                    } else {
                        var cPrix = row.createCell(4);
                        cPrix.setCellValue(article.getPrix().doubleValue());
                        cPrix.setCellStyle(montant);
                    }
                }
            }
            if (avecStock) {
                Row ligneTotal = feuille.createRow(numero);
                ligneTotal.createCell(0).setCellValue("Total");
                var cTotal = ligneTotal.createCell(7);
                cTotal.setCellValue(valeurTotale.doubleValue());
                cTotal.setCellStyle(total);
            }
            for (int i = 0; i < colonnes.length; i++) feuille.autoSizeColumn(i);
            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }
}
