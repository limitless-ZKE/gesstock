package com.gesstock.gesstock.ventes;

import com.gesstock.gesstock.common.exception.ApprobationRefuseeException;
import com.gesstock.gesstock.common.exception.ApprobationRequiseException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.JournalActivite;
import com.gesstock.gesstock.entities.LigneVente;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.StatutProforma;
import com.gesstock.gesstock.referentiels.dto.CreateUtilisateurRequest;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.RoleRequest;
import com.gesstock.gesstock.referentiels.dto.RoleResponse;
import com.gesstock.gesstock.referentiels.service.RoleService;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.referentiels.service.UtilisateurService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.JournalActiviteRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
import com.gesstock.gesstock.ventes.dto.ApprobationRequest;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.ProformaRequest;
import com.gesstock.gesstock.ventes.dto.ProformaResponse;
import com.gesstock.gesstock.ventes.dto.VenteDirecteRequest;
import com.gesstock.gesstock.ventes.dto.VenteResponse;
import com.gesstock.gesstock.ventes.service.GestionVentesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un prix négocié au-delà du plafond du vendeur (CAISSIER 5 %, GESTIONNAIRE 10 %, MANAGER 25 %, ADMIN 100 %), ou sous le
 * coût de revient, exige l'accord d'un AUTRE responsable (MANAGER ou ADMIN) : identifiant et mot de passe saisis au comptoir.
 * Article : ELEC-001, catalogue 245 000, coût moyen 189 000.
 */
@SpringBootTest
@Transactional
class ApprobationRemisesTests {

    private static final String MDP_MANAGER = "motdepasse-manager";

    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private UtilisateurService utilisateurService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private VenteRepository venteRepository;
    @Autowired
    private JournalActiviteRepository journalRepository;

    private Article telephone() {
        return articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
    }

    private void creerCompte(String username, String motDePasse, String role) {
        Long roleId = roleService.lister().stream().filter(candidat -> candidat.name().equals(role))
            .map(RoleResponse::id).findFirst().orElseGet(() -> roleService.creer(new RoleRequest(role)).id());
        utilisateurService.creer(new CreateUtilisateurRequest(username, motDePasse, true, Set.of(roleId)));
    }

    private ApprobationRequest accordDuManager() {
        creerCompteManagerSiAbsent();
        return new ApprobationRequest("manager-test", MDP_MANAGER);
    }

    private void creerCompteManagerSiAbsent() {
        if (utilisateurService.lister().stream().noneMatch(u -> u.username().equals("manager-test"))) {
            creerCompte("manager-test", MDP_MANAGER, "MANAGER");
        }
    }

    private VenteDirecteRequest vente(String prixNegocie, ApprobationRequest approbation) {
        return new VenteDirecteRequest(null, false,
            List.of(new LigneDocumentRequest(telephone().getId(), null, 1,
                prixNegocie == null ? null : new BigDecimal(prixNegocie), null)),
            BigDecimal.ZERO, ModeReglement.ESPECES, new BigDecimal("10000000"), approbation);
    }

    private LigneVente premiereLigne(VenteResponse vente) {
        return venteRepository.findById(vente.id()).orElseThrow().getLignesVente().get(0);
    }

    @Test
    void uneRemiseDansLePlafondDuCaissierPasseSansAccordAuCentimePres() {
        VenteResponse dansLePlafond = ventesService.creerVenteDirecte(vente("232750", null), "caissier"); // exactement 5 %

        assertThat(premiereLigne(dansLePlafond).getRemiseApprouveePar()).isNull();
        assertThatThrownBy(() -> ventesService.creerVenteDirecte(vente("232749.99", null), "caissier")) // 5,000004 %
            .isInstanceOf(ApprobationRequiseException.class)
            .hasMessageContaining("ELEC-001")
            .hasMessageContaining("plafond");
    }

    @Test
    void auDelaDuPlafondLAccordDUnManagerFaitPasserLaVenteEtLaisseSaTrace() {
        assertThatThrownBy(() -> ventesService.creerVenteDirecte(vente("196000", null), "caissier")) // 20 %
            .isInstanceOf(ApprobationRequiseException.class);

        VenteResponse vente = ventesService.creerVenteDirecte(vente("196000", accordDuManager()), "caissier");

        assertThat(premiereLigne(vente).getRemiseApprouveePar().getUsername()).isEqualTo("manager-test");
        assertThat(journalRepository.findAll()).extracting(JournalActivite::getAction).contains("APPROBATION_REMISE");
        assertThat(journalRepository.findAll()).filteredOn(entree -> "APPROBATION_REMISE".equals(entree.getAction()))
            .singleElement().satisfies(entree -> assertThat(entree.getDetails())
                .contains("manager-test").contains("caissier").contains(vente.code()));
    }

