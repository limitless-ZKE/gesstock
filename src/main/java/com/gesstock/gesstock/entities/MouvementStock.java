package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "mouvements_stock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MouvementStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TypeMouvementStock type;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer quantite;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer stockAvant;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer stockApres;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dateMouvement = LocalDateTime.now();

    @Size(max = 40)
    @Column(length = 40)
    private String referenceDocument;

    @Size(max = 500)
    private String commentaire;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @ManyToOne
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur effectuePar;

    @ManyToOne
    @JoinColumn(name = "session_caisse_id")
    private SessionCaisse sessionCaisse;

    @ManyToOne
    @JoinColumn(name = "approvisionnement_id")
    private Approvisionnement approvisionnement;

    @ManyToOne
    @JoinColumn(name = "vente_id")
    private Vente vente;
}
