package com.gesstock.gesstock.controllers;

import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.Vente;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private VenteRepository venteRepository;

    @GetMapping({"/", "/dashboard"})
    public String afficherDashboard(Model model) {
        // 1. Récupérer le nombre total d'articles
        long totalArticles = articleRepository.count();
        model.addAttribute("totalArticles", totalArticles);

        // 2. Récupérer TOUS les articles pour affichage complet dans le tableau
        List<Article> articles = articleRepository.findAll();
        model.addAttribute("articles", articles);

        // 3. Calculer le Chiffre d'Affaires Global (somme des montants totaux de toutes les ventes)
        List<Vente> ventes = venteRepository.findAll();
        java.math.BigDecimal chiffreAffaires = ventes.stream()
                .map(Vente::getMontantTotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        model.addAttribute("chiffreAffaires", chiffreAffaires);

        return "dashboard"; // Renvoie vers le fichier dashboard.html
    }
}