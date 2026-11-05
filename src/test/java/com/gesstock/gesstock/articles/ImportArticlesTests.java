package com.gesstock.gesstock.articles;

import com.gesstock.gesstock.articles.importation.ApercuImport;
import com.gesstock.gesstock.articles.importation.ApercuImport.Statut;
import com.gesstock.gesstock.articles.importation.ImportArticlesService;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.TypeMouvementStock;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.CategorieArticleRepository;
import com.gesstock.gesstock.repositories.JournalActiviteRepository;
import com.gesstock.gesstock.repositories.MouvementStockRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demande du client (novembre 2026) : importer des articles depuis Excel ou CSV. Un aperçu ligne par ligne, puis un import « tout ou
 * rien » qui passe par les mêmes services que l'écran des articles (stock de départ tracé, coût aligné sur le prix d'achat, journal).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImportArticlesTests {

    private static final String TITRES = "Référence;Désignation;Catégorie;Unité;Prix d'achat;Prix de vente;Stock;Seuil d'alerte";

    @Autowired
    private ImportArticlesService importService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private CategorieArticleRepository categorieRepository;
    @Autowired
    private MouvementStockRepository mouvementRepository;
    @Autowired
    private JournalActiviteRepository journalRepository;
    @Autowired
    private MockMvc mockMvc;

    private static byte[] csv(String... lignes) {
        return String.join("\n", lignes).getBytes(StandardCharsets.UTF_8);
    }

    private Article article(String reference) {
        return articleRepository.findByReferenceIgnoreCase(reference).orElseThrow();
    }

    private static byte[] excel(Object[]... lignes) throws Exception {
        try (Workbook classeur = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            Sheet feuille = classeur.createSheet("Articles");
            for (int i = 0; i < lignes.length; i++) {
                Row ligne = feuille.createRow(i);
                for (int j = 0; j < lignes[i].length; j++) {
                    Object valeur = lignes[i][j];
                    if (valeur instanceof Number nombre) ligne.createCell(j).setCellValue(nombre.doubleValue());
                    else if (valeur != null) ligne.createCell(j).setCellValue(valeur.toString());
                }
            }
            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }

    // ------------------------------------------------------------------ l'aperçu

    @Test
    void l_apercuDistingueNouveauModifieIdentiqueEtErreurSansRienEnregistrer() {
        long articles = articleRepository.count();
        ApercuImport apercu = importService.apercu("articles.csv", csv(TITRES,
            "NEW-001;Jus de bissap;Boissons;Litre;\"1 200,50\";1800;12,5;3",
            "ALIM-001;;;;950;;;",
            "ALIM-002;Huile végétale;Alimentation;Litre;1400;1950;;12",
            "ERR-001;;;Caisse;abc;;;"));

        assertThat(apercu.nombreDeLignes()).isEqualTo(4);
        assertThat(apercu.nouveaux()).isEqualTo(1);
        assertThat(apercu.modifies()).isEqualTo(1);
        assertThat(apercu.identiques()).isEqualTo(1);
        assertThat(apercu.erreurs()).isEqualTo(1);
        assertThat(apercu.importable()).as("une erreur bloque tout").isFalse();
        assertThat(apercu.categoriesACreer()).containsExactly("Boissons");

        var nouveau = apercu.lignes().get(0);
        assertThat(nouveau.statut()).isEqualTo(Statut.NOUVEAU);
        assertThat(nouveau.changements()).contains("stock de départ 12.5");
        assertThat(nouveau.avertissements()).anyMatch(message -> message.contains("« Boissons » n'existe pas"));
        var modifie = apercu.lignes().get(1);
        assertThat(modifie.statut()).isEqualTo(Statut.MODIFIE);
        assertThat(modifie.changements()).containsExactly("prix d'achat 900 → 950");
        assertThat(modifie.designation()).as("cellule vide : l'article garde sa désignation").isEqualTo("Riz local");
        assertThat(apercu.lignes().get(2).statut()).isEqualTo(Statut.IDENTIQUE);
        var erreur = apercu.lignes().get(3);
        assertThat(erreur.statut()).isEqualTo(Statut.ERREUR);
        assertThat(erreur.numero()).as("numéro de ligne tel qu'on le voit dans Excel").isEqualTo(5);
        assertThat(erreur.erreurs()).anyMatch(message -> message.contains("désignation est vide"))
            .anyMatch(message -> message.contains("L'unité « Caisse » n'existe pas"))
            .anyMatch(message -> message.contains("« Prix d'achat » : « abc » n'est pas un nombre"));

        assertThat(articleRepository.count()).as("l'aperçu n'enregistre rien").isEqualTo(articles);
        assertThat(categorieRepository.findByNomIgnoreCase("Boissons")).isEmpty();
    }

    @Test
    void lesTitresSontReconnusSansTenirCompteDesAccentsNiDesMajusculesEtLesColonnesInconnuesSontSignalees() {
        ApercuImport apercu = importService.apercu("a.csv", csv(
            "REFERENCE;NOM;PRIX_ACHAT;Prix de vente;Couleur",
            "NEW-002;Savon;300;450;bleu"));

        assertThat(apercu.erreurs()).isZero();
        assertThat(apercu.nouveaux()).isEqualTo(1);
        assertThat(apercu.colonnesIgnorees()).containsExactly("Couleur");
        assertThat(apercu.importable()).isTrue();
    }

    @Test
    void unPrixSousLeCoutOuAZeroEstSignaleMaisPasRefuse() {
        ApercuImport apercu = importService.apercu("a.csv", csv(TITRES,
            "NEW-003;Sucre;;;1000;800;;",
            "NEW-004;Sel;;;;;;"));

        assertThat(apercu.erreurs()).isZero();
        assertThat(apercu.lignes().get(0).avertissements()).anyMatch(message -> message.contains("inférieur au prix d'achat"));
        assertThat(apercu.lignes().get(1).avertissements()).anyMatch(message -> message.contains("Coût à vérifier"))
            .anyMatch(message -> message.contains("Prix de vente absent"));
        assertThat(apercu.avertissements()).isEqualTo(2);
    }

    @Test
    void uneReferenceEnDoubleDansLeFichierEstRefusee() {
        ApercuImport apercu = importService.apercu("a.csv", csv(TITRES, "DBL-1;Un;;;1;2;;", "dbl-1;Deux;;;1;2;;"));

        assertThat(apercu.lignes().get(0).statut()).isEqualTo(Statut.NOUVEAU);
        assertThat(apercu.lignes().get(1).erreurs()).anyMatch(message -> message.contains("apparaît déjà à la ligne 2"));
    }

    @Test
    void lesNombresTropPrecisNegatifsOuIllisiblesSontRefusesAvecLeurLigne() {
        ApercuImport apercu = importService.apercu("a.csv", csv(TITRES,
            "N-1;A;;;1;2;1,2345;",
            "N-2;B;;;-5;2;;",
            "N-3;C;;;1;2;;2,5"));

        assertThat(apercu.lignes().get(0).erreurs()).anyMatch(message -> message.contains("« Stock »") && message.contains("trop de décimales"));
        assertThat(apercu.lignes().get(1).erreurs()).anyMatch(message -> message.contains("n'est pas un nombre positif"));
        assertThat(apercu.lignes().get(2).erreurs()).anyMatch(message -> message.contains("« Seuil d'alerte »") && message.contains("nombre entier"));
    }

    // ------------------------------------------------------------------ la lecture des fichiers

    @Test
    void unCsvEnregistreParExcelEnWindows1252EstLuAvecSesAccents() {
        byte[] octets = String.join("\r\n", TITRES, "CAF-1;Café noir;Boissons chaudes;;500;800;;").getBytes(Charset.forName("windows-1252"));

        ApercuImport apercu = importService.apercu("export-excel.csv", octets);

        assertThat(apercu.erreurs()).isZero();
        assertThat(apercu.lignes().get(0).designation()).isEqualTo("Café noir");
        assertThat(apercu.categoriesACreer()).containsExactly("Boissons chaudes");
    }

    @Test
    void unCsvAvecDesVirgulesDesGuillemetsEtUneMarqueUtf8EstLu() {
        byte[] corps = String.join("\n", "Référence,Désignation,Prix de vente", "VIR-1,\"Pâtes, 500 g\",750").getBytes(StandardCharsets.UTF_8);
        byte[] avecMarque = new byte[corps.length + 3];
        avecMarque[0] = (byte) 0xEF;
        avecMarque[1] = (byte) 0xBB;
        avecMarque[2] = (byte) 0xBF;
        System.arraycopy(corps, 0, avecMarque, 3, corps.length);

        ApercuImport apercu = importService.apercu("a.csv", avecMarque);

        assertThat(apercu.erreurs()).isZero();
        assertThat(apercu.lignes().get(0).reference()).isEqualTo("VIR-1");
        assertThat(apercu.lignes().get(0).designation()).isEqualTo("Pâtes, 500 g");
    }

    @Test
    void unFichierExcelEstLuAvecSesNombresEtSesReferencesNumeriques() throws Exception {
        byte[] fichier = excel(
            new Object[] {"Référence", "Désignation", "Prix d'achat", "Prix de vente", "Stock"},
            new Object[] {1001, "Eau 1,5 L", 300, 500, 20.5});

        importService.importer("articles.xlsx", fichier, "admin");

        Article eau = article("1001");
        assertThat(eau.getDesignation()).isEqualTo("Eau 1,5 L");
        assertThat(eau.getPrixAchat()).isEqualByComparingTo("300");
        assertThat(eau.getPrix()).isEqualByComparingTo("500");
        assertThat(eau.getQuantiteStock()).isEqualByComparingTo("20.5");
    }

    @Test
    void unFichierInutilisableEstRefuseAvecUneExplication() {
        assertThatThrownBy(() -> importService.apercu("a.csv", new byte[0])).isInstanceOf(RequeteInvalideException.class).hasMessageContaining("vide");
        assertThatThrownBy(() -> importService.apercu("a.pdf", csv(TITRES))).isInstanceOf(RequeteInvalideException.class).hasMessageContaining("pas pris en charge");
        assertThatThrownBy(() -> importService.apercu("a.csv", csv("Référence;Prix de vente", "X;1")))
            .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("« Désignation »");
        assertThatThrownBy(() -> importService.apercu("a.csv", csv(TITRES))).isInstanceOf(RequeteInvalideException.class).hasMessageContaining("aucun article");
        assertThatThrownBy(() -> importService.apercu("a.xlsx", "ceci n'est pas un classeur".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("n'a pas pu être lu");
    }

    // ------------------------------------------------------------------ l'import

    @Test
    void importerCreeLesArticlesLesCategoriesLeStockDeDepartEtMetAJourLesExistantsSansToucherAuStock() {
        BigDecimal stockRiz = article("ALIM-001").getQuantiteStock();

        var resultat = importService.importer("catalogue.csv", csv(TITRES,
            "NEW-001;Jus de bissap;Boissons;Litre;\"1 200,50\";1800;12,5;3",
            "ALIM-001;;;;950;1400;999;"), "admin");

        assertThat(resultat.crees()).isEqualTo(1);
        assertThat(resultat.modifies()).isEqualTo(1);
        assertThat(resultat.categoriesCreees()).isEqualTo(1);

        Article jus = article("NEW-001");
        assertThat(jus.getPrixAchat()).isEqualByComparingTo("1200.5");
        assertThat(jus.getCmp()).as("le coût d'un nouvel article est son prix d'achat").isEqualByComparingTo("1200.5");
        assertThat(jus.getQuantiteStock()).isEqualByComparingTo("12.5");
        assertThat(jus.getSeuilAlerte()).isEqualTo(3);
        assertThat(jus.getCategorie().getNom()).isEqualTo("Boissons");
        assertThat(jus.getUniteMesure().getLibelle()).isEqualTo("Litre");
        assertThat(mouvementRepository.findAll()).filteredOn(m -> m.getArticle().getId().equals(jus.getId()))
            .singleElement().satisfies(m -> {
                assertThat(m.getType()).isEqualTo(TypeMouvementStock.STOCK_INITIAL);
                assertThat(m.getQuantite()).isEqualByComparingTo("12.5");
            });

        Article riz = article("ALIM-001");
        assertThat(riz.getPrixAchat()).isEqualByComparingTo("950");
        assertThat(riz.getCmp()).as("comme dans la fiche : le prix d'achat devient le coût du stock en rayon").isEqualByComparingTo("950");
        assertThat(riz.getPrix()).isEqualByComparingTo("1400");
        assertThat(riz.getQuantiteStock()).as("le stock d'un article existant ne change jamais par import").isEqualByComparingTo(stockRiz);
        assertThat(riz.getDesignation()).isEqualTo("Riz local");

        assertThat(journalRepository.findAll()).anySatisfy(entree -> {
            assertThat(entree.getAction()).isEqualTo("IMPORT");
            assertThat(entree.getDetails()).contains("catalogue.csv").contains("1 créé(s)").contains("1 modifié(s)").contains("1 catégorie(s) créée(s)");
        });
    }

    @Test
    void uneSeuleLigneEnErreurEmpecheTousLesImports() {
        long articles = articleRepository.count();
        long categories = categorieRepository.count();

        assertThatThrownBy(() -> importService.importer("a.csv", csv(TITRES,
            "OK-1;Valide;Nouvelle catégorie;;100;150;5;1",
            "KO-1;;;;1;2;;"), "admin"))
            .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("rien n'a été importé");

        assertThat(articleRepository.count()).isEqualTo(articles);
        assertThat(categorieRepository.count()).isEqualTo(categories);
        assertThat(articleRepository.findByReferenceIgnoreCase("OK-1")).isEmpty();
    }

    @Test
    void unFichierSansRienDeNouveauNiDeModifieEstRefuse() {
        assertThatThrownBy(() -> importService.importer("a.csv", csv(TITRES, "ALIM-002;Huile végétale;Alimentation;Litre;1400;1950;;12"), "admin"))
            .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("Rien à importer");
    }

    @Test
    void leCatalogueExporteReimporteTelQuelNeChangeRien() throws Exception {
        byte[] catalogue = importService.modele(true);

        ApercuImport apercu = importService.apercu("catalogue.xlsx", catalogue);

        assertThat(apercu.erreurs()).isZero();
        assertThat(apercu.nouveaux()).isZero();
        assertThat(apercu.modifies()).isZero();
        assertThat(apercu.identiques()).isEqualTo((int) articleRepository.count());
        assertThat(apercu.avertissements()).isZero();
        assertThat(apercu.importable()).isFalse();
    }

    @Test
    void leModeleVideEstUnVraiClasseurAvecSesTitresEtSonModeDEmploi() throws Exception {
        try (Workbook classeur = new XSSFWorkbook(new ByteArrayInputStream(importService.modele(false)))) {
            Sheet articles = classeur.getSheet("Articles");
            assertThat(articles.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Référence");
            assertThat(articles.getRow(0).getCell(4).getStringCellValue()).isEqualTo("Prix d'achat");
            assertThat(articles.getLastRowNum()).as("aucune ligne d'exemple qu'on importerait par erreur").isZero();
            assertThat(classeur.getSheet("Mode d'emploi")).isNotNull();
        }
    }

    // ------------------------------------------------------------------ l'API

    @Test
    void l_apercuEtLImportPassentParLApiPourLeStockEtJamaisPourLaCaisse() throws Exception {
        var fichier = new MockMultipartFile("fichier", "articles.csv", "text/csv", csv(TITRES, "API-1;Biscuit;;;200;300;10;2"));

        mockMvc.perform(multipart("/api/articles/import/apercu").file(fichier).with(user("g").roles("GESTIONNAIRE")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nouveaux").value(1))
            .andExpect(jsonPath("$.importable").value(true))
            .andExpect(jsonPath("$.lignes[0].statut").value("NOUVEAU"));
        mockMvc.perform(multipart("/api/articles/import").file(fichier).with(user("g").roles("MANAGER")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.crees").value(1));
        assertThat(article("API-1").getQuantiteStock()).isEqualByComparingTo("10");

        mockMvc.perform(multipart("/api/articles/import/apercu").file(fichier).with(user("c").roles("CAISSIER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/articles/import").file(fichier).with(user("c").roles("CAISSIER")).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/articles/import/modele").with(user("c").roles("CAISSIER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/articles/import/modele").param("avecArticles", "true").with(user("c").roles("CAISSIER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/articles/import/modele").with(user("g").roles("GESTIONNAIRE")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        mockMvc.perform(get("/api/articles/import/modele")).andExpect(status().isUnauthorized());
    }

    @Test
    void unTypeDeFichierInconnuEstRefuseAvecUn400() throws Exception {
        var fichier = new MockMultipartFile("fichier", "articles.pdf", "application/pdf", "x".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/articles/import/apercu").file(fichier).with(user("g").roles("GESTIONNAIRE")).with(csrf()))
            .andExpect(status().isBadRequest());
    }

}
