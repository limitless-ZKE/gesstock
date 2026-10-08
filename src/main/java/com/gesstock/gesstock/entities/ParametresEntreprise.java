package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Informations de l'entreprise imprimées sur les documents (tickets, reçus, rapports, feuilles d'inventaire). Une seule
 * ligne existe : {@link #ID_UNIQUE}. Le fichier du logo est dans {@link LogoEntreprise} ; ici n'en restent que l'empreinte
 * et les dimensions, pour lire ces informations sans charger l'image.
 */
@Entity
@Table(name = "parametres_entreprise")
@Getter
@Setter
@NoArgsConstructor
public class ParametresEntreprise {

    public static final int ID_UNIQUE = 1;

    @Id
    private Integer id = ID_UNIQUE;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String nom;

    @Size(max = 300)
    @Column(length = 300)
    private String adresse;

    @Size(max = 40)
    @Column(length = 40)
    private String telephone;

    @Email
    @Size(max = 180)
    @Column(length = 180)
    private String email;

    /** RCCM, NINEA, SIRET… selon le pays. */
    @Size(max = 80)
    @Column(length = 80)
    private String identifiantLegal;

    @Size(max = 180)
    @Column(length = 180)
    private String siteWeb;

    /** Message en bas des tickets (« Merci de votre visite ! »). */
    @Size(max = 200)
    @Column(length = 200)
    private String piedDePage;

    @Column(nullable = false)
    private boolean logoSurTickets = true;

    /** Empreinte SHA-256 du logo stocké ; null s'il n'y a pas de logo. Change à chaque remplacement. */
    @Size(max = 64)
    @Column(length = 64)
    private String logoEmpreinte;

    private Integer logoLargeur;

    private Integer logoHauteur;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime misAJourLe = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "mis_a_jour_par_id")
    private Utilisateur misAJourPar;

    @Version
    @Column(nullable = false)
    private Long version;

    public boolean aUnLogo() {
        return logoEmpreinte != null;
    }
}
