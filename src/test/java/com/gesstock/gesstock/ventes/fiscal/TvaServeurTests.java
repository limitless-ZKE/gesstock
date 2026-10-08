package com.gesstock.gesstock.ventes.fiscal;

import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.VenteDirecteRequest;
import com.gesstock.gesstock.ventes.dto.VenteResponse;
import com.gesstock.gesstock.ventes.service.GestionVentesService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le taux de TVA est décidé par le serveur. Ces tests l'activent (18 %) pour vérifier les deux régimes ;
 * la configuration par défaut (aucune TVA) est couverte par GestionVentesServiceTests.
 */
class TvaServeurTests {

    private static final String ADMIN = "admin";

    abstract static class Base {
        @Autowired
        GestionVentesService ventesService;
        @Autowired
        ArticleRepository articleRepository;

        VenteResponse creer(BigDecimal tauxEnvoye) {
            Article telephone = articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
            return ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false,
                List.of(new LigneDocumentRequest(telephone.getId(), null, 2, null, null)),
                tauxEnvoye, ModeReglement.ESPECES, new BigDecimal("10000000")), ADMIN);
        }
    }

    @Nested
    @SpringBootTest(properties = {"app.tva.taux=18", "app.tva.prix-ttc=false"})
    @Transactional
    class PrixHorsTaxe extends Base {

        @Test
        void laTvaSAjouteAuPrixEtLeTauxEnvoyeParLaCaisseEstOptionnel() {
            VenteResponse vente = creer(new BigDecimal("18"));

            assertThat(vente.tauxTva()).isEqualByComparingTo("18");
            assertThat(vente.montantHT()).isEqualByComparingTo("490000");
            assertThat(vente.montantTva()).isEqualByComparingTo("88200");
            assertThat(vente.montantTotal()).isEqualByComparingTo("578200");
            assertThat(creer(null).montantTotal()).as("sans taux envoyé, le serveur applique le sien").isEqualByComparingTo("578200");
        }

        @Test
        void uneCaisseQuiEnvoieUnAutreTauxEstRefusee() {
            assertThatThrownBy(() -> creer(BigDecimal.ZERO))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("fixé par le serveur (18 %)");
            assertThatThrownBy(() -> creer(new BigDecimal("5")))
                .isInstanceOf(RequeteInvalideException.class);
        }
    }

    @Nested
    @SpringBootTest(properties = {"app.tva.taux=18", "app.tva.prix-ttc=true"})
    @Transactional
    class PrixToutesTaxesComprises extends Base {

        @Test
        void laTvaEstExtraiteEtLeClientPayeLePrixAffiche() {
            VenteResponse vente = creer(new BigDecimal("18"));

            assertThat(vente.montantTotal()).isEqualByComparingTo("490000");
            assertThat(vente.montantHT()).isEqualByComparingTo("415254.2373");
            assertThat(vente.montantTva()).isEqualByComparingTo("74745.7627");
        }
    }
}
