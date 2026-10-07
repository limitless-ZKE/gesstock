package com.gesstock.gesstock.controllers; // Adaptez le package selon votre projet

import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.repositories.ArticleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/articles") // Toutes les routes de ce contrôleur commenceront par /articles
public class ArticleController {

    @Autowired
    private ArticleRepository articleRepository;

    // Affiche la liste de tous les articles (URL : /articles)
    @GetMapping
    public String index(Model model, @RequestParam(name = "keyword", required = false) String keyword) {
        List<Article> articles;
        if (keyword != null && !keyword.isEmpty()) {
            // Si une recherche est effectuée (optionnel selon votre implémentation)
            articles = articleRepository.findByNomContainingIgnoreCase(keyword);
        } else {
            articles = articleRepository.findAll();
        }
        model.addAttribute("articles", articles);
        return "articles"; // correspond au fichier articles.html
    }

    // Affiche le formulaire pour ajouter un nouvel article (URL : /articles/nouveau)
    @GetMapping("/nouveau")
    public String afficherFormulaireAjout(Model model) {
        model.addAttribute("article", new Article());
        return "article-form"; // correspond au fichier article-form.html
    }

    // NOUVEAU : Affiche le formulaire pré-rempli pour modifier un article existant (URL : /articles/editer/{id})
    @GetMapping("/editer/{id}")
    public String afficherFormulaireModification(@PathVariable Long id, Model model) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Article invalide ID: " + id));
        model.addAttribute("article", article);
        return "article-form"; // Réutilise le même formulaire pour la modification
    }

    // Enregistre ou met à jour un article (URL : /articles/sauvegarder)
    @PostMapping("/sauvegarder")
    public String sauvegarderArticle(@ModelAttribute Article article) {
        articleRepository.save(article);
        return "redirect:/articles";
    }

    // Supprimer un article (URL : /articles/supprimer/{id})
    @GetMapping("/supprimer/{id}")
    public String supprimerArticle(@PathVariable Long id) {
        articleRepository.deleteById(id);
        return "redirect:/articles";
    }
}