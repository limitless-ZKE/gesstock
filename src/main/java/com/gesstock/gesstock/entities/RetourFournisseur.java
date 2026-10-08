package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Marchandise rendue au fournisseur à partir d'un bon reçu. Brouillon tant qu'un responsable ne l'a pas validé ; la validation
 * sort le stock, ajuste le coût moyen et crée l'avoir du fournisseur.
 */
@Entity
@Table(name = "retours_fournisseurs")
@Getter
@Setter
@NoArgsConstructor
public class RetourFournisseur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 40)
    @Column(nullable = false, unique = true, length = 40)
    private String numero;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutRetourFournisseur statut = StatutRetourFournisseur.BROUILLON;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "fournisseur_id", nullable = false)
    private Fournisseur fournisseur;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "approvisionnement_id", nullable = false)
    private Approvisionnement approvisionnement;

    @NotBlank
    @Size(max = 300)
    @Column(nullable = false, length = 300)
    private String motif;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "cree_par_id", nullable = false)
    private Utilisateur creePar;

    private LocalDateTime dateValidation;

    @ManyToOne
    @JoinColumn(name = "valide_par_id")
    private Utilisateur validePar;

    /** Somme des lignes (quantité × coût d'origine) : ce que le fournisseur nous crédite à la validation. */
    @NotNull
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal montantTotal = BigDecimal.ZERO;

    @OneToOne
    @JoinColumn(name = "avoir_id", unique = true)
    private AvoirFournisseur avoir;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "retour", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<LigneRetourFournisseur> lignes = new ArrayList<>();
}
