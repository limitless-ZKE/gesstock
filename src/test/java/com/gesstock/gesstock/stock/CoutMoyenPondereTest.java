package com.gesstock.gesstock.stock;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoutMoyenPondereTest {

    private static BigDecimal bd(String valeur) {
        return new BigDecimal(valeur);
    }

    @Test
    void uneEntreeAUnAutrePrixPondereLeCout() {
        // 12 téléphones à 189 000 + 10 reçus à 230 000 : (12×189 000 + 10×230 000) / 22
        assertThat(CoutMoyenPondere.apresEntree(12, bd("189000"), 10, bd("230000"))).isEqualByComparingTo("207636.3636");
    }

    @Test
    void uneEntreeAuMemePrixNeChangeRien() {
        assertThat(CoutMoyenPondere.apresEntree(40, bd("1234.5678"), 25, bd("1234.5678"))).isEqualByComparingTo("1234.5678");
    }

    @Test
    void stockVideLePrixDeLEntreeDevientLeCmp() {
        assertThat(CoutMoyenPondere.apresEntree(0, bd("999"), 5, bd("100"))).isEqualByComparingTo("100");
        assertThat(CoutMoyenPondere.apresEntree(-3, bd("999"), 5, bd("100"))).isEqualByComparingTo("100");
    }

    @Test
    void arrondiALaQuatriemeDecimaleDemiSuperieur() {
        assertThat(CoutMoyenPondere.apresEntree(3, bd("100"), 1, bd("101"))).isEqualByComparingTo("100.25");
        assertThat(CoutMoyenPondere.apresEntree(1, bd("100"), 2, bd("100.0001"))).isEqualByComparingTo("100.0001"); // 300.0002 / 3 = 100.00006667
    }

    @Test
    void entreesInvalidesSontRefusees() {
        assertThatThrownBy(() -> CoutMoyenPondere.apresEntree(5, bd("10"), 0, bd("10"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CoutMoyenPondere.apresEntree(5, bd("10"), 1, bd("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lAnciennePratiqueDuDernierPrixFaussaitLaMargeDUneVenteDejaFaite() {
        BigDecimal prixNet = bd("245000");
        BigDecimal coutMemorise = bd("189000"); // CMP au moment de la vente, copié sur la ligne de vente

        BigDecimal margeAvantReception = CoutMoyenPondere.marge(prixNet, coutMemorise, 1);
        // un nouveau lot arrive à 230 000 : le CMP bouge, MAIS la vente passée garde son coût mémorisé
        BigDecimal nouveauCmp = CoutMoyenPondere.apresEntree(11, coutMemorise, 10, bd("230000"));
        BigDecimal margeApresReception = CoutMoyenPondere.marge(prixNet, coutMemorise, 1);

        assertThat(nouveauCmp).isGreaterThan(coutMemorise);
        assertThat(margeApresReception).isEqualByComparingTo(margeAvantReception).isEqualByComparingTo("56000");
        // avec « dernier prix d'achat » (état actuel) la même vente affichait 245 000 - 230 000 = 15 000
    }

    @Test
    void retourFournisseurRetireLaValeurAuCoutDOrigine() {
        // 22 articles à 207 636,3636 ; on rend 10 reçus à 230 000 -> il reste 12 articles à 189 000
        BigDecimal cmp = CoutMoyenPondere.apresEntree(12, bd("189000"), 10, bd("230000"));
        // le CMP est stocké à 4 décimales : retirer le lot reçu redonne 189 000 à 0,001 près (188 999,9999 exactement)
        assertThat(CoutMoyenPondere.apresRetourFournisseur(22, cmp, 10, bd("230000")))
            .isBetween(bd("188999.999"), bd("189000.001"));
    }

    @Test
    void retourFournisseurTotalOuIncoherentConserveLeCmp() {
        assertThat(CoutMoyenPondere.apresRetourFournisseur(5, bd("100"), 5, bd("100"))).isEqualByComparingTo("100");
        // coût d'origine absurde (supérieur à toute la valeur du stock) : on ne produit pas un CMP négatif
        assertThat(CoutMoyenPondere.apresRetourFournisseur(5, bd("10"), 3, bd("1000"))).isEqualByComparingTo("10");
        assertThatThrownBy(() -> CoutMoyenPondere.apresRetourFournisseur(5, bd("10"), 6, bd("10"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laValeurDuStockResteCoherenteApresBeaucoupDEntrees() {
        Random hasard = new Random(42);
        int stock = 0;
        BigDecimal cmp = BigDecimal.ZERO;
        BigDecimal valeurReelle = BigDecimal.ZERO;
        for (int i = 0; i < 500; i++) {
            int quantite = 1 + hasard.nextInt(50);
            BigDecimal cout = bd(String.valueOf(1000 + hasard.nextInt(9000))).add(bd("0." + (1000 + hasard.nextInt(8999))));
            valeurReelle = valeurReelle.add(cout.multiply(BigDecimal.valueOf(quantite)));
            cmp = CoutMoyenPondere.apresEntree(stock, cmp, quantite, cout);
            stock += quantite;
        }
        BigDecimal ecart = CoutMoyenPondere.valeurDuStock(stock, cmp).subtract(valeurReelle).abs();
        // L'arrondi à 4 décimales du CMP dérive de quelques francs sur des dizaines de millions : erreur relative < 1 millionième.
        BigDecimal erreurRelative = ecart.divide(valeurReelle, 12, java.math.RoundingMode.HALF_UP);
        assertThat(erreurRelative).as("erreur relative sur %d unités (écart %s)", stock, ecart).isLessThan(bd("0.000001"));
    }
}
