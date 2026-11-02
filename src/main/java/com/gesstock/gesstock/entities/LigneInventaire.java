package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Un article dans un inventaire. {@code stockTheorique} et {@code coutUnitaire} (CMP) sont figés à l'ouverture : les ventes
 * continuent pendant le comptage, c'est donc l'ÉCART (compté - théorique) qui est appliqué à la validation, pas le compté.
 */
@Entity
@Table(name = "lignes_inventaire")
@Getter
@Setter
@NoArgsConstructor
public class LigneInventaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "inventaire_id", nullable = false)
    private Inventaire inventaire;

    @ManyToOne(optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal stockTheorique;

    /** Null tant que l'article n'a pas été compté : il n'est alors pas touché à la validation. */
    @Column(precision = 15, scale = 3)
    private BigDecimal stockCompte;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal coutUnitaire = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private MotifAjustementStock motif;

    @Column(length = 500)
    private String commentaire;

    /** Écart réellement appliqué au stock à la validation ; null avant. */
    @Column(precision = 15, scale = 3)
    private BigDecimal ecartApplique;

    public BigDecimal ecart() {
        return stockCompte == null ? null : stockCompte.subtract(stockTheorique);
    }
}
