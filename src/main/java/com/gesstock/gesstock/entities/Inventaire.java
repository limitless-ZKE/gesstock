package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Inventaire physique : une photographie du stock théorique, les quantités réellement comptées, puis la validation d'un
 * responsable qui applique les écarts. Un seul inventaire peut être en cours à la fois (garanti par la base).
 */
@Entity
@Table(name = "inventaires")
@Getter
@Setter
@NoArgsConstructor
public class Inventaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutInventaire statut = StatutInventaire.EN_SAISIE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PerimetreInventaire perimetre = PerimetreInventaire.TOUT;

    @ManyToOne
    @JoinColumn(name = "categorie_id")
    private CategorieArticle categorie;

    @Column(length = 500)
    private String commentaire;

    @Column(nullable = false)
    private LocalDateTime ouvertLe;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ouvert_par_id", nullable = false)
    private Utilisateur ouvertPar;

    private LocalDateTime soumisLe;

    private LocalDateTime valideLe;

    @ManyToOne
    @JoinColumn(name = "valide_par_id")
    private Utilisateur validePar;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "inventaire", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<LigneInventaire> lignes = new ArrayList<>();
}
