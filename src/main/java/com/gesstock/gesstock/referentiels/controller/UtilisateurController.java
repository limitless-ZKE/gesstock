package com.gesstock.gesstock.referentiels.controller;

import com.gesstock.gesstock.referentiels.dto.CreateUtilisateurRequest;
import com.gesstock.gesstock.referentiels.dto.UpdateUtilisateurRequest;
import com.gesstock.gesstock.referentiels.dto.UtilisateurResponse;
import com.gesstock.gesstock.referentiels.service.UtilisateurService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/referentiels/utilisateurs")
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

    public UtilisateurController(UtilisateurService utilisateurService) {
        this.utilisateurService = utilisateurService;
    }

    @GetMapping
    public List<UtilisateurResponse> lister() {
        return utilisateurService.lister();
    }

    @GetMapping("/{id}")
    public UtilisateurResponse obtenir(@PathVariable Long id) {
        return utilisateurService.obtenir(id);
    }

    @PostMapping
    public ResponseEntity<UtilisateurResponse> creer(@Valid @RequestBody CreateUtilisateurRequest request) {
        UtilisateurResponse response = utilisateurService.creer(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public UtilisateurResponse modifier(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUtilisateurRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return utilisateurService.modifier(id, request, principal.getUsername());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails principal
    ) {
        utilisateurService.supprimer(id, principal.getUsername());
        return ResponseEntity.noContent().build();
    }
}
