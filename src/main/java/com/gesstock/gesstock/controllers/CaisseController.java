package com.gesstock.gesstock.controllers;

import com.gesstock.gesstock.entities.Vente;
import com.gesstock.gesstock.repositories.VenteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Controller
public class CaisseController {

    @Autowired
    private VenteRepository venteRepository;

    @GetMapping("/caisse/cloture")
    public String afficherClotureCaisse(Model model) {
        // Intervalle de temps pour aujourd'hui (de 00:00:00 à 23:59:59)
        LocalDateTime debutJour = LocalDate.now().atStartOfDay();
        LocalDateTime finJour = LocalDate.now().atTime(LocalTime.MAX);

        // Récupération des ventes de la journée
        List<Vente> ventesDuJour = venteRepository.findByDateVenteBetween(debutJour, finJour);

        // Calcul du total d'espèces encaissé aujourd'hui
        java.math.BigDecimal totalJournee = ventesDuJour.stream()
                .map(Vente::getPrixTotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        model.addAttribute("ventesDuJour", ventesDuJour);
        model.addAttribute("totalJournee", totalJournee);
        model.addAttribute("nombreVentes", ventesDuJour.size());
        model.addAttribute("dateAujourdhui", LocalDate.now());

        return "cloture-caisse";
    }
}