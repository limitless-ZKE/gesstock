package com.gesstock.gesstock.caisse;

import com.gesstock.gesstock.caisse.dto.EcritureCaissier;
import com.gesstock.gesstock.caisse.dto.JournalCaissierJour;
import com.gesstock.gesstock.caisse.dto.JournalCaissierResponse;
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
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le journal d'un caissier ne contient que ce que CE caissier a fait : ses ventes, les règlements de dettes qu'il a encaissés,
 * ses sorties. Le fond de caisse, le solde théorique et l'écart de clôture sont ceux du tiroir de la journée (partagé par tous) :
 * ils n'y figurent pas, à leur place « espèces à remettre ». Données du DataSeeder : comptes « admin » et « caissier »,
 * ELEC-001 (téléphone) à 245 000 et ALIM-001 (riz) à 1 300.
 */
@SpringBootTest
@Transactional
class JournalCaissierServiceTests {

    private static final String ADMIN = "admin";
    private static final String CAISSIER = "caissier";

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

    private void ouvrirLaCaisse() {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
    }

    private VenteResponse vendre(String acteur, String reference, int quantite, ModeReglement mode, Long clientId, boolean aCredit) {
        var article = articleRepository.findByReferenceIgnoreCase(reference).orElseThrow();
        BigDecimal recu = mode == ModeReglement.ESPECES ? new BigDecimal("1000000") : article.getPrix().multiply(BigDecimal.valueOf(quantite));
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(clientId, aCredit,
            List.of(new LigneDocumentRequest(article.getId(), null, BigDecimal.valueOf(quantite), null, null)), BigDecimal.ZERO,
            aCredit ? null : mode, aCredit ? null : recu), acteur);
        return ventesService.validerVente(brouillon.id(), acteur);
    }

    private JournalCaissierResponse journalDe(String caissier) {
        return journalService.journalDuCaissier(caissier, LocalDate.now(), LocalDate.now());
    }

    @Test
    void seulesLesLignesDuCaissierFigurentDansSonJournalEtIlNeVoitNiLeFondNiLeSoldeDuTiroir() {
        ouvrirLaCaisse();
        vendre(CAISSIER, "ELEC-001", 1, ModeReglement.ESPECES, null, false);          // + 245 000 (caissier)
        vendre(ADMIN, "ALIM-001", 2, ModeReglement.ESPECES, null, false);             // + 2 600 (admin : pas dans le journal du caissier)
        mouvementService.enregistrer(new MouvementCaisseRequest("Transport de marchandises", "Moussa", new BigDecimal("5000")), CAISSIER);

        JournalCaissierResponse journal = journalDe(CAISSIER);

        assertThat(journal.caissier()).isEqualTo(CAISSIER);
        assertThat(journal.jours()).hasSize(1);
        JournalCaissierJour jour = journal.jours().get(0);
        assertThat(jour.ecritures()).extracting(EcritureCaissier::type)
            .containsExactly(TypeEcritureCaisse.VENTE, TypeEcritureCaisse.SORTIE);
        assertThat(jour.ecritures()).extracting(EcritureCaissier::type).doesNotContain(TypeEcritureCaisse.OUVERTURE);
        assertThat(jour.ventesEspeces()).isEqualByComparingTo("245000");
        assertThat(jour.reglementsEspeces()).isZero();
        assertThat(jour.sorties()).isEqualByComparingTo("5000");
        assertThat(jour.especesARemettre()).as("ses ventes + ses règlements − ses sorties").isEqualByComparingTo("240000");
        assertThat(jour.nombreVentes()).isEqualTo(1);
        assertThat(jour.ecritures().get(0).cumul()).as("cumul de SES lignes, sans le fond de caisse").isEqualByComparingTo("245000");
        assertThat(jour.ecritures().get(1).cumul()).isEqualByComparingTo("240000");
        assertThat(jour.ecritures().get(1).libelle()).contains("Transport de marchandises").contains("Moussa");
        assertThat(journal.totaux().especesARemettre()).isEqualByComparingTo("240000");
        assertThat(journal.totaux().nombreJours()).isEqualTo(1);
    }

    @Test
    void chaqueCaissierAUnJournalDistinctEtLeursTotauxRetombentSurLeTiroir() {
        ouvrirLaCaisse();
        vendre(CAISSIER, "ELEC-001", 1, ModeReglement.ESPECES, null, false);
        vendre(ADMIN, "ALIM-001", 2, ModeReglement.ESPECES, null, false);

        JournalCaissierResponse duCaissier = journalDe(CAISSIER);
        JournalCaissierResponse deLAdmin = journalDe(ADMIN);

        assertThat(deLAdmin.jours()).hasSize(1);
        assertThat(deLAdmin.jours().get(0).ventesEspeces()).isEqualByComparingTo("2600");
        assertThat(deLAdmin.jours().get(0).nombreVentes()).isEqualTo(1);
        assertThat(duCaissier.totaux().ventesEspeces().add(deLAdmin.totaux().ventesEspeces()))
            .as("la somme des journaux individuels = les espèces de vente du journal complet")
            .isEqualByComparingTo(journalService.journal(LocalDate.now(), LocalDate.now()).totaux().encaissementsEspeces());
    }

    @Test
    void lesPaiementsHorsEspecesLesVentesACreditEtLesReglementsDuCaissierSontResumes() {
        ouvrirLaCaisse();
        ClientResponse client = clientService.creer(new ClientRequest("Boutique Aminata", null, "0100000000", "Dakar"));
        vendre(CAISSIER, "ELEC-001", 1, ModeReglement.CARTE, null, false);            // 245 000 par carte
        vendre(CAISSIER, "ALIM-001", 10, null, client.id(), true);                    // 13 000 à crédit
        vendre(ADMIN, "ELEC-001", 1, ModeReglement.MOBILE_MONEY, null, false);        // à l'admin : absent
        DetteResponse dette = detteService.listerDettesClient(client.id()).get(0);
        detteService.enregistrerReglement(dette.id(), new ReglementRequest(new BigDecimal("4000"), ModeReglement.ESPECES, "REC-1"), CAISSIER);
        detteService.enregistrerReglement(dette.id(), new ReglementRequest(new BigDecimal("1000"), ModeReglement.ESPECES, "REC-2"), ADMIN);

        JournalCaissierJour jour = journalDe(CAISSIER).jours().get(0);

        assertThat(jour.reglementsEspeces()).as("seul le règlement qu'il a encaissé").isEqualByComparingTo("4000");
        assertThat(jour.ventesEspeces()).isZero();
        assertThat(jour.especesARemettre()).isEqualByComparingTo("4000");
        assertThat(jour.ventesACredit()).isEqualByComparingTo("13000");
        assertThat(jour.autresEncaissements()).extracting(e -> e.mode().name(), e -> e.montant().intValue())
            .containsExactly(org.assertj.core.groups.Tuple.tuple("CARTE", 245000));
        assertThat(jour.ecritures()).extracting(EcritureCaissier::type).containsExactly(TypeEcritureCaisse.REGLEMENT);
        assertThat(jour.ecritures().get(0).reference()).isEqualTo("REC-1");
        assertThat(jour.nombreVentes()).as("sa vente par carte et sa vente à crédit").isEqualTo(2);
        assertThat(journalDe(CAISSIER).totaux().ventesACredit()).isEqualByComparingTo("13000");
        assertThat(journalDe(CAISSIER).totaux().autresEncaissements()).isEqualByComparingTo("245000");
    }

    @Test
    void uneVenteAnnuleeDuCaissierEstSignaleeMaisNeCompteNiDansSesVentesNiDansSesEspeces() {
        ouvrirLaCaisse();
        VenteResponse vente = vendre(CAISSIER, "ELEC-001", 1, ModeReglement.ESPECES, null, false);
        ventesService.annulerVente(vente.id(), "Client mécontent", true, ADMIN);

        JournalCaissierJour jour = journalDe(CAISSIER).jours().get(0);

        assertThat(jour.nombreVentesAnnulees()).isEqualTo(1);
        assertThat(jour.nombreVentes()).isZero();
        assertThat(jour.especesARemettre()).isZero();
        assertThat(jour.ecritures()).isEmpty();
    }

    @Test
    void uneJourneeOuLeCaissierNAPasTravailleNEstPasListee() {
        ouvrirLaCaisse();
        vendre(ADMIN, "ALIM-001", 2, ModeReglement.ESPECES, null, false);

        JournalCaissierResponse journal = journalDe(CAISSIER);

        assertThat(journal.jours()).isEmpty();
        assertThat(journal.totaux().nombreJours()).isZero();
        assertThat(journal.totaux().especesARemettre()).isZero();
    }

    @Test
    void unUtilisateurInconnuOuUnePeriodeIncoherenteSontRefuses() {
        LocalDate aujourdhui = LocalDate.now();
        assertThatThrownBy(() -> journalService.journalDuCaissier("personne", aujourdhui, aujourdhui))
            .isInstanceOf(RessourceIntrouvableException.class).hasMessageContaining("personne");
        assertThatThrownBy(() -> journalService.journalDuCaissier(CAISSIER, aujourdhui, aujourdhui.minusDays(1)))
            .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("date de fin");
        assertThatThrownBy(() -> journalService.journalDuCaissier(CAISSIER, aujourdhui.minusDays(366), aujourdhui))
            .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("366");
    }

    @Test
    void laListeDesCaissiersDonneTousLesComptes() {
        assertThat(journalService.caissiersDuJournal()).extracting(c -> c.username()).contains(ADMIN, CAISSIER).isSorted();
    }
}
