package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un règlement d'une dette fournisseur. Source PAIEMENT : un mode de règlement (et, en espèces, la sortie de caisse
 * correspondante). Source AVOIR : l'avoir imputé, sans mode ni mouvement de caisse.
 */
@Entity
@Table(name = "reglements_fournisseurs")
@Getter
@Setter
@NoArgsConstructor
public class ReglementFournisseur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dateReglement = LocalDateTime.now();

    @NotNull
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal montant;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceReglementFournisseur source;

    @Enumerated(EnumType.STRING)
    private ModeReglement modeReglement;

    @Size(max = 80)
    @Column(length = 80)
    private String reference;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "dette_id", nullable = false)
    private DetteFournisseur dette;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fournisseur_id", nullable = false)
    private Fournisseur fournisseur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avoir_id")
    private AvoirFournisseur avoir;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mouvement_caisse_id")
    private MouvementCaisse mouvementCaisse;

    @ManyToOne
    @JoinColumn(name = "effectue_par_id")
    private Utilisateur effectuePar;
}
