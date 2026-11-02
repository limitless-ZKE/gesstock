package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ce que nous devons à un fournisseur pour un bon d'approvisionnement reçu (une dette par bon). */
@Entity
@Table(name = "dettes_fournisseurs")
@Getter
@Setter
@NoArgsConstructor
public class DetteFournisseur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private LocalDate dateCreation = LocalDate.now();

    @NotNull
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal montantInitial;

    @NotNull
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal montantRegle = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutDette statut = StatutDette.EN_COURS;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "fournisseur_id", nullable = false)
    private Fournisseur fournisseur;

    @NotNull
    @OneToOne(optional = false)
    @JoinColumn(name = "approvisionnement_id", nullable = false, unique = true)
    private Approvisionnement approvisionnement;

    @Version
    @Column(nullable = false)
    private Long version;

    public BigDecimal solde() {
        return montantInitial.subtract(montantRegle);
    }

    /** Nouveau montant après correction du bon ; jamais sous ce qui est déjà réglé (l'appelant le vérifie). */
    public void corrigerMontant(BigDecimal nouveauMontant) {
        montantInitial = nouveauMontant;
        statut = montantRegle.signum() == 0 ? StatutDette.EN_COURS
            : montantRegle.compareTo(montantInitial) == 0 ? StatutDette.REGLEE : StatutDette.PARTIELLEMENT_REGLEE;
    }

    /** Impute un règlement : la dette passe à « partiellement réglée », puis à « réglée » quand le solde est nul. */
    public void imputer(BigDecimal montant) {
        montantRegle = montantRegle.add(montant);
        statut = montantRegle.compareTo(montantInitial) == 0 ? StatutDette.REGLEE : StatutDette.PARTIELLEMENT_REGLEE;
    }
}
