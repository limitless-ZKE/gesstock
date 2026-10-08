package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Le fichier du logo, tel que stocké après contrôle : toujours un PNG ré-encodé par le serveur (donc sans métadonnées ni
 * contenu actif) et borné en dimensions. Les formats réduits (ticket, en-tête…) sont dérivés à la demande, jamais stockés.
 */
@Entity
@Table(name = "logo_entreprise")
@Getter
@Setter
@NoArgsConstructor
public class LogoEntreprise {

    @Id
    private Integer id = ParametresEntreprise.ID_UNIQUE;

    @NotNull
    @Lob
    @Column(nullable = false)
    private byte[] contenu;

    @NotNull
    @Column(nullable = false, length = 40)
    private String typeMime = "image/png";

    @Column(nullable = false)
    private int largeur;

    @Column(nullable = false)
    private int hauteur;

    @NotNull
    @Column(nullable = false, length = 64)
    private String empreinte;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime misAJourLe = LocalDateTime.now();
}
