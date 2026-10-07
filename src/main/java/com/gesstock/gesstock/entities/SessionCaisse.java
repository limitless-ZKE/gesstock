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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sessions_caisse")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionCaisse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max = 50)
    @Column(unique = true, length = 50)
    private String code;

    @NotNull
    @Column(nullable = false, unique = true)
    private LocalDate journee = LocalDate.now();

    @NotNull
    @Column(nullable = false)
    private LocalDateTime ouverteLe = LocalDateTime.now();

    private LocalDateTime fermeeLe;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fondDeCaisse = BigDecimal.ZERO;

    @DecimalMin("0.0")
    @Column(precision = 19, scale = 4)
    private BigDecimal montantCloture;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutSessionCaisse statut = StatutSessionCaisse.OUVERTE;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "ouverte_par_id", nullable = false)
    private Utilisateur ouvertePar;

    @ManyToOne
    @JoinColumn(name = "fermee_par_id")
    private Utilisateur fermeePar;

    @OneToMany(mappedBy = "sessionCaisse")
    private List<Vente> ventes = new ArrayList<>();

    @OneToMany(mappedBy = "sessionCaisse")
    private List<Approvisionnement> approvisionnements = new ArrayList<>();
}
