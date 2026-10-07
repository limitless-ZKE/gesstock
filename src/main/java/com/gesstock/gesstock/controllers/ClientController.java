package com.gesstock.gesstock.controllers;

import com.gesstock.gesstock.entities.Client;
import com.gesstock.gesstock.repositories.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/clients")
public class ClientController {

    @Autowired
    private ClientRepository clientRepository;

    // Afficher la liste des clients
    @GetMapping
    public String listerClients(Model model) {
        List<Client> clients = clientRepository.findAll();
        model.addAttribute("clients", clients);
        return "clients"; // fichier clients.html
    }

    // Formulaire d'ajout
    @GetMapping("/nouveau")
    public String afficherFormulaire(Model model) {
        model.addAttribute("client", new Client());
        return "client-form"; // fichier client-form.html
    }

    // Formulaire de modification
    @GetMapping("/modifier/{id}")
    public String afficherFormulaireModification(@PathVariable Long id, Model model) {
        Client client = clientRepository.findById(id).orElse(null);
        if (client == null) {
            return "redirect:/clients";
        }
        model.addAttribute("client", client);
        return "client-form"; // On réutilise le même formulaire
    }

    // Enregistrer ou mettre à jour un client
    @PostMapping("/sauvegarder")
    public String sauvegarderClient(@ModelAttribute Client client) {
        clientRepository.save(client);
        return "redirect:/clients";
    }

    // Supprimer un client
    @GetMapping("/supprimer/{id}")
    public String supprimerClient(@PathVariable Long id) {
        clientRepository.deleteById(id);
        return "redirect:/clients";
    }
}