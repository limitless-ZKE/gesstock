package com.gesstock.gesstock.articles;

import com.gesstock.gesstock.stock.Quantites;

import java.math.BigDecimal;

import com.gesstock.gesstock.articles.dto.AjustementStockRequest;
import com.gesstock.gesstock.articles.dto.ArticleResponse;
import com.gesstock.gesstock.entities.MotifAjustementStock;
import com.gesstock.gesstock.articles.service.AjustementStockService;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.MouvementStock;
import com.gesstock.gesstock.entities.TypeMouvementStock;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.JournalActiviteRepository;
import com.gesstock.gesstock.repositories.MouvementStockRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Un ajustement de stock laisse toujours sa trace : mouvement (avant, après, motif, auteur) et journal. */
@SpringBootTest
@Transactional
class AjustementStockTests {

    private static final String ADMIN = "admin";

    @Autowired
    private AjustementStockService ajustementService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private MouvementStockRepository mouvementRepository;
    @Autowired
    private JournalActiviteRepository journalRepository;
    @Autowired
    private EntityManager entityManager;

    private Article telephone() {
        return articleRepository.findByReferenceIgnoreCase("ELEC-001").orElseThrow();
    }

    private List<MouvementStock> mouvementsDuJour(Article article) {
        return mouvementRepository.findByArticleIdAndDateMouvementBetweenOrderByDateMouvementDesc(
            article.getId(), LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay());
    }

    @Test
    void unAjustementALaBaisseLaisseUnMouvementNegatifEtUneLigneDeJournal() {
        Article telephone = telephone();
        BigDecimal stock = telephone.getQuantiteStock();
        long lignesDeJournal = journalRepository.count();

        ArticleResponse reponse = ajustementService.ajuster(telephone.getId(),
            new AjustementStockRequest(stock, stock.subtract(BigDecimal.valueOf(2)), MotifAjustementStock.CASSE, "Écran fissuré"), ADMIN);

        assertThat(reponse.quantiteStock()).isEqualByComparingTo(stock.subtract(BigDecimal.valueOf(2)));
        List<MouvementStock> mouvements = mouvementsDuJour(telephone);
        assertThat(mouvements).hasSize(1);
        MouvementStock mouvement = mouvements.get(0);
        assertThat(mouvement.getType()).isEqualTo(TypeMouvementStock.AJUSTEMENT_NEGATIF);
        assertThat(mouvement.getQuantite()).isEqualByComparingTo("2");
        assertThat(mouvement.getStockAvant()).isEqualByComparingTo(stock);
        assertThat(mouvement.getStockApres()).isEqualByComparingTo(stock.subtract(BigDecimal.valueOf(2)));
        assertThat(mouvement.getCommentaire()).contains("Casse").contains("Écran fissuré");
        assertThat(mouvement.getEffectuePar().getUsername()).isEqualTo(ADMIN);
        assertThat(journalRepository.count()).isEqualTo(lignesDeJournal + 1);
    }

    @Test
    void unAjustementAlaHausseLaisseUnMouvementPositif() {
        Article telephone = telephone();
        BigDecimal stock = telephone.getQuantiteStock();

        ajustementService.ajuster(telephone.getId(),
            new AjustementStockRequest(stock, stock.add(BigDecimal.valueOf(3)), MotifAjustementStock.ECART_DE_COMPTAGE, null), ADMIN);

        MouvementStock mouvement = mouvementsDuJour(telephone).get(0);
        assertThat(mouvement.getType()).isEqualTo(TypeMouvementStock.AJUSTEMENT_POSITIF);
        assertThat(mouvement.getQuantite()).isEqualByComparingTo("3");
        assertThat(mouvement.getCommentaire()).isEqualTo("Écart de comptage");
    }

    @Test
    void siLeStockABougeDepuisLAffichageOnRefuseSansRienChanger() {
        Article telephone = telephone();
        BigDecimal stock = telephone.getQuantiteStock();

        // la personne voyait un autre stock que celui enregistré (une vente est passée entre-temps)
        assertThatThrownBy(() -> ajustementService.ajuster(telephone.getId(),
            new AjustementStockRequest(stock.add(BigDecimal.valueOf(8)), BigDecimal.ZERO, MotifAjustementStock.VOL, null), ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("Le stock a changé depuis l'affichage (" + Quantites.texte(stock) + " actuellement)");

        entityManager.flush();
        entityManager.clear();
        assertThat(articleRepository.findById(telephone.getId()).orElseThrow().getQuantiteStock()).isEqualByComparingTo(stock);
        assertThat(mouvementsDuJour(telephone)).isEmpty();
    }

    @Test
    void unAjustementSansChangementEstRefuse() {
        Article telephone = telephone();
        BigDecimal stock = telephone.getQuantiteStock();

        assertThatThrownBy(() -> ajustementService.ajuster(telephone.getId(),
            new AjustementStockRequest(stock, stock, MotifAjustementStock.ERREUR_DE_SAISIE, null), ADMIN))
            .isInstanceOf(ConflitRessourceException.class)
            .hasMessageContaining("déjà de " + Quantites.texte(stock));
    }

    @Test
    void leMotifAutreExigeUnCommentaire() {
        Article telephone = telephone();
        BigDecimal stock = telephone.getQuantiteStock();

        for (String commentaire : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> ajustementService.ajuster(telephone.getId(),
                new AjustementStockRequest(stock, stock.subtract(BigDecimal.ONE), MotifAjustementStock.AUTRE, commentaire), ADMIN))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("commentaire");
        }
        ajustementService.ajuster(telephone.getId(),
            new AjustementStockRequest(stock, stock.subtract(BigDecimal.ONE), MotifAjustementStock.AUTRE, "Échantillon offert"), ADMIN);
        assertThat(telephone.getQuantiteStock()).isEqualByComparingTo(stock.subtract(BigDecimal.valueOf(1)));
    }

    @Test
    void unArticleInconnuEstRefuse() {
        assertThatThrownBy(() -> ajustementService.ajuster(987654321L,
            new AjustementStockRequest(BigDecimal.ONE, BigDecimal.ZERO, MotifAjustementStock.CASSE, null), ADMIN))
            .isInstanceOf(RessourceIntrouvableException.class);
    }
}
