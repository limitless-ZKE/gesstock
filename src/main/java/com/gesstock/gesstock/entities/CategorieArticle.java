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
@Table(name = "categories_articles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategorieArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String nom;

    @Size(max = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private CategorieArticle parent;

    @OneToMany(mappedBy = "parent")
    private List<CategorieArticle> sousCategories = new ArrayList<>();

    @OneToMany(mappedBy = "categorie")
    private List<Article> articles = new ArrayList<>();

    @ManyToMany(mappedBy = "categories")
    private List<Promotion> promotions = new ArrayList<>();
}
