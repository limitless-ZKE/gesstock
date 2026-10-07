package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "unites_mesure")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UniteMesure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String libelle;

    @OneToMany(mappedBy = "uniteMesure")
    private List<Article> articles = new ArrayList<>();

    @OneToMany(mappedBy = "uniteMesure")
    private List<UniteVente> unitesVente = new ArrayList<>();
}
