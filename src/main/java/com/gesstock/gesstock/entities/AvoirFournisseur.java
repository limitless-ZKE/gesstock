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

/**
 * Crédit que nous accorde un fournisseur (retour de marchandise, ristourne…). Il s'impute automatiquement sur les dettes les
 * plus anciennes ; ce qui n'est pas consommé reste disponible pour les prochains bons.
 */
@Entity
@Table(name = "avoirs_fournisseurs")
@Getter
@Setter
@NoArgsConstructor
public class AvoirFournisseur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "fournisseur_id", nullable = false)
    private Fournisseur fournisseur;

    @NotNull
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal montant;

    @NotNull
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal montantUtilise = BigDecimal.ZERO;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    @NotBlank
    @Size(max = 300)
    @Column(nullable = false, length = 300)
    private String motif;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "cree_par_id", nullable = false)
    private Utilisateur creePar;

    @Version
    @Column(nullable = false)
    private Long version;

    public BigDecimal restant() {
        return montant.subtract(montantUtilise);
    }
}
