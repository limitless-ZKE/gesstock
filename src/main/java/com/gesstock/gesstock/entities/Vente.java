package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vente")
public class Vente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max = 50)
    @Column(length = 50, unique = true)
    private String code;

    // Champs conservés pour la compatibilité avec votre structure existante si besoin
    @PositiveOrZero
    private int quantite;

    @PositiveOrZero
    @Column(name = "prix_unit", precision = 19, scale = 4)
    private BigDecimal prixUnit = BigDecimal.ZERO;

    @Column(name = "montant_total", precision = 19, scale = 4)
    @PositiveOrZero
    private BigDecimal montantTotal = BigDecimal.ZERO;

    @Column(name = "prix_total", precision = 19, scale = 4)
    private BigDecimal prixTotalField = BigDecimal.ZERO;

    @Column(name = "montant_ht", precision = 19, scale = 4)
    private BigDecimal montantHT = BigDecimal.ZERO;

    @Column(name = "montant_tva", precision = 19, scale = 4)
    private BigDecimal montantTva = BigDecimal.ZERO;

    @Column(name = "taux_tva", precision = 7, scale = 4)
    private BigDecimal tauxTva = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_reglement", length = 20)
    private ModeReglement modeReglement;

    @Column(name = "montant_recu", precision = 19, scale = 4)
    private BigDecimal montantRecu;

    private LocalDateTime dateVente;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StatutVente statut = StatutVente.VALIDEE;

    @ManyToOne
    @JoinColumn(name = "article_id")
    private Article article; // Utilisé pour l'ancienne version, conservé pour éviter d'autres erreurs

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    // Nouvelle relation OneToMany vers les lignes de vente (Panier)
    @OneToMany(mappedBy = "vente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<@Valid LigneVente> lignesVente = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "session_caisse_id")
    private SessionCaisse sessionCaisse;

    @ManyToOne
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur vendeur;

    @ManyToOne
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @OneToOne
    @JoinColumn(name = "proforma_id", unique = true)
    private Proforma proforma;

    @OneToOne(mappedBy = "vente")
    private DetteClient detteClient;

    @Column(nullable = false)
    private boolean venteACredit;

    @Version
    private Long version;

    @PrePersist
    public void prePersist() {
        if (this.code == null || this.code.trim().isEmpty()) {
            this.code = "VTE-" + System.currentTimeMillis();
        }
        if (this.dateVente == null) {
            this.dateVente = LocalDateTime.now();
        }
    }

    public BigDecimal getPrixTotal() {
        return this.montantTotal;
    }

    public void setPrixTotal(BigDecimal montantTotal) {
        this.montantTotal = montantTotal;
        this.prixTotalField = montantTotal;
    }

    // --- GETTERS ET SETTERS ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public int getQuantite() { return quantite; }
    public void setQuantite(int quantite) { this.quantite = quantite; }

    public BigDecimal getPrixUnit() { return prixUnit; }
    public void setPrixUnit(BigDecimal prixUnit) { this.prixUnit = prixUnit; }

    public BigDecimal getMontantTotal() { return montantTotal; }
    public void setMontantTotal(BigDecimal montantTotal) { 
        this.montantTotal = montantTotal; 
        this.prixTotalField = montantTotal; 
    }

    public BigDecimal getMontantHT() { return montantHT; }
    public void setMontantHT(BigDecimal montantHT) { this.montantHT = montantHT; }

    public BigDecimal getMontantTva() { return montantTva; }
    public void setMontantTva(BigDecimal montantTva) { this.montantTva = montantTva; }

    public BigDecimal getTauxTva() { return tauxTva; }
    public void setTauxTva(BigDecimal tauxTva) { this.tauxTva = tauxTva; }

    public ModeReglement getModeReglement() { return modeReglement; }
    public void setModeReglement(ModeReglement modeReglement) { this.modeReglement = modeReglement; }

    public BigDecimal getMontantRecu() { return montantRecu; }
    public void setMontantRecu(BigDecimal montantRecu) { this.montantRecu = montantRecu; }

    public LocalDateTime getDateVente() { return dateVente; }
    public void setDateVente(LocalDateTime dateVente) { this.dateVente = dateVente; }

    public StatutVente getStatut() { return statut; }
    public void setStatut(StatutVente statut) { this.statut = statut; }

    public Article getArticle() { return article; }
    public void setArticle(Article article) { this.article = article; }

    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }

    // Getters et Setters pour la liste des lignes de vente (Panier)
    public List<LigneVente> getLignesVente() { 
        return lignesVente; 
    }
    
    public void setLignesVente(List<LigneVente> lignesVente) { 
        this.lignesVente = lignesVente; 
    }

    public SessionCaisse getSessionCaisse() { return sessionCaisse; }
    public void setSessionCaisse(SessionCaisse sessionCaisse) { this.sessionCaisse = sessionCaisse; }

    public Utilisateur getVendeur() { return vendeur; }
    public void setVendeur(Utilisateur vendeur) { this.vendeur = vendeur; }

    public Promotion getPromotion() { return promotion; }
    public void setPromotion(Promotion promotion) { this.promotion = promotion; }

    public Proforma getProforma() { return proforma; }
    public void setProforma(Proforma proforma) { this.proforma = proforma; }

    public DetteClient getDetteClient() { return detteClient; }
    public void setDetteClient(DetteClient detteClient) { this.detteClient = detteClient; }

    public boolean isVenteACredit() { return venteACredit; }
    public void setVenteACredit(boolean venteACredit) { this.venteACredit = venteACredit; }
}