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

    /**
     * La longueur n'est pas une limite : elle fait choisir à Hibernate le type LONGBLOB de MySQL (au-delà de 16 777 215), celui
     * de la migration V13. Sans elle il attend un TINYBLOB et le démarrage échoue à la validation du schéma — ce que la base
     * de test H2 ne montre pas. Le poids réel est borné par {@code app.entreprise.logo-octets-max}.
     */
    @NotNull
    @Lob
    @Column(nullable = false, length = 67_108_864)
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
    // nom explicite : le découpage automatique de « misAJourLe » ne donne pas « mis_a_jour_le » (colonne de la migration V13)
    @Column(name = "mis_a_jour_le", nullable = false)
    private LocalDateTime misAJourLe = LocalDateTime.now();
}
