package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "approvisionnements")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Approvisionnement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 60)
    @Column(unique = true, length = 60)
    private String numero;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateApprovisionnement = LocalDate.now();

    // Relation Many-to-One avec Fournisseur
    @ManyToOne
    @JoinColumn(name = "fournisseur_id")
    private Fournisseur fournisseur;

    // Relation One-to-Many avec les lignes d'approvisionnement
    @OneToMany(mappedBy = "approvisionnement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<@Valid LigneApprovisionnement> lignes;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StatutApprovisionnement statut = StatutApprovisionnement.RECU;

    /** Date et heure de la réception (null tant que le bon est un brouillon) : c'est ce jour-là que l'achat compte. */
    private LocalDateTime recuLe;

    @ManyToOne
    @JoinColumn(name = "session_caisse_id")
    private SessionCaisse sessionCaisse;

    @ManyToOne
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur creePar;

    @Version
    private Long version;
}