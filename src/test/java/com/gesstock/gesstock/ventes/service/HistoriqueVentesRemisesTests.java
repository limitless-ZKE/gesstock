package com.gesstock.gesstock.ventes.service;

import com.gesstock.gesstock.entities.LigneVente;
import com.gesstock.gesstock.entities.Vente;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Les remises de l'historique sont celles du ticket : négociée avec le client (catalogue − prix appliqué) + promotion, par quantité. */
class HistoriqueVentesRemisesTests {

    private static LigneVente ligne(int quantite, String prixUnitaire, String prixCatalogue, String remisePromotion) {
        LigneVente ligne = new LigneVente();
        ligne.setQuantite(quantite);
        ligne.setPrixUnitaire(new BigDecimal(prixUnitaire));
        ligne.setPrixCatalogue(prixCatalogue == null ? null : new BigDecimal(prixCatalogue));
        ligne.setRemiseUnitaire(remisePromotion == null ? null : new BigDecimal(remisePromotion));
        return ligne;
    }

    private static Vente vente(LigneVente... lignes) {
        Vente vente = new Vente();
        for (LigneVente ligne : lignes) vente.getLignesVente().add(ligne);
        return vente;
    }

    @Test
    void laRemiseNegocieeEtLaPromotionSAdditionnentParUnite() {
        // catalogue 1 000, vendu 900 (100 négociés) et 50 de promotion, 3 unités : (100 + 50) × 3
        assertThat(HistoriqueVentesService.remisesDe(vente(ligne(3, "900", "1000", "50")))).isEqualByComparingTo("450");
    }

    @Test
    void uneVenteSansRemiseNEnAPas() {
        assertThat(HistoriqueVentesService.remisesDe(vente(ligne(2, "1300", "1300", "0")))).isZero();
        assertThat(HistoriqueVentesService.remisesDe(vente(ligne(2, "1300", null, null))))
            .as("anciennes lignes : sans prix catalogue ni promotion").isZero();
    }

    @Test
    void unPrixAppliqueSuperieurAuCatalogueNeDonneJamaisUneRemiseNegative() {
        assertThat(HistoriqueVentesService.remisesDe(vente(ligne(1, "1200", "1000", "0")))).isZero();
    }

    @Test
    void lesRemisesDeToutesLesLignesSAdditionnent() {
        Vente vente = vente(ligne(1, "900", "1000", "0"), ligne(2, "500", "500", "25"));
        assertThat(HistoriqueVentesService.remisesDe(vente)).isEqualByComparingTo("150");   // 100 + 2 × 25
    }
}
