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
import java.time.LocalDateTime;

@Entity
@Table(name = "reglements_clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReglementClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dateReglement = LocalDateTime.now();

    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal montant;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModeReglement modeReglement;

    @Size(max = 80)
    @Column(length = 80)
    private String reference;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "dette_id", nullable = false)
    private DetteClient dette;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur recuPar;

    @ManyToOne
    @JoinColumn(name = "session_caisse_id")
    private SessionCaisse sessionCaisse;
}
