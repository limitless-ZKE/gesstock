package com.gesstock.gesstock.journal;

import com.gesstock.gesstock.articles.dto.ArticleRequest;
import com.gesstock.gesstock.articles.dto.ArticleResponse;
import com.gesstock.gesstock.articles.dto.CategorieRequest;
import com.gesstock.gesstock.articles.dto.CategorieResponse;
import com.gesstock.gesstock.articles.service.ArticleCatalogueService;
import com.gesstock.gesstock.articles.service.CategorieArticleService;
import com.gesstock.gesstock.clients.dto.ClientRequest;
import com.gesstock.gesstock.clients.dto.ClientResponse;
import com.gesstock.gesstock.clients.service.ClientCatalogueService;
import com.gesstock.gesstock.entities.JournalActivite;
import com.gesstock.gesstock.journal.dto.JournalOperationsResponse;
import com.gesstock.gesstock.journal.dto.NombreParLibelle;
import com.gesstock.gesstock.journal.dto.OperationJournal;
import com.gesstock.gesstock.journal.service.JournalOperationsExportService;
import com.gesstock.gesstock.journal.service.JournalOperationsService;
import com.gesstock.gesstock.journal.service.OperationsConnues.Famille;
import com.gesstock.gesstock.referentiels.dto.CreateUtilisateurRequest;
import com.gesstock.gesstock.referentiels.dto.UpdateUtilisateurRequest;
import com.gesstock.gesstock.referentiels.dto.UtilisateurResponse;
import com.gesstock.gesstock.referentiels.service.UtilisateurService;
import com.gesstock.gesstock.repositories.JournalActiviteRepository;
import com.gesstock.gesstock.repositories.RoleRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Journal des opérations : chaque opération se lit en clair et se range dans sa famille ; il se filtre par utilisateur et par
 * famille ; les nouvelles traces (articles, catégories, clients, comptes) disent ce qui a changé ; les documents reprennent les
 * mêmes lignes. Les opérations « de lecture » sont datées du 1er mars 2001 : aucune autre opération des tests ne tombe ce jour-là.
 */
@SpringBootTest
@Transactional
class JournalOperationsTests {

    private static final LocalDate JOUR = LocalDate.of(2001, 3, 1);
    private static final DataFormatter FORMAT = new DataFormatter();

    @Autowired
    private JournalOperationsService operationsService;
    @Autowired
    private JournalOperationsExportService exportService;
    @Autowired
    private JournalActiviteRepository journalRepository;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ArticleCatalogueService articleService;
    @Autowired
    private CategorieArticleService categorieService;
    @Autowired
    private ClientCatalogueService clientService;
    @Autowired
    private UtilisateurService utilisateurService;

    private void operation(String action, String entite, String utilisateur, int heure, String details) {
        JournalActivite activite = new JournalActivite();
        activite.setAction(action);
        activite.setEntite(entite);
        activite.setDetails(details);
        activite.setDateActivite(JOUR.atTime(heure, 0));
        if (utilisateur != null) {
            activite.setUtilisateur(utilisateurRepository.findByUsername(utilisateur).orElseThrow());
        }
        journalRepository.save(activite);
    }

    private void preparerLaJournee() {
        operation("CONNEXION_REFUSEE", "COMPTE", null, 7, "Connexion refusée pour l'identifiant inconnu « pirate »");
        operation("OUVERTURE", "SESSION_CAISSE", "admin", 8, "Ouverture de journée avec fond de caisse 1000");
        operation("VALIDATION", "VENTE", "caissier", 9, "Validation de la vente VTE-1, total 1300");
        operation("ANNULATION", "VENTE", "admin", 10, "Annulation de la vente au comptant VTE-1 (erreur de saisie)");
        operation("ACTION_INCONNUE", "TRUC", "admin", 11, "?");
    }

    @Test
    void chaqueOperationSeLitEnClairEtSeRangeDansSaFamille() {
        preparerLaJournee();

        JournalOperationsResponse journal = operationsService.journal(JOUR, JOUR, null, null);

        assertThat(journal.nombre()).isEqualTo(5);
        assertThat(journal.operations()).extracting(OperationJournal::operation, OperationJournal::famille, OperationJournal::utilisateur)
            .as("les plus récentes d'abord")
            .containsExactly(
                tuple("Action inconnue · truc", "Autres", "admin"),
                tuple("Vente annulée", "Ventes", "admin"),
                tuple("Vente validée", "Ventes", "caissier"),
                tuple("Ouverture de la caisse", "Caisse", "admin"),
                tuple("Connexion refusée", "Connexions et comptes", null));
        assertThat(journal.parUtilisateur()).extracting(NombreParLibelle::libelle, NombreParLibelle::nombre)
            .containsExactly(tuple("admin", 3L), tuple("caissier", 1L), tuple("Sans compte", 1L));
        assertThat(journal.operations().get(2).element()).isEqualTo("VENTE");
    }

