package com.gesstock.gesstock.articles.importation;

import com.gesstock.gesstock.articles.dto.ArticleRequest;
import com.gesstock.gesstock.articles.dto.CategorieRequest;
import com.gesstock.gesstock.articles.importation.AnalyseurImportArticles.Operation;
import com.gesstock.gesstock.articles.importation.AnalyseurImportArticles.Plan;
import com.gesstock.gesstock.articles.service.ArticleCatalogueService;
import com.gesstock.gesstock.articles.service.CategorieArticleService;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.CategorieArticle;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.CategorieArticleRepository;
import com.gesstock.gesstock.repositories.UniteMesureRepository;
import org.apache.poi.ss.usermodel.CellStyle;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Importe des articles depuis un fichier Excel ou CSV : un aperçu ligne par ligne d'abord, puis l'import, qui est TOUT OU RIEN (une
 * seule erreur dans le fichier et rien n'est enregistré). Les articles passent par les mêmes services que l'écran des articles :
 * mêmes règles, même journal, même stock de départ tracé.
 */
@Service
public class ImportArticlesService {

    /** Ce qui a été fait. */
    public record ResultatImport(int crees, int modifies, int identiques, int categoriesCreees) {}

    private final ArticleRepository articleRepository;
    private final CategorieArticleRepository categorieRepository;
    private final UniteMesureRepository uniteRepository;
    private final ArticleCatalogueService articleService;
    private final CategorieArticleService categorieService;
    private final JournalActiviteService journalService;

    public ImportArticlesService(ArticleRepository articleRepository, CategorieArticleRepository categorieRepository,
                                 UniteMesureRepository uniteRepository, ArticleCatalogueService articleService,
                                 CategorieArticleService categorieService, JournalActiviteService journalService) {
        this.articleRepository = articleRepository;
        this.categorieRepository = categorieRepository;
        this.uniteRepository = uniteRepository;
        this.articleService = articleService;
        this.categorieService = categorieService;
        this.journalService = journalService;
    }

    private Plan planifier(String nomDuFichier, byte[] contenu) {
        var tableau = LecteurFichierArticles.lire(nomDuFichier, contenu);
        Map<String, Article> articles = articleRepository.findAll().stream()
            .collect(Collectors.toMap(article -> article.getReference().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        Map<String, CategorieArticle> categories = new HashMap<>();
        for (CategorieArticle categorie : categorieRepository.findAll()) categories.putIfAbsent(categorie.getNom().toLowerCase(Locale.ROOT), categorie);
        return AnalyseurImportArticles.analyser(tableau, articles, categories, uniteRepository.findAll());
    }

    @Transactional(readOnly = true)
    public ApercuImport apercu(String nomDuFichier, byte[] contenu) {
        return planifier(nomDuFichier, contenu).apercu();
    }

    @Transactional
    public ResultatImport importer(String nomDuFichier, byte[] contenu, String username) {
        Plan plan = planifier(nomDuFichier, contenu);
        ApercuImport apercu = plan.apercu();
        if (apercu.erreurs() > 0) {
            throw new RequeteInvalideException("Le fichier contient " + apercu.erreurs() + " ligne(s) en erreur : rien n'a été importé. "
                + "Corrigez le fichier puis recommencez.");
        }
        if (apercu.nouveaux() + apercu.modifies() == 0) {
            throw new RequeteInvalideException("Rien à importer : aucun article nouveau ni modifié dans ce fichier.");
        }
        Map<String, Long> categoriesCreees = new HashMap<>();
        for (String nom : apercu.categoriesACreer()) {
            var creee = categorieService.creer(new CategorieRequest(nom, null, true, null));
            categoriesCreees.put(nom.toLowerCase(Locale.ROOT), creee.id());
        }
        for (Operation operation : plan.operations()) {
            Long categorieId = operation.categorieId() != null ? operation.categorieId()
                : operation.categorieACreer() == null ? null : categoriesCreees.get(operation.categorieACreer().toLowerCase(Locale.ROOT));
            var requete = new ArticleRequest(operation.reference(), operation.designation(), operation.prixAchat(), operation.prixVente(),
                operation.seuil(), operation.stock(), categorieId, operation.uniteId());
            switch (operation.statut()) {
                case NOUVEAU -> articleService.creer(requete, username);
                case MODIFIE -> articleService.modifier(operation.existant().getId(), requete);
                default -> { /* identique : rien à faire */ }
            }
        }
        journalService.enregistrer("IMPORT", "ARTICLE", null, "Import d'articles (fichier « " + nomDuFichier + " ») : " + apercu.nouveaux()
            + " créé(s), " + apercu.modifies() + " modifié(s), " + apercu.identiques() + " identique(s)"
            + (categoriesCreees.isEmpty() ? "" : ", " + categoriesCreees.size() + " catégorie(s) créée(s)"), username);
        return new ResultatImport(apercu.nouveaux(), apercu.modifies(), apercu.identiques(), categoriesCreees.size());
    }

    // ------------------------------------------------------------------ le modèle à télécharger

    /**
     * Le fichier Excel de départ : les titres des colonnes et un mode d'emploi, ou (avec les articles) le catalogue actuel dans le même
     * format, à modifier puis à réimporter.
     */
    @Transactional(readOnly = true)
    public byte[] modele(boolean avecLesArticles) throws IOException {
        try (Workbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            CellStyle entete = classeur.createCellStyle();
            entete.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font gras = classeur.createFont();
            gras.setBold(true);
            entete.setFont(gras);

            Sheet feuille = classeur.createSheet("Articles");
            Row titres = feuille.createRow(0);
            ColonneImport[] colonnes = ColonneImport.values();
            for (int i = 0; i < colonnes.length; i++) {
                var cellule = titres.createCell(i);
                cellule.setCellValue(colonnes[i].intitule());
                cellule.setCellStyle(entete);
            }
            if (avecLesArticles) {
                int numero = 1;
                var articles = articleRepository.findAll().stream()
                    .sorted(Comparator.comparing((Article article) -> article.getCategorie() == null ? "~" : article.getCategorie().getNom(),
                            String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Article::getDesignation, String.CASE_INSENSITIVE_ORDER))
                    .toList();
                for (Article article : articles) {
                    Row ligne = feuille.createRow(numero++);
                    ligne.createCell(0).setCellValue(article.getReference());
                    ligne.createCell(1).setCellValue(article.getDesignation());
                    ligne.createCell(2).setCellValue(article.getCategorie() == null ? "" : article.getCategorie().getNom());
                    ligne.createCell(3).setCellValue(article.getUniteMesure() == null ? "" : article.getUniteMesure().getLibelle());
                    ligne.createCell(4).setCellValue(article.getPrixAchat().doubleValue());
                    ligne.createCell(5).setCellValue(article.getPrix().doubleValue());
                    ligne.createCell(6).setCellValue(article.getQuantiteStock().doubleValue());
                    ligne.createCell(7).setCellValue(article.getSeuilAlerte());
                }
            }
            for (int i = 0; i < colonnes.length; i++) feuille.autoSizeColumn(i);
            feuille.createFreezePane(0, 1);

            Sheet aide = classeur.createSheet("Mode d'emploi");
            String[] lignes = {
                "IMPORTER DES ARTICLES DANS GESSTOCK",
                "",
                "1. Remplissez la feuille « Articles » : une ligne par article. Ne changez pas les titres de la première ligne.",
                "2. Dans GesStock, ouvrez « Catalogue articles », cliquez sur « Importer », choisissez ce fichier et vérifiez l'aperçu.",
                "3. Rien n'est enregistré tant que vous n'avez pas cliqué sur « Importer ». S'il y a une erreur, rien n'est importé : corrigez et recommencez.",
                "",
                "LES COLONNES",
                "Référence (obligatoire) : le code unique de l'article. S'il existe déjà dans GesStock, l'article est MODIFIÉ ; sinon il est CRÉÉ.",
                "Désignation (obligatoire pour un nouvel article) : le nom de l'article.",
                "Catégorie : créée automatiquement si elle n'existe pas encore.",
                "Unité : doit déjà exister dans GesStock (menu « Catégories & unités »), par exemple casier ou pack.",
                "Prix d'achat et Prix de vente : en francs, sans symbole. Virgule ou point pour les centimes (1500,50).",
                "   Le prix d'achat d'un article existant devient le coût de tout son stock en rayon (comme dans sa fiche).",
                "Stock : sert seulement pour un NOUVEL article (son stock de départ). Pour un article existant il est ignoré :",
                "   le stock ne change que par un approvisionnement ou « Ajuster le stock ».",
                "Seuil d'alerte : un nombre entier (le stock à partir duquel l'article est « bas »).",
                "",
                "Une cellule vide laisse un article existant inchangé. Pour un nouvel article, elle vaut 0.",
                "Vous pouvez aussi enregistrer le fichier au format CSV (séparateur point-virgule)."
            };
            for (int i = 0; i < lignes.length; i++) aide.createRow(i).createCell(0).setCellValue(lignes[i]);
            aide.setColumnWidth(0, 120 * 256);

            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }
}
