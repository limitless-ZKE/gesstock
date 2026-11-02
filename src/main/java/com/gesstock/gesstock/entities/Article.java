package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "article")
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 150)
    @Column(length = 150)
    private String nom;

    @NotBlank
    @Size(max = 60)
    @Column(length = 60, unique = true)
    private String reference;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal prixAchat = BigDecimal.ZERO;

    /**
     * Coût moyen pondéré (CMP) : la valeur du stock divisée par sa quantité. Il sert aux marges et à la valeur du stock ;
     * {@code prixAchat} reste le DERNIER prix d'achat saisi, à titre informatif. Il ne change que par une entrée en stock.
     */
    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal cmp = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal prix = BigDecimal.ZERO;

    /** Stock en unité de stock, à 3 décimales au plus (V15 : une caisse entamée, un liquide au litre…). */
    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantite = BigDecimal.ZERO;

    @Min(0)
    private int seuilAlerte = 0;

    @ManyToOne
    @JoinColumn(name = "categorie_id")
    private CategorieArticle categorie;

    @ManyToOne
    @JoinColumn(name = "unite_mesure_id")
    private UniteMesure uniteMesure;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UniteVente> unitesVente = new ArrayList<>();

    @ManyToMany(mappedBy = "articles")
    private List<Promotion> promotions = new ArrayList<>();

    public Article() {}

    public Article(String nom, BigDecimal prix, BigDecimal quantite) {
        this.nom = nom;
        this.prix = prix;
        this.quantite = quantite;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public BigDecimal getPrix() { return prix; }
    public void setPrix(BigDecimal prix) { this.prix = prix; }

    public BigDecimal getQuantite() { return quantite; }
    public void setQuantite(BigDecimal quantite) { this.quantite = quantite; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public BigDecimal getPrixAchat() { return prixAchat; }
    public void setPrixAchat(BigDecimal prixAchat) { this.prixAchat = prixAchat; }

    public BigDecimal getCmp() { return cmp; }
    public void setCmp(BigDecimal cmp) { this.cmp = cmp; }

    public int getSeuilAlerte() { return seuilAlerte; }
    public void setSeuilAlerte(int seuilAlerte) { this.seuilAlerte = seuilAlerte; }

    public CategorieArticle getCategorie() { return categorie; }
    public void setCategorie(CategorieArticle categorie) { this.categorie = categorie; }

    public UniteMesure getUniteMesure() { return uniteMesure; }
    public void setUniteMesure(UniteMesure uniteMesure) { this.uniteMesure = uniteMesure; }

    public List<UniteVente> getUnitesVente() { return unitesVente; }
    public void setUnitesVente(List<UniteVente> unitesVente) { this.unitesVente = unitesVente; }

    public List<Promotion> getPromotions() { return promotions; }
    public void setPromotions(List<Promotion> promotions) { this.promotions = promotions; }

    // --- Méthodes de compatibilité (Alias) pour PdfService et anciens composants ---
    public String getDesignation() {
        return nom;
    }

    public void setDesignation(String designation) {
        this.nom = designation;
    }

    public BigDecimal getQuantiteStock() {
        return quantite;
    }

    public void setQuantiteStock(BigDecimal quantiteStock) {
        this.quantite = quantiteStock;
    }
}