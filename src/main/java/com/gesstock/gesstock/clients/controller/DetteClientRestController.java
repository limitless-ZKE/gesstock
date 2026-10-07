package com.gesstock.gesstock.clients.controller;

import com.gesstock.gesstock.clients.dto.DetteAvecClientResponse;
import com.gesstock.gesstock.clients.dto.DetteResponse;
import com.gesstock.gesstock.clients.dto.ReglementRequest;
import com.gesstock.gesstock.clients.dto.ReglementResponse;
import com.gesstock.gesstock.clients.service.GestionDetteClientService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/dettes-clients")
public class DetteClientRestController {

    private final GestionDetteClientService detteService;

    public DetteClientRestController(GestionDetteClientService detteService) {
        this.detteService = detteService;
    }

    @GetMapping
    public List<DetteAvecClientResponse> listerToutes() {
        return detteService.listerToutesLesDettes();
    }

    @GetMapping("/client/{clientId}")
    public List<DetteResponse> listerParClient(@PathVariable Long clientId) {
        return detteService.listerDettesClient(clientId);
    }

    @GetMapping("/{detteId}")
    public DetteResponse obtenir(@PathVariable Long detteId) {
        return detteService.obtenirDette(detteId);
    }

    @GetMapping("/{detteId}/reglements")
    public List<ReglementResponse> historique(@PathVariable Long detteId) {
        return detteService.listerReglementsDette(detteId);
    }

    @PostMapping("/{detteId}/reglements")
    public ResponseEntity<ReglementResponse> enregistrerReglement(
        @PathVariable Long detteId,
        @Valid @RequestBody ReglementRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        ReglementResponse response = detteService.enregistrerReglement(
            detteId, request, principal.getUsername());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{reglementId}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }
}
