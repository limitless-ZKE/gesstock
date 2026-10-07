package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "journal_activites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JournalActivite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String action;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String entite;

    private Long entiteId;

    @Size(max = 2000)
    @Column(length = 2000)
    private String details;

    @Size(max = 64)
    @Column(length = 64)
    private String adresseIp;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dateActivite = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;
}
