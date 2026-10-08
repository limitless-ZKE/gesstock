package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Une ligne par ligne de réception : ce que le prix d'achat et le coût moyen pondéré valaient avant et après. Elle n'est
 * jamais modifiée ni supprimée, de sorte qu'on peut toujours expliquer d'où vient le coût d'un article.
 */
@Entity
@Table(name = "historique_prix_achat")
@Getter
@Setter
@NoArgsConstructor
public class HistoriquePrixAchat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(nullable = false)
    private LocalDateTime dateEffet;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal ancienPrix;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal nouveauPrix;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal ancienCmp;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal nouveauCmp;

    @Column(nullable = false)
    private int stockAvant;

    @Column(nullable = false)
    private int quantiteRecue;

    @ManyToOne
    @JoinColumn(name = "approvisionnement_id")
    private Approvisionnement approvisionnement;

    @ManyToOne
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;
}