    @Test
    void leJournalSeFiltreParUtilisateurEtParFamille() {
        preparerLaJournee();

        assertThat(operationsService.journal(JOUR, JOUR, "CAISSIER", null).operations())
            .extracting(OperationJournal::operation).containsExactly("Vente validée");
        assertThat(operationsService.journal(JOUR, JOUR, null, Famille.VENTES).operations())
            .extracting(OperationJournal::operation).containsExactly("Vente annulée", "Vente validée");
        assertThat(operationsService.journal(JOUR, JOUR, "admin", Famille.CAISSE).nombre()).isEqualTo(1);
        assertThat(operationsService.journal(JOUR.plusDays(1), JOUR.plusDays(1), null, null).operations()).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin")
    void lesChangementsDuCatalogueDesClientsEtDesComptesSontEnregistresAvecCeQuiAChange() {
        ArticleResponse article = articleService.creer(new ArticleRequest("JRN-001", "Eau minérale 1,5 L",
            new BigDecimal("250"), new BigDecimal("400"), 5, 0, null, null));
        ArticleRequest nouveauPrix = new ArticleRequest("JRN-001", "Eau minérale 1,5 L", new BigDecimal("250"), new BigDecimal("450"), 5, null, null, null);
        articleService.modifier(article.id(), nouveauPrix);
        articleService.modifier(article.id(), nouveauPrix);   // rien ne change : aucune trace
        articleService.supprimer(article.id());
        CategorieResponse categorie = categorieService.creer(new CategorieRequest("Eaux du journal", null, true, null));
        categorieService.modifier(categorie.id(), new CategorieRequest("Eaux minérales du journal", null, true, null));
        ClientResponse client = clientService.creer(new ClientRequest("Client du journal", null, "770000000", "Dakar"));
        clientService.modifier(client.id(), new ClientRequest("Client du journal", null, "780000000", "Dakar"));
        clientService.supprimer(client.id());
        Long caissier = roleRepository.findByName("CAISSIER").orElseThrow().getId();
        Long gestionnaire = roleRepository.findByName("GESTIONNAIRE").orElseThrow().getId();
        UtilisateurResponse compte = utilisateurService.creer(new CreateUtilisateurRequest("awa.journal", "motdepasse-1", true, Set.of(caissier)));
        utilisateurService.modifier(compte.id(), new UpdateUtilisateurRequest("awa.journal", "motdepasse-2", false, Set.of(gestionnaire)), "admin");

        JournalOperationsResponse journal = operationsService.journal(LocalDate.now(), LocalDate.now(), "admin", null);
        List<String> lignes = journal.operations().stream().map(operation -> operation.operation() + " : " + operation.details()).toList();

        assertThat(lignes).contains(
            "Article créé : Article JRN-001 « Eau minérale 1,5 L » créé : prix de vente 400, prix d'achat 250",
            "Article modifié : Article JRN-001 « Eau minérale 1,5 L » : prix de vente 400 → 450",
            "Article supprimé : Article JRN-001 « Eau minérale 1,5 L » supprimé (prix de vente 450)",
            "Catégorie créée : Catégorie « Eaux du journal » créée",
            "Catégorie modifiée : Catégorie « Eaux du journal » renommée « Eaux minérales du journal »",
            "Client créé : Client « Client du journal » créé, téléphone 770000000",
            "Client modifié : Client « Client du journal » : téléphone « 770000000 » → « 780000000 »",
            "Client supprimé : Client « Client du journal » supprimé",
            "Compte créé : Compte « awa.journal » créé : CAISSIER",
            "Compte modifié : Compte « awa.journal » : rôles CAISSIER → GESTIONNAIRE, désactivé, mot de passe changé");
        assertThat(lignes).filteredOn(ligne -> ligne.startsWith("Article modifié : Article JRN-001"))
            .as("une modification sans changement ne laisse pas de trace").hasSize(1);
        assertThat(journal.operations()).filteredOn(operation -> operation.details().contains("journal") || operation.details().contains("JRN-001"))
            .allSatisfy(operation -> assertThat(operation.utilisateur()).as("l'auteur est le compte connecté").isEqualTo("admin"));
    }

    @Test
    void lesDocumentsReprennentLesMemesOperations() throws Exception {
        preparerLaJournee();

        String deLAdmin = texteDuPdf(exportService.exporterPdf(JOUR, JOUR, "admin", null));
        assertThat(deLAdmin).contains("Journal des opérations", "utilisateur : admin", "Vente annulée", "Ouverture de la caisse", "erreur de saisie")
            .doesNotContain("Vente validée");

        String connexions = texteDuPdf(exportService.exporterPdf(JOUR, JOUR, null, Famille.COMPTES));
        assertThat(connexions).contains("tous les utilisateurs", "Connexions et comptes", "Connexion refusée", "pirate").doesNotContain("Vente annulée");

        try (Workbook classeur = new XSSFWorkbook(new ByteArrayInputStream(exportService.exporterExcel(JOUR, JOUR, null, null)))) {
            List<String> textes = new ArrayList<>();
            for (Row ligne : classeur.getSheetAt(0)) ligne.forEach(cellule -> textes.add(FORMAT.formatCellValue(cellule)));
            assertThat(classeur.getSheetAt(0).getSheetName()).isEqualTo("Journal des opérations");
            assertThat(textes).contains("Journal des opérations", "Vente validée", "caissier", "Ventes", "VENTE", "Connexion refusée");
        }
    }

    /** Texte du PDF, espaces insécables ramenées à des espaces ordinaires. */
    private static String texteDuPdf(byte[] pdf) throws Exception {
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        PdfReader lecteur = new PdfReader(pdf);
        PdfTextExtractor extracteur = new PdfTextExtractor(lecteur);
        StringBuilder texte = new StringBuilder();
        for (int page = 1; page <= lecteur.getNumberOfPages(); page++) texte.append(extracteur.getTextFromPage(page)).append(' ');
        lecteur.close();
        String brut = texte.toString().replace((char) 0xA0, ' ').replace((char) 0x202F, ' ');
        while (brut.contains("  ")) brut = brut.replace("  ", " ");
        return brut;
    }
}
