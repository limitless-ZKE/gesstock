package com.gesstock.gesstock.controllers;

import com.gesstock.gesstock.entities.Fournisseur;
import com.gesstock.gesstock.repositories.FournisseurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/fournisseurs")
public class FournisseurController {

    @Autowired
    private FournisseurRepository fournisseurRepository;

    // Afficher la liste des fournisseurs
    @GetMapping
    public String listerFournisseurs(Model model) {
        List<Fournisseur> fournisseurs = fournisseurRepository.findAll();
        model.addAttribute("fournisseurs", fournisseurs);
        return "fournisseurs"; // fichier fournisseurs.html
    }

    // Formulaire d'ajout
    @GetMapping("/nouveau")
    public String afficherFormulaire(Model model) {
        model.addAttribute("fournisseur", new Fournisseur());
        return "fournisseur-form"; // fichier fournisseur-form.html
    }

    // Formulaire de modification
    @GetMapping("/modifier/{id}")
    public String afficherFormulaireModification(@PathVariable Long id, Model model) {
        Fournisseur fournisseur = fournisseurRepository.findById(id).orElse(null);
        if (fournisseur == null) {
            return "redirect:/fournisseurs";
        }
        model.addAttribute("fournisseur", fournisseur);
        return "fournisseur-form";
    }

    // Enregistrer ou mettre à jour un fournisseur
    @PostMapping("/sauvegarder")
    public String sauvegarderFournisseur(@ModelAttribute Fournisseur fournisseur) {
        fournisseurRepository.save(fournisseur);
        return "redirect:/fournisseurs";
    }

    // Supprimer un fournisseur
    @GetMapping("/supprimer/{id}")
    public String supprimerFournisseur(@PathVariable Long id) {
        fournisseurRepository.deleteById(id);
        return "redirect:/fournisseurs";
    }
}