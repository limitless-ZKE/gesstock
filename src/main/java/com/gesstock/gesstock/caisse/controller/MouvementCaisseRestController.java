package com.gesstock.gesstock.caisse.controller;

import com.gesstock.gesstock.caisse.dto.MouvementCaisseRequest;
import com.gesstock.gesstock.caisse.dto.MouvementCaisseResponse;
import com.gesstock.gesstock.caisse.service.MouvementCaisseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/caisse/mouvements")
public class MouvementCaisseRestController {

    private final MouvementCaisseService mouvementService;

    public MouvementCaisseRestController(MouvementCaisseService mouvementService) {
        this.mouvementService = mouvementService;
    }

    @GetMapping
    public List<MouvementCaisseResponse> lister(@RequestParam Long sessionId) {
        return mouvementService.lister(sessionId);
    }

    @PostMapping
    public ResponseEntity<MouvementCaisseResponse> enregistrer(
        @Valid @RequestBody MouvementCaisseRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        MouvementCaisseResponse response = mouvementService.enregistrer(request, principal.getUsername());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    /** Un apport de fonds dans la caisse du jour : réservé aux responsables (ADMIN, MANAGER) par SecurityConfig. */
    @PostMapping("/entrees")
    public ResponseEntity<MouvementCaisseResponse> enregistrerEntree(
        @Valid @RequestBody MouvementCaisseRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        MouvementCaisseResponse response = mouvementService.enregistrerEntree(request, principal.getUsername());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }
}
