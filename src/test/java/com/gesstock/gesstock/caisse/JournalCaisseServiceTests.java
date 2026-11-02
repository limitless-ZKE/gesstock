package com.gesstock.gesstock.caisse;

import com.gesstock.gesstock.caisse.dto.EcritureCaisse;
import com.gesstock.gesstock.caisse.dto.JournalCaisseResponse;
import com.gesstock.gesstock.caisse.dto.JournalSessionCaisse;
import com.gesstock.gesstock.caisse.dto.MouvementCaisseRequest;
import com.gesstock.gesstock.caisse.dto.TypeEcritureCaisse;
import com.gesstock.gesstock.caisse.service.JournalCaisseService;
import com.gesstock.gesstock.caisse.service.MouvementCaisseService;
import com.gesstock.gesstock.clients.dto.ClientRequest;
import com.gesstock.gesstock.clients.dto.ClientResponse;
import com.gesstock.gesstock.clients.dto.DetteResponse;
import com.gesstock.gesstock.clients.dto.ReglementRequest;
import com.gesstock.gesstock.clients.service.ClientCatalogueService;
import com.gesstock.gesstock.clients.service.GestionDetteClientService;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.SessionCaisse;
import com.gesstock.gesstock.entities.StatutSessionCaisse;
import com.gesstock.gesstock.referentiels.dto.ClotureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.SessionCaisseResponse;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.SessionCaisseRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.VenteDirecteRequest;
import com.gesstock.gesstock.ventes.dto.VenteResponse;
import com.gesstock.gesstock.ventes.service.GestionVentesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le journal de caisse suit les règles du solde théorique de la clôture : son dernier solde est toujours celui que la caisse
 * affiche. Données de démonstration du DataSeeder : ELEC-001 (téléphone) à 245 000 et ALIM-001 (riz) à 1 300.
 */
@SpringBootTest
@Transactional
class JournalCaisseServiceTests {

    private static final String ADMIN = "admin";