    @Test
    void unAccordInvalideEstRefuseAvecToujoursLeMemeMessage() {
        creerCompteManagerSiAbsent();
        creerCompte("caissier-refus", "motdepasse-refus", "CAISSIER"); // vendeur dédié : le limiteur compte les échecs par vendeur
        List<ApprobationRequest> accordsInvalides = List.of(
            new ApprobationRequest("manager-test", "mauvais-mot-de-passe"),     // mot de passe faux
            new ApprobationRequest("inconnu-total", MDP_MANAGER),               // compte inexistant
            new ApprobationRequest("caissier-refus", "motdepasse-refus"),       // le vendeur lui-même
            new ApprobationRequest("gestionnaire", "gestionnaire123"));         // bon mot de passe mais pas un responsable

        List<String> messages = accordsInvalides.stream().map(accord -> {
            try {
                ventesService.creerVenteDirecte(vente("196000", accord), "caissier-refus");
                return "ACCEPTE";
            } catch (ApprobationRefuseeException exception) {
                return exception.getMessage();
            }
        }).toList();

        assertThat(messages).doesNotContain("ACCEPTE").hasSize(4);
        assertThat(messages).as("on ne révèle pas lequel des cas est en cause").containsOnly(messages.get(0));
        assertThat(messages.get(0)).contains("Accord refusé");
    }

    @Test
    void lesEssaisDAccordSontLimitesCommeLaConnexion() {
        creerCompte("caissier-limite", "motdepasse-limite", "CAISSIER");
        ApprobationRequest faux = new ApprobationRequest("manager-test", "faux");
        creerCompteManagerSiAbsent();

        for (int essai = 1; essai <= 5; essai++) {
            assertThatThrownBy(() -> ventesService.creerVenteDirecte(vente("196000", faux), "caissier-limite"))
                .isInstanceOf(ApprobationRefuseeException.class).hasMessageContaining("Accord refusé");
        }

        // le 6e essai porte le BON mot de passe : il est refusé quand même pendant le blocage
        assertThatThrownBy(() -> ventesService.creerVenteDirecte(vente("196000", accordDuManager()), "caissier-limite"))
            .isInstanceOf(ApprobationRefuseeException.class).hasMessageContaining("Trop d'accords refusés");
        // un autre vendeur n'est pas touché
        assertThat(ventesService.creerVenteDirecte(vente("196000", accordDuManager()), "caissier").id()).isNotNull();
    }

    @Test
    void unPrixSousLeCoutExigeUnAccordMemePourUnAdministrateur() {
        assertThat(ventesService.creerVenteDirecte(vente("190000", null), "admin").id()).as("au-dessus du coût de 189 000").isNotNull();

        assertThatThrownBy(() -> ventesService.creerVenteDirecte(vente("150000", null), "admin"))
            .isInstanceOf(ApprobationRequiseException.class).hasMessageContaining("coût");
        VenteResponse approuvee = ventesService.creerVenteDirecte(vente("150000", accordDuManager()), "admin");
        assertThat(premiereLigne(approuvee).getRemiseApprouveePar().getUsername()).isEqualTo("manager-test");
    }

    @Test
    void unAdministrateurNePeutPasSeDonnerSonPropreAccord() {
        assertThatThrownBy(() -> ventesService.creerVenteDirecte(
            vente("150000", new ApprobationRequest("admin", "admin123")), "admin"))
            .isInstanceOf(ApprobationRefuseeException.class);
    }

    @Test
    void unProformaSuitLaMemeRegleEtLAccordSuitLaLigneJusqueDansLaVente() {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), "admin");
        List<LigneDocumentRequest> lignes = List.of(
            new LigneDocumentRequest(telephone().getId(), null, 1, new BigDecimal("196000"), null));

        assertThatThrownBy(() -> ventesService.creerProforma(
            new ProformaRequest("PF-REM-1", LocalDate.now(), LocalDate.now().plusDays(3), null, lignes), "caissier"))
            .isInstanceOf(ApprobationRequiseException.class);

        ProformaResponse proforma = ventesService.creerProforma(
            new ProformaRequest("PF-REM-1", LocalDate.now(), LocalDate.now().plusDays(3), null, lignes, accordDuManager()), "caissier");
        ventesService.modifierStatutProforma(proforma.id(), StatutProforma.ENVOYEE, "caissier");
        ventesService.modifierStatutProforma(proforma.id(), StatutProforma.ACCEPTEE, "caissier");
        VenteResponse vente = ventesService.convertirProformaEnVente(
            proforma.id(), false, ModeReglement.ESPECES, new BigDecimal("1000000"), "caissier");

        assertThat(premiereLigne(vente).getRemiseApprouveePar().getUsername())
            .as("l'accord donné au proforma figure sur la ligne de la vente").isEqualTo("manager-test");
    }

    @Test
    void leMotDePasseDeLAccordNeSortJamaisDansLesJournaux() {
        assertThat(new ApprobationRequest("manager", "secret-absolu").toString()).doesNotContain("secret-absolu").contains("manager");
    }
}
