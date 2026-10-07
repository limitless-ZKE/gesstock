package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String nom;

    @Email
    @Size(max = 180)
    @Column(length = 180)
    private String email;

    @Size(max = 30)
    private String telephone;

    @Size(max = 255)
    private String adresse;

    @OneToMany(mappedBy = "client")
    private List<Vente> ventes;

    @OneToMany(mappedBy = "client")
    private List<DetteClient> dettes = new ArrayList<>();

    @OneToMany(mappedBy = "client")
    private List<ReglementClient> reglements = new ArrayList<>();

    // Constructeurs
    public Client() {}

    public Client(String nom, String telephone, String adresse) {
        this.nom = nom;
        this.telephone = telephone;
        this.adresse = adresse;
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public List<Vente> getVentes() { return ventes; }
    public void setVentes(List<Vente> ventes) { this.ventes = ventes; }

    public List<DetteClient> getDettes() { return dettes; }
    public void setDettes(List<DetteClient> dettes) { this.dettes = dettes; }

    public List<ReglementClient> getReglements() { return reglements; }
    public void setReglements(List<ReglementClient> reglements) { this.reglements = reglements; }
}