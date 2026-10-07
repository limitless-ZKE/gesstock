package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dettes_clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DetteClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private LocalDate dateCreation = LocalDate.now();

    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal montantInitial;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal montantRegle = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private StatutDette statut = StatutDette.EN_COURS;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @OneToOne
    @JoinColumn(name = "vente_id", unique = true)
    private Vente vente;

    @OneToMany(mappedBy = "dette", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReglementClient> reglements = new ArrayList<>();

    @Version
    private Long version;

    @AssertTrue(message = "Le montant réglé ne peut pas dépasser le montant initial")
    public boolean isMontantRegleValide() {
        return montantInitial == null || montantRegle == null || montantRegle.compareTo(montantInitial) <= 0;
    }
}
