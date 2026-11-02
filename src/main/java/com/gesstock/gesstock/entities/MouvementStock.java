package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mouvements_stock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MouvementStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TypeMouvementStock type;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantite;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal stockAvant;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal stockApres;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dateMouvement = LocalDateTime.now();

    @Size(max = 40)
    @Column(length = 40)
    private String referenceDocument;

    @Size(max = 500)
    private String commentaire;

    /** Coût unitaire (par unité de stock) auquel ce mouvement valorise le stock : prix d'achat à l'entrée, CMP à la sortie. */
    @DecimalMin("0.0")
    @Column(precision = 19, scale = 4)
    private java.math.BigDecimal coutUnitaire;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @ManyToOne
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur effectuePar;

    @ManyToOne
    @JoinColumn(name = "session_caisse_id")
    private SessionCaisse sessionCaisse;

    @ManyToOne
    @JoinColumn(name = "approvisionnement_id")
    private Approvisionnement approvisionnement;

    @ManyToOne
    @JoinColumn(name = "vente_id")
    private Vente vente;

    /** Inventaire à l'origine de l'ajustement, le cas échéant. */
    @ManyToOne
    @JoinColumn(name = "inventaire_id")
    private Inventaire inventaire;

    /** Retour au fournisseur à l'origine de la sortie, le cas échéant. */
    @ManyToOne
    @JoinColumn(name = "retour_fournisseur_id")
    private RetourFournisseur retourFournisseur;
}
