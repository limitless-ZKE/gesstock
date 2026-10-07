package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Entity
@Table(name = "lignes_vente")
public class LigneVente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Min(1)
    private int quantite;

    @PositiveOrZero
    @Column(precision = 19, scale = 4)
    private java.math.BigDecimal prixUnitaire = java.math.BigDecimal.ZERO;

    @ManyToOne
    @JoinColumn(name = "article_id", nullable = false)
    @NotNull
    private Article article; // Pointe vers la table 'article' ou 'articles'

    @ManyToOne
    @JoinColumn(name = "vente_id", nullable = false)
    @NotNull
    private Vente vente;

    @ManyToOne
    @JoinColumn(name = "unite_vente_id")
    private UniteVente uniteVente;

    @ManyToOne
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @Column(precision = 19, scale = 4)
    private java.math.BigDecimal remiseUnitaire = java.math.BigDecimal.ZERO;

    /** Prix catalogue au moment de la vente : sert à afficher la remise négociée sur le reçu. Null sur les anciennes lignes. */
    @Column(precision = 19, scale = 4)
    private java.math.BigDecimal prixCatalogue;

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getQuantite() { return quantite; }
    public void setQuantite(int quantite) { this.quantite = quantite; }
    public java.math.BigDecimal getPrixUnitaire() { return prixUnitaire; }
    public void setPrixUnitaire(java.math.BigDecimal prixUnitaire) { this.prixUnitaire = prixUnitaire; }
    public Article getArticle() { return article; }
    public void setArticle(Article article) { this.article = article; }
    public Vente getVente() { return vente; }
    public void setVente(Vente vente) { this.vente = vente; }
    public UniteVente getUniteVente() { return uniteVente; }
    public void setUniteVente(UniteVente uniteVente) { this.uniteVente = uniteVente; }
    public Promotion getPromotion() { return promotion; }
    public void setPromotion(Promotion promotion) { this.promotion = promotion; }
    public java.math.BigDecimal getPrixCatalogue() { return prixCatalogue; }
    public void setPrixCatalogue(java.math.BigDecimal prixCatalogue) { this.prixCatalogue = prixCatalogue; }
    public java.math.BigDecimal getRemiseUnitaire() { return remiseUnitaire; }
    public void setRemiseUnitaire(java.math.BigDecimal remiseUnitaire) { this.remiseUnitaire = remiseUnitaire; }
}