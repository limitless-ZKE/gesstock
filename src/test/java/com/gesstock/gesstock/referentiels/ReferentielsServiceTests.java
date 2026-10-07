package com.gesstock.gesstock.referentiels;

import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.entities.StatutSessionCaisse;
import com.gesstock.gesstock.referentiels.dto.ClotureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.CreateUtilisateurRequest;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.RoleResponse;
import com.gesstock.gesstock.referentiels.dto.SessionCaisseResponse;
import com.gesstock.gesstock.referentiels.dto.UniteMesureRequest;
import com.gesstock.gesstock.referentiels.dto.UniteMesureResponse;
import com.gesstock.gesstock.referentiels.dto.UpdateUtilisateurRequest;
import com.gesstock.gesstock.referentiels.dto.UtilisateurResponse;
import com.gesstock.gesstock.referentiels.service.RoleService;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.referentiels.service.UniteMesureService;
import com.gesstock.gesstock.referentiels.service.UtilisateurService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les données de démonstration (utilisateur « admin », rôles) viennent du DataSeeder. */
@SpringBootTest
@Transactional
class ReferentielsServiceTests {

    private static final String ADMIN = "admin";

    @Autowired
    private UtilisateurService utilisateurService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private UniteMesureService uniteService;

    private Long unRoleId() {
        return roleService.lister().stream().map(RoleResponse::id).findFirst().orElseThrow();
    }

    // --- Utilisateurs et rôles ---

    @Test
    void utilisateurCreeModifieEtSupprime() {
        Long roleId = unRoleId();
        UtilisateurResponse cree = utilisateurService.creer(
            new CreateUtilisateurRequest("caissier-test", "motdepasse1", null, Set.of(roleId)));
        assertThat(cree.actif()).isTrue();
        assertThat(cree.roles()).extracting(RoleResponse::id).containsExactly(roleId);

        UtilisateurResponse modifie = utilisateurService.modifier(cree.id(),
            new UpdateUtilisateurRequest("caissier-test2", null, false, Set.of(roleId)), ADMIN);
        assertThat(modifie.username()).isEqualTo("caissier-test2");
        assertThat(modifie.actif()).isFalse();

        utilisateurService.supprimer(cree.id(), ADMIN);
        assertThatThrownBy(() -> utilisateurService.obtenir(cree.id()))
            .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    void nomUtilisateurDejaPrisEstRefuse() {
        assertThatThrownBy(() -> utilisateurService.creer(
            new CreateUtilisateurRequest("ADMIN", "motdepasse1", true, Set.of(unRoleId()))))
            .isInstanceOf(ConflitRessourceException.class);
    }

    @Test
    void roleInconnuEstRefuse() {
        assertThatThrownBy(() -> utilisateurService.creer(
            new CreateUtilisateurRequest("autre", "motdepasse1", true, Set.of(-1L))))
            .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    void ladminNePeutPasSupprimerNiDesactiverSonPropreCompte() {
        UtilisateurResponse admin = utilisateurService.lister().stream()
            .filter(u -> u.username().equals(ADMIN)).findFirst().orElseThrow();
        Set<Long> roleIds = Set.of(admin.roles().iterator().next().id());

        assertThatThrownBy(() -> utilisateurService.supprimer(admin.id(), ADMIN))
            .isInstanceOf(ConflitRessourceException.class);
        assertThatThrownBy(() -> utilisateurService.modifier(admin.id(),
            new UpdateUtilisateurRequest(ADMIN, null, false, roleIds), ADMIN))
            .isInstanceOf(ConflitRessourceException.class);
    }

    // --- Session de caisse (création de journée) ---

    @Test
    void ouvertureEtClotureCalculentLeSoldeEtLEcart() {
        LocalDate journee = LocalDate.now();
        SessionCaisseResponse ouverte = sessionService.ouvrir(
            new OuvertureSessionRequest(journee, new BigDecimal("5000")), ADMIN);
        assertThat(ouverte.statut()).isEqualTo(StatutSessionCaisse.OUVERTE);
        assertThat(ouverte.fondDeCaisse()).isEqualByComparingTo("5000");
        assertThat(sessionService.sessionOuverte(journee).id()).isEqualTo(ouverte.id());

        SessionCaisseResponse fermee = sessionService.cloturer(
            ouverte.id(), new ClotureSessionRequest(new BigDecimal("4900")), ADMIN);
        assertThat(fermee.statut()).isEqualTo(StatutSessionCaisse.FERMEE);
        assertThat(fermee.soldeTheorique()).isEqualByComparingTo("5000");
        assertThat(fermee.ecartCloture()).isEqualByComparingTo("-100");
        assertThat(fermee.fermeePar()).isEqualTo(ADMIN);
    }

    @Test
    void uneSeuleJourneeParDateEtPasDeDoubleCloture() {
        LocalDate journee = LocalDate.now();
        SessionCaisseResponse session = sessionService.ouvrir(
            new OuvertureSessionRequest(journee, BigDecimal.ZERO), ADMIN);

        assertThatThrownBy(() -> sessionService.ouvrir(
            new OuvertureSessionRequest(journee, BigDecimal.ZERO), ADMIN))
            .isInstanceOf(ConflitRessourceException.class);

        sessionService.cloturer(session.id(), new ClotureSessionRequest(BigDecimal.ZERO), ADMIN);
        assertThatThrownBy(() -> sessionService.cloturer(
            session.id(), new ClotureSessionRequest(BigDecimal.ZERO), ADMIN))
            .isInstanceOf(ConflitRessourceException.class);
    }

    // --- Unités de mesure ---

    @Test
    void uniteDeMesureCrudEtUnicite() {
        UniteMesureResponse cree = uniteService.creer(new UniteMesureRequest("TST", "Unité de test"));
        assertThatThrownBy(() -> uniteService.creer(new UniteMesureRequest("tst", "Doublon")))
            .isInstanceOf(ConflitRessourceException.class);

        UniteMesureResponse modifiee = uniteService.modifier(cree.id(), new UniteMesureRequest("TST", "Renommée"));
        assertThat(modifiee.libelle()).isEqualTo("Renommée");

        uniteService.supprimer(cree.id());
        assertThatThrownBy(() -> uniteService.obtenir(cree.id()))
            .isInstanceOf(RessourceIntrouvableException.class);
    }
}
