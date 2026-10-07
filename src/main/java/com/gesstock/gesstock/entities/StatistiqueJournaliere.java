package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "statistiques_journalieres")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StatistiqueJournaliere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false, unique = true)
    private LocalDate journee;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer nombreVentes = 0;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal chiffreAffaires = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.0")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal montantReglements = BigDecimal.ZERO;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime derniereMiseAJour = LocalDateTime.now();
}