    @Autowired
    private JournalCaisseService journalService;
    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private MouvementCaisseService mouvementService;
    @Autowired
    private ClientCatalogueService clientService;
    @Autowired
    private GestionDetteClientService detteService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SessionCaisseRepository sessionRepository;
    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private SessionCaisseResponse ouvrir(String fond) {
        return sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal(fond)), ADMIN);
    }

    private VenteResponse vendre(String reference, int quantite, ModeReglement mode, Long clientId, boolean aCredit) {
        var article = articleRepository.findByReferenceIgnoreCase(reference).orElseThrow();
        // en espèces on remet un gros billet ; un autre moyen de paiement doit correspondre exactement au total
        BigDecimal recu = mode == ModeReglement.ESPECES ? new BigDecimal("1000000") : article.getPrix().multiply(BigDecimal.valueOf(quantite));
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(clientId, aCredit,
            List.of(new LigneDocumentRequest(article.getId(), null, BigDecimal.valueOf(quantite), null, null)), BigDecimal.ZERO,
            aCredit ? null : mode, aCredit ? null : recu), ADMIN);
        return ventesService.validerVente(brouillon.id(), ADMIN);
    }

    private JournalSessionCaisse aujourdhui() {
        JournalCaisseResponse journal = journalService.journal(LocalDate.now(), LocalDate.now());
        assertThat(journal.sessions()).hasSize(1);
        return journal.sessions().get(0);
    }

    @Test
    void lesEcrituresSeCumulentEtLeDernierSoldeEstLeSoldeTheoriqueDeLaCaisse() {
        SessionCaisseResponse session = ouvrir("1000");
        vendre("ELEC-001", 1, ModeReglement.ESPECES, null, false);       // + 245 000
        mouvementService.enregistrer(new MouvementCaisseRequest("Transport de marchandises", "Moussa", new BigDecimal("5000")), ADMIN);
        vendre("ALIM-001", 2, ModeReglement.ESPECES, null, false);       // + 2 600

        JournalSessionCaisse journee = aujourdhui();

        assertThat(journee.ecritures()).extracting(EcritureCaisse::type)
            .containsExactlyInAnyOrder(TypeEcritureCaisse.OUVERTURE, TypeEcritureCaisse.VENTE, TypeEcritureCaisse.VENTE, TypeEcritureCaisse.SORTIE);
        assertThat(journee.ecritures().get(0).type()).as("l'ouverture ouvre le journal").isEqualTo(TypeEcritureCaisse.OUVERTURE);
        assertThat(journee.ecritures().get(0).entree()).isEqualByComparingTo("1000");
        assertThat(journee.ecritures()).extracting(EcritureCaisse::date).isSorted();
        BigDecimal courant = BigDecimal.ZERO;
        for (EcritureCaisse ecriture : journee.ecritures()) {
            courant = ecriture.entree() != null ? courant.add(ecriture.entree()) : courant.subtract(ecriture.sortie());
            assertThat(ecriture.solde()).as("solde après « %s »", ecriture.libelle()).isEqualByComparingTo(courant);
        }
        assertThat(journee.soldeTheorique()).isEqualByComparingTo("243600");
        assertThat(journee.encaissementsEspeces()).isEqualByComparingTo("247600");
        assertThat(journee.sorties()).isEqualByComparingTo("5000");
        assertThat(journee.nombreVentes()).isEqualTo(2);
        assertThat(journee.soldeTheorique()).as("le même solde que celui de la clôture")
            .isEqualByComparingTo(sessionService.obtenir(session.id()).soldeTheorique());
        EcritureCaisse sortie = journee.ecritures().stream().filter(e -> e.type() == TypeEcritureCaisse.SORTIE).findFirst().orElseThrow();
        assertThat(sortie.libelle()).contains("Transport de marchandises").contains("Moussa");
        assertThat(sortie.utilisateur()).isEqualTo(ADMIN);
    }

    @Test
    void laClotureDonneLesEspecesCompteesEtLEcart() {
        SessionCaisseResponse session = ouvrir("1000");
        vendre("ELEC-001", 1, ModeReglement.ESPECES, null, false);
        assertThat(aujourdhui().montantCloture()).as("tant que la session est ouverte, rien n'est compté").isNull();
        assertThat(aujourdhui().ecart()).isNull();

        sessionService.cloturer(session.id(), new ClotureSessionRequest(new BigDecimal("245400")), ADMIN);   // 600 de moins que prévu

        JournalSessionCaisse journee = aujourdhui();
        assertThat(journee.statut()).isEqualTo(StatutSessionCaisse.FERMEE);
        assertThat(journee.montantCloture()).isEqualByComparingTo("245400");
        assertThat(journee.ecart()).as("compté − théorique").isEqualByComparingTo("-600");
        assertThat(journee.fermeePar()).isEqualTo(ADMIN);
        assertThat(journalService.journal(LocalDate.now(), LocalDate.now()).totaux().ecarts()).isEqualByComparingTo("-600");
    }

    @Test
    void lesPaiementsHorsEspecesEtLesVentesACreditNeTouchentPasLeTiroir() {
        ouvrir("1000");
        ClientResponse client = clientService.creer(new ClientRequest("Boutique Aminata", null, "0100000000", "Dakar"));
        vendre("ELEC-001", 1, ModeReglement.CARTE, null, false);                // 245 000 par carte
        vendre("ALIM-001", 10, null, client.id(), true);                        // 13 000 à crédit
        DetteResponse dette = detteService.listerDettesClient(client.id()).get(0);
        detteService.enregistrerReglement(dette.id(), new ReglementRequest(new BigDecimal("4000"), ModeReglement.ESPECES, "REC-1"), ADMIN);
        detteService.enregistrerReglement(dette.id(), new ReglementRequest(new BigDecimal("3000"), ModeReglement.MOBILE_MONEY, "OM-9"), ADMIN);

        JournalSessionCaisse journee = aujourdhui();

        assertThat(journee.soldeTheorique()).as("fond + règlement en espèces seulement").isEqualByComparingTo("5000");
        assertThat(journee.reglementsClientsEspeces()).isEqualByComparingTo("4000");
        assertThat(journee.encaissementsEspeces()).isZero();
        assertThat(journee.ventesACredit()).isEqualByComparingTo("13000");
        // un règlement de dette hors espèces n'est jamais rattaché à la session de caisse (voir GestionDetteClientService) :
        // le journal n'en parle pas, seul le paiement par carte d'une vente y est résumé à part
        assertThat(journee.autresEncaissements()).extracting(e -> e.mode().name(), e -> e.montant().intValue())
            .containsExactly(org.assertj.core.groups.Tuple.tuple("CARTE", 245000));
        assertThat(journee.ecritures()).extracting(EcritureCaisse::type)
            .containsExactlyInAnyOrder(TypeEcritureCaisse.OUVERTURE, TypeEcritureCaisse.REGLEMENT);
        EcritureCaisse reglement = journee.ecritures().stream().filter(e -> e.type() == TypeEcritureCaisse.REGLEMENT).findFirst().orElseThrow();
        assertThat(reglement.libelle()).contains("Boutique Aminata");
        assertThat(reglement.reference()).isEqualTo("REC-1");
        assertThat(journee.nombreVentes()).as("les trois modes comptent comme des ventes").isEqualTo(2);
    }

    @Test
    void uneVenteAnnuleeEstSignaleeMaisNeCompteNiDansLesVentesNiDansLesEspeces() {
        ouvrir("1000");
        VenteResponse vente = vendre("ELEC-001", 1, ModeReglement.ESPECES, null, false);
        ventesService.annulerVente(vente.id(), "Client mécontent", true, ADMIN);

        JournalSessionCaisse journee = aujourdhui();

        assertThat(journee.nombreVentesAnnulees()).isEqualTo(1);
        assertThat(journee.nombreVentes()).isZero();
        assertThat(journee.soldeTheorique()).isEqualByComparingTo("1000");
        assertThat(journee.ecritures()).extracting(EcritureCaisse::type).containsExactly(TypeEcritureCaisse.OUVERTURE);
    }

    @Test
    void lesJourneesSontRangeesDansLOrdreDesDatesEtLesTotauxAdditionnentTout() {
        ouvrir("1000");
        vendre("ELEC-001", 1, ModeReglement.ESPECES, null, false);
        SessionCaisse hier = new SessionCaisse();
        hier.setCode("CAISSE-HIER-TEST");
        hier.setJournee(LocalDate.now().minusDays(1));
        hier.setOuverteLe(LocalDateTime.now().minusDays(1).withHour(8));
        hier.setFermeeLe(LocalDateTime.now().minusDays(1).withHour(19));
        hier.setFondDeCaisse(new BigDecimal("2000"));
        hier.setMontantCloture(new BigDecimal("2000"));
        hier.setStatut(StatutSessionCaisse.FERMEE);
        hier.setOuvertePar(utilisateurRepository.findByUsername(ADMIN).orElseThrow());
        hier.setFermeePar(utilisateurRepository.findByUsername(ADMIN).orElseThrow());
        sessionRepository.save(hier);

        JournalCaisseResponse journal = journalService.journal(LocalDate.now().minusDays(1), LocalDate.now());

        assertThat(journal.sessions()).extracting(JournalSessionCaisse::journee).containsExactly(LocalDate.now().minusDays(1), LocalDate.now());
        assertThat(journal.totaux().nombreSessions()).isEqualTo(2);
        assertThat(journal.totaux().sessionsNonCloturees()).isEqualTo(1);
        assertThat(journal.totaux().fondsDeCaisse()).isEqualByComparingTo("3000");
        assertThat(journal.totaux().soldeTheorique()).isEqualByComparingTo("248000");   // 2 000 + (1 000 + 245 000)
        assertThat(journal.totaux().especesComptees()).as("seule la session clôturée est comptée").isEqualByComparingTo("2000");
        assertThat(journal.totaux().ecarts()).isZero();
    }

    @Test
    void uneJourneeSansSessionDonneUnJournalVide() {
        JournalCaisseResponse journal = journalService.journal(LocalDate.now().minusDays(30), LocalDate.now().minusDays(29));
        assertThat(journal.sessions()).isEmpty();
        assertThat(journal.totaux().nombreSessions()).isZero();
        assertThat(journal.totaux().soldeTheorique()).isZero();
    }

    @Test
    void uneVenteDUneAutreJourneeNEntrePasDansCelleCi() {
        // la session d'aujourd'hui ne contient que ses propres ventes : on vérifie avec une période qui ne la couvre pas
        ouvrir("1000");
        vendre("ELEC-001", 1, ModeReglement.ESPECES, null, false);
        assertThat(journalService.journal(LocalDate.now().minusDays(5), LocalDate.now().minusDays(1)).sessions()).isEmpty();
    }

    @Test
    void laPeriodeDoitEtreCoherenteEtNePasDepasserUnAn() {
        LocalDate aujourdhui = LocalDate.now();
        assertThatThrownBy(() -> journalService.journal(aujourdhui, aujourdhui.minusDays(1)))
            .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("date de fin");
        assertThatThrownBy(() -> journalService.journal(null, aujourdhui))
            .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("début");
        assertThatThrownBy(() -> journalService.journal(aujourdhui.minusDays(366), aujourdhui))
            .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("366");
        assertThat(journalService.journal(aujourdhui.minusDays(365), aujourdhui).debut()).isEqualTo(aujourdhui.minusDays(365));
    }
}
