package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "lignes_proforma")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LigneProforma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer quantite;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal prixUnitaire;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal montantLigne;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "proforma_id", nullable = false)
    private Proforma proforma;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @ManyToOne
    @JoinColumn(name = "unite_vente_id")
    private UniteVente uniteVente;

    @ManyToOne
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal remiseUnitaire = BigDecimal.ZERO;

    /** Prix catalogue à l'émission : sert à afficher la remise négociée. Null sur les anciennes lignes. */
    @Column(precision = 19, scale = 4)
    private BigDecimal prixCatalogue;
}
