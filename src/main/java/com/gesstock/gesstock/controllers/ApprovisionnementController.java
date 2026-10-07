package com.gesstock.gesstock.controllers;

import com.gesstock.gesstock.entities.Approvisionnement;
import com.gesstock.gesstock.entities.LigneApprovisionnement;
import com.gesstock.gesstock.services.ApprovisionnementService;
import com.gesstock.gesstock.services.ArticleService;
import com.gesstock.gesstock.services.FournisseurService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@Controller
@RequestMapping("/approvisionnements")
public class ApprovisionnementController {

    @Autowired
    private ApprovisionnementService approvisionnementService;

    @Autowired
    private FournisseurService fournisseurService;

    @Autowired
    private ArticleService articleService;

    @GetMapping
    public String listerApprovisionnements(Model model) {
        model.addAttribute("approvisionnements", approvisionnementService.listerTous());
        return "approvisionnements/liste";
    }

    @GetMapping("/nouveau")
    public String formulaireAjout(Model model) {
        Approvisionnement approvisionnement = new Approvisionnement();
        if (approvisionnement.getLignes() == null) {
            approvisionnement.setLignes(new ArrayList<>());
            approvisionnement.getLignes().add(new LigneApprovisionnement());
        }
        
        model.addAttribute("approvisionnement", approvisionnement);
        model.addAttribute("fournisseurs", fournisseurService.listerTous());
        model.addAttribute("articles", articleService.listerTous());
        return "approvisionnements/formulaire";
    }

    @PostMapping("/enregistrer")
    public String enregistrerApprovisionnement(@ModelAttribute Approvisionnement approvisionnement) {
        approvisionnementService.sauvegarderApprovisionnement(approvisionnement);
        return "redirect:/approvisionnements";
    }

    @GetMapping("/supprimer/{id}")
    public String supprimerApprovisionnement(@PathVariable Long id) {
        approvisionnementService.supprimer(id);
        return "redirect:/approvisionnements";
    }
}