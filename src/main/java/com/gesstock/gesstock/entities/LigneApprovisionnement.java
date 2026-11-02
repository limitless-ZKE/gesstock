package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ligne_approvisionnements")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LigneApprovisionnement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 15, scale = 3)
    private java.math.BigDecimal quantite;

    @PositiveOrZero
    @Column(nullable = false, precision = 19, scale = 4)
    private java.math.BigDecimal prixUnitaire = java.math.BigDecimal.ZERO;

    /**
     * Unités déjà rendues au fournisseur. Jamais modifié par l'entité : seul l'UPDATE conditionnel du dépôt le fait, ce qui
     * rend « jamais plus rendu que reçu » vrai même avec deux validations simultanées (la base le garantit aussi par un CHECK).
     */
    @Column(nullable = false, updatable = false, precision = 15, scale = 3)
    private java.math.BigDecimal quantiteRetournee = java.math.BigDecimal.ZERO;

    @ManyToOne
    @JoinColumn(name = "approvisionnement_id", nullable = false)
    @NotNull
    private Approvisionnement approvisionnement;

    @ManyToOne
    @JoinColumn(name = "article_id", nullable = false)
    @NotNull
    private Article article;
}
