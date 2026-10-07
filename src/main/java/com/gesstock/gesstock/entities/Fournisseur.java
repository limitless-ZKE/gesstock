package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Entity
@Table(name = "fournisseurs")
public class Fournisseur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String nom;

    @Size(max = 30)
    private String telephone;

    @Email
    @Size(max = 180)
    @Column(length = 180)
    private String email;

    @Size(max = 255)
    private String adresse;

    // Relation avec les approvisionnements (que nous créerons juste après)
    @OneToMany(mappedBy = "fournisseur")
    private List<Approvisionnement> approvisionnements;

    // Constructeurs
    public Fournisseur() {}

    public Fournisseur(String nom, String telephone, String email, String adresse) {
        this.nom = nom;
        this.telephone = telephone;
        this.email = email;
        this.adresse = adresse;
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public List<Approvisionnement> getApprovisionnements() { return approvisionnements; }
    public void setApprovisionnements(List<Approvisionnement> approvisionnements) { this.approvisionnements = approvisionnements; }
}