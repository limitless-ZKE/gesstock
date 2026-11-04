package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mouvements_caisse")
public class MouvementCaisse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeMouvementCaisse type = TypeMouvementCaisse.SORTIE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrigineMouvementCaisse origine = OrigineMouvementCaisse.MANUEL;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String motif;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String beneficiaire;

    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal montant;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dateMouvement = LocalDateTime.now();

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "session_caisse_id", nullable = false)
    private SessionCaisse sessionCaisse;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "effectue_par_id", nullable = false)
    private Utilisateur effectuePar;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public TypeMouvementCaisse getType() { return type; }
    public void setType(TypeMouvementCaisse type) { this.type = type; }
    public OrigineMouvementCaisse getOrigine() { return origine; }
    public void setOrigine(OrigineMouvementCaisse origine) { this.origine = origine; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public String getBeneficiaire() { return beneficiaire; }
    public void setBeneficiaire(String beneficiaire) { this.beneficiaire = beneficiaire; }
    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }
    public LocalDateTime getDateMouvement() { return dateMouvement; }
    public void setDateMouvement(LocalDateTime dateMouvement) { this.dateMouvement = dateMouvement; }
    public SessionCaisse getSessionCaisse() { return sessionCaisse; }
    public void setSessionCaisse(SessionCaisse sessionCaisse) { this.sessionCaisse = sessionCaisse; }
    public Utilisateur getEffectuePar() { return effectuePar; }
    public void setEffectuePar(Utilisateur effectuePar) { this.effectuePar = effectuePar; }
}
