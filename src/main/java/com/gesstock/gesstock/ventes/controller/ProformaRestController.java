package com.gesstock.gesstock.ventes.controller;

import com.gesstock.gesstock.ventes.dto.ProformaRequest;
import com.gesstock.gesstock.ventes.dto.ProformaResponse;
import com.gesstock.gesstock.ventes.dto.ConversionProformaRequest;
import com.gesstock.gesstock.ventes.dto.ProformaStatutRequest;
import com.gesstock.gesstock.ventes.dto.VenteResponse;
import com.gesstock.gesstock.ventes.service.GestionVentesService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/proformas")
public class ProformaRestController {

    private final GestionVentesService gestionVentesService;

    public ProformaRestController(GestionVentesService gestionVentesService) {
        this.gestionVentesService = gestionVentesService;
    }

    @GetMapping
    public List<ProformaResponse> lister() {
        return gestionVentesService.listerProformas();
    }

    @GetMapping("/{id}")
    public ProformaResponse obtenir(@PathVariable Long id) {
        return gestionVentesService.obtenirProforma(id);
    }

    @PostMapping
    public ResponseEntity<ProformaResponse> creer(
        @Valid @RequestBody ProformaRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        ProformaResponse response = gestionVentesService.creerProforma(request, principal.getUsername());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public ProformaResponse modifier(
        @PathVariable Long id,
        @Valid @RequestBody ProformaRequest request
    ) {
        return gestionVentesService.modifierProforma(id, request);
    }

    @PatchMapping("/{id}/statut")
    public ProformaResponse modifierStatut(
        @PathVariable Long id,
        @Valid @RequestBody ProformaStatutRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return gestionVentesService.modifierStatutProforma(id, request.statut(), principal.getUsername());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        gestionVentesService.supprimerProforma(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/conversion")
    public VenteResponse convertir(
        @PathVariable Long id,
        @RequestBody(required = false) ConversionProformaRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        ConversionProformaRequest conversion = request == null
            ? new ConversionProformaRequest(false, null, null) : request;
        return gestionVentesService.convertirProformaEnVente(
            id, conversion.aCredit(), conversion.modeReglement(), conversion.montantRecu(), principal.getUsername());
    }
}
