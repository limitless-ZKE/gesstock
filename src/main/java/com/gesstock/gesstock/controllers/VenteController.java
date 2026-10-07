package com.gesstock.gesstock.controllers;

import com.gesstock.gesstock.entities.Vente;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.ClientRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
import com.gesstock.gesstock.services.PdfService;
import com.gesstock.gesstock.services.VenteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayInputStream;
import java.util.List;

@Controller
@RequestMapping("/ventes")
public class VenteController {

    @Autowired
    private VenteRepository venteRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private VenteService venteService;

    @Autowired
    private PdfService pdfService;

    // Gère l'affichage de l'historique des ventes
    @GetMapping({"", "/historique"})
    public String afficherVentes(Model model) {
        // CORRECTION : Utilisation de la méthode avec JOIN FETCH
        List<Vente> ventes = venteRepository.findAllWithLignesVente();
        model.addAttribute("ventes", ventes);
        return "historique-ventes"; 
    }

    @GetMapping("/nouveau")
    public String nouvelleVenteForm(Model model) {
        model.addAttribute("articles", articleRepository.findAll());
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("vente", new Vente());
        return "formulaire-vente"; 
    }

    @PostMapping("/sauvegarder")
    public String sauvegarderVente(@ModelAttribute Vente vente, 
                                   @RequestParam(value = "clientId", required = false) Long clientId,
                                   @RequestParam("articleIds") List<Long> articleIds,
                                   @RequestParam("quantites") List<Integer> quantites,
                                   RedirectAttributes redirectAttributes) {
        try {
            venteService.enregistrerVentePanier(vente, clientId, articleIds, quantites);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erreur", e.getMessage());
            return "redirect:/ventes/nouveau";
        }

        return "redirect:/ventes/historique";
    }

    @GetMapping("/recu/{id}")
    public ResponseEntity<byte[]> telechargerRecu(@PathVariable("id") Long id) {
        ByteArrayInputStream pdfStream = pdfService.genererRecuPdf(id);
        byte[] bytes = pdfStream.readAllBytes();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=recu_vente_" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}