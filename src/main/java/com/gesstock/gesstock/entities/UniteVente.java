package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "unites_vente", uniqueConstraints = {
    @UniqueConstraint(name = "uk_unite_vente_article_code", columnNames = {"article_id", "code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UniteVente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "unite_mesure_id", nullable = false)
    private UniteMesure uniteMesure;

    @Size(max = 30)
    @Column(length = 30)
    private String code;

    @NotNull
    @DecimalMin(value = "0.000001")
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal facteurConversion = BigDecimal.ONE;

    @DecimalMin("0.0")
    @Column(precision = 19, scale = 4)
    private BigDecimal prixVente;

    @Column(nullable = false)
    private boolean active = true;
}
