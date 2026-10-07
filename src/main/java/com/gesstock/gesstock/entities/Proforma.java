package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.Valid;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "proformas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Proforma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String numero;

    @NotNull
    @Column(nullable = false)
    private LocalDate dateEmission = LocalDate.now();

    private LocalDate dateExpiration;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal montantTotal = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutProforma statut = StatutProforma.BROUILLON;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur creePar;

    @OneToMany(mappedBy = "proforma", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<@Valid LigneProforma> lignes = new ArrayList<>();

    @OneToOne(mappedBy = "proforma")
    private Vente vente;

    @AssertTrue(message = "La date d'expiration ne peut pas précéder la date d'émission")
    public boolean isDateExpirationValide() {
        return dateExpiration == null || dateEmission == null || !dateExpiration.isBefore(dateEmission);
    }
}
