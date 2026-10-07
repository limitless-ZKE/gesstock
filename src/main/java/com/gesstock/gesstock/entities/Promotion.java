package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String nom;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypePromotion type;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal valeur;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime debut;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime fin;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToMany
    @JoinTable(
        name = "promotion_articles",
        joinColumns = @JoinColumn(name = "promotion_id"),
        inverseJoinColumns = @JoinColumn(name = "article_id")
    )
    private List<Article> articles = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "promotion_categories",
        joinColumns = @JoinColumn(name = "promotion_id"),
        inverseJoinColumns = @JoinColumn(name = "categorie_id")
    )
    private List<CategorieArticle> categories = new ArrayList<>();

    @AssertTrue(message = "La fin de promotion doit être après son début, et le pourcentage ne doit pas dépasser 100")
    public boolean isPeriodeEtValeurValides() {
        if (debut == null || fin == null || fin.isBefore(debut) || valeur == null || type == null) {
            return false;
        }
        return type != TypePromotion.POURCENTAGE || valeur.compareTo(BigDecimal.valueOf(100)) <= 0;
    }
}
