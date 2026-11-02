package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import com.gesstock.gesstock.stock.Quantites;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Une ligne du bon rendue en tout ou partie. Le coût est celui du bon : c'est ce que le fournisseur doit nous créditer. */
@Entity
@Table(name = "lignes_retour_fournisseur")
@Getter
@Setter
@NoArgsConstructor
public class LigneRetourFournisseur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "retour_id", nullable = false)
    private RetourFournisseur retour;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "ligne_approvisionnement_id", nullable = false)
    private LigneApprovisionnement ligneApprovisionnement;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantite;

    @NotNull
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal coutUnitaire;

    public BigDecimal montant() {
        return Quantites.montant(coutUnitaire, quantite);
    }
}
