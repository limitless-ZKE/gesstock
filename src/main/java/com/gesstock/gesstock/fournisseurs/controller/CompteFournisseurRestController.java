package com.gesstock.gesstock.fournisseurs.controller;

import com.gesstock.gesstock.fournisseurs.dto.AvoirFournisseurRequest;
import com.gesstock.gesstock.fournisseurs.dto.AvoirFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.dto.CompteFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.dto.CompteFournisseurResume;
import com.gesstock.gesstock.fournisseurs.dto.ReglementFournisseurRequest;
import com.gesstock.gesstock.fournisseurs.dto.ReglementFournisseurResponse;
import com.gesstock.gesstock.fournisseurs.service.CompteFournisseurService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Compte fournisseur. Consulter et payer : ADMIN, MANAGER, GESTIONNAIRE. Saisir un avoir à la main : ADMIN et MANAGER
 * (voir SecurityConfig), car il efface une dette sans qu'aucun argent ne bouge.
 */
@RestController
@RequestMapping("/api/fournisseurs")
public class CompteFournisseurRestController {

    private final CompteFournisseurService compteService;

    public CompteFournisseurRestController(CompteFournisseurService compteService) {
        this.compteService = compteService;
    }

    @GetMapping("/comptes")
    public List<CompteFournisseurResume> comptes() {
        return compteService.lister();
    }

    @GetMapping("/{id}/compte")
    public CompteFournisseurResponse compte(@PathVariable Long id) {
        return compteService.obtenir(id);
    }

    @PostMapping("/dettes/{detteId}/reglements")
    public ResponseEntity<ReglementFournisseurResponse> payer(
        @PathVariable Long detteId,
        @Valid @RequestBody ReglementFournisseurRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(compteService.payer(detteId, request, principal.getUsername()));
    }

    @PostMapping("/{id}/avoirs")
    public ResponseEntity<AvoirFournisseurResponse> creerAvoir(
        @PathVariable Long id,
        @Valid @RequestBody AvoirFournisseurRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(compteService.creerAvoir(id, request, principal.getUsername()));
    }
}
