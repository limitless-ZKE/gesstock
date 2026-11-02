package com.gesstock.gesstock.performance;

import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.VenteDirecteRequest;
import com.gesstock.gesstock.ventes.dto.VenteResponse;
import com.gesstock.gesstock.ventes.service.GestionVentesService;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde-fou contre le N+1 : le nombre de requêtes SQL d'une liste ne doit PAS croître avec le nombre de ventes.
 * (Avant correction : 32 requêtes pour 15 ventes d'une ligne, soit 2 de plus par vente.)
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class RequetesSqlTests {

    private static final String ADMIN = "admin";

    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private EntityManager entityManager;

    private long requetesPourListerLesVentes(int nombreDeVentes) {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        Article riz = articleRepository.findByReferenceIgnoreCase("ALIM-001").orElseThrow();
        for (int i = 0; i < nombreDeVentes; i++) {
            VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(null, false,
                List.of(new LigneDocumentRequest(riz.getId(), null, BigDecimal.valueOf(1), null, null),
                        new LigneDocumentRequest(articleRepository.findByReferenceIgnoreCase("ALIM-002").orElseThrow().getId(), null, BigDecimal.valueOf(1), null, null)),
                BigDecimal.ZERO, ModeReglement.ESPECES, new BigDecimal("10000")), ADMIN);
            ventesService.validerVente(brouillon.id(), ADMIN);
        }
        entityManager.flush();
        entityManager.clear();
        SessionFactory fabrique = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        fabrique.getStatistics().clear();
        List<VenteResponse> ventes = ventesService.listerVentes(LocalDate.now());
        assertThat(ventes).hasSize(nombreDeVentes);
        return fabrique.getStatistics().getPrepareStatementCount();
    }

    @Test
    void lHistoriqueDUneJourneeNEmetQueQuelquesRequetesQuelleQueSoitLaTaille() {
        long requetes = requetesPourListerLesVentes(25);

        System.out.println("MESURE|25 ventes de 2 lignes -> " + requetes + " requêtes SQL");
        assertThat(requetes).as("requêtes SQL pour 25 ventes de 2 lignes").isLessThanOrEqualTo(3);
    }
}
