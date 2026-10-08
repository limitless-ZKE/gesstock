package com.gesstock.gesstock.fournisseurs.controller;

import com.gesstock.gesstock.fournisseurs.dto.LigneRetournableResponse;
import com.gesstock.gesstock.fournisseurs.dto.RetourFournisseurRequest;
import com.gesstock.gesstock.fournisseurs.dto.RetourFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.service.RetourFournisseurService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Retours de marchandise au fournisseur. Préparer et annuler un brouillon : ADMIN, MANAGER, GESTIONNAIRE. Valider (sortie de
 * stock et avoir) : ADMIN et MANAGER seulement, voir SecurityConfig.
 */
@RestController
@RequestMapping("/api/retours-fournisseurs")
public class RetourFournisseurRestController {

    private final RetourFournisseurService retourService;

    public RetourFournisseurRestController(RetourFournisseurService retourService) {
        this.retourService = retourService;
    }

    @GetMapping
    public List<RetourFournisseurResponse> lister() {
        return retourService.lister();
    }

    @GetMapping("/{id}")
    public RetourFournisseurResponse obtenir(@PathVariable Long id) {
        return retourService.obtenir(id);
    }

    /** Lignes d'un bon reçu avec la quantité encore retournable : de quoi composer un retour. */
    @GetMapping("/bons/{approvisionnementId}/lignes")
    public List<LigneRetournableResponse> lignesRetournables(@PathVariable Long approvisionnementId) {
        return retourService.lignesRetournables(approvisionnementId);
    }

    @PostMapping
    public ResponseEntity<RetourFournisseurResponse> creer(
        @Valid @RequestBody RetourFournisseurRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        RetourFournisseurResponse reponse = retourService.creer(request, principal.getUsername());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(reponse.id()).toUri();
        return ResponseEntity.created(location).body(reponse);
    }

    @PostMapping("/{id}/validation")
    public RetourFournisseurResponse valider(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return retourService.valider(id, principal.getUsername());
    }

    @PostMapping("/{id}/annulation")
    public RetourFournisseurResponse annuler(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return retourService.annuler(id, principal.getUsername());
    }
}
