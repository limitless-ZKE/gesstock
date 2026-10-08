package com.gesstock.gesstock.ventes.controller;

import com.gesstock.gesstock.ventes.dto.AnnulationVenteRequest;
import com.gesstock.gesstock.ventes.dto.AnnulationVenteResponse;
import com.gesstock.gesstock.ventes.dto.PageVentesResponse;
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

    public static final String EN_TETE_TOTAL = "X-Total-Count";

    private final GestionVentesService gestionVentesService;

    public VenteRestController(GestionVentesService gestionVentesService) {
        this.gestionVentesService = gestionVentesService;
    }

    /**
     * Avec {@code date} : les ventes de cette journée. Sinon : l'historique paginé, les plus récentes d'abord
     * ({@code page} à partir de 0, {@code size} 50 par défaut et 200 au plus), limité à {@code clientId} s'il est donné.
     * Le nombre total de ventes est dans l'en-tête {@code X-Total-Count}.
     */
    @GetMapping
    public ResponseEntity<List<VenteResponse>> lister(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) Long clientId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        if (date != null) {
            List<VenteResponse> duJour = gestionVentesService.listerVentes(date);
            return ResponseEntity.ok().header(EN_TETE_TOTAL, String.valueOf(duJour.size())).body(duJour);
        }
        PageVentesResponse resultat = gestionVentesService.listerVentesPaginees(clientId, page, size);
        return ResponseEntity.ok().header(EN_TETE_TOTAL, String.valueOf(resultat.total())).body(resultat.ventes());
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
