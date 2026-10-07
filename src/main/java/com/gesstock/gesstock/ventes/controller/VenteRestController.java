package com.gesstock.gesstock.ventes.controller;

import com.gesstock.gesstock.ventes.dto.AnnulationVenteRequest;
import com.gesstock.gesstock.ventes.dto.AnnulationVenteResponse;
import com.gesstock.gesstock.ventes.dto.VenteDirecteRequest;
import com.gesstock.gesstock.ventes.dto.VenteResponse;
import com.gesstock.gesstock.ventes.service.GestionVentesService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/ventes")
public class VenteRestController {

    private final GestionVentesService gestionVentesService;

    public VenteRestController(GestionVentesService gestionVentesService) {
        this.gestionVentesService = gestionVentesService;
    }

    @GetMapping
    public List<VenteResponse> lister(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return gestionVentesService.listerVentes(date);
    }

    @GetMapping("/{id}")
    public VenteResponse obtenir(@PathVariable Long id) {
        return gestionVentesService.obtenirVente(id);
    }

    @PostMapping
    public ResponseEntity<VenteResponse> creerBrouillon(
        @Valid @RequestBody VenteDirecteRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        VenteResponse response = gestionVentesService.creerVenteDirecte(request, principal.getUsername());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/{id}/validation")
    public VenteResponse valider(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return gestionVentesService.validerVente(id, principal.getUsername());
    }

    @PostMapping("/{id}/annulation")
    public AnnulationVenteResponse annuler(
        @PathVariable Long id,
        @Valid @RequestBody AnnulationVenteRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return gestionVentesService.annulerVenteACredit(
            id, request.motif(), request.articlesRapportes(), principal.getUsername());
    }
}
